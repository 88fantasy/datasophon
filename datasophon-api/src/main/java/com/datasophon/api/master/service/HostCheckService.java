/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.master.service;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusVectorResult;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PromInfoUtils;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.domain.host.enums.HostState;
import com.datasophon.domain.host.enums.MANAGED;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 节点状态巡检 Spring Service（替代 HostCheckActor）。
 *
 * <p>原 HostCheckActor.doOnReceive() 的全部逻辑迁移至此；
 * Pekka 分支彻底移除，改为通过 {@link WorkerCommandClient#ping} gRPC 调用。</p>
 */
@Slf4j
@Service
public class HostCheckService {
    
    private static final Map<String, String> FILESYSTEM_FILTERS = Map.of("type", "ext.*|xfs");
    private static final Map<String, String> FILESYSTEM_FILTERS_NE = Map.of("mountpoint", ".*pod.*");
    private static final Map<String, String> DISK_USED_FILTERS = Map.of("type", "ext.*|xfs", "state", "used");
    
    private final ClusterInfoService clusterInfoService;
    private final ClusterHostService clusterHostService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final WorkerCommandClient workerCommandClient;
    private final OtelMetricsQueryService metricsQueryService;
    private final Executor masterExecutor;
    
    public HostCheckService(ClusterInfoService clusterInfoService,
                            ClusterHostService clusterHostService,
                            ClusterServiceRoleInstanceService roleInstanceService,
                            WorkerCommandClient workerCommandClient,
                            OtelMetricsQueryService metricsQueryService,
                            @Qualifier("masterExecutor") Executor masterExecutor) {
        this.clusterInfoService = clusterInfoService;
        this.clusterHostService = clusterHostService;
        this.roleInstanceService = roleInstanceService;
        this.workerCommandClient = workerCommandClient;
        this.metricsQueryService = metricsQueryService;
        this.masterExecutor = masterExecutor;
    }
    
    /**
     * 检测所有物理集群主机的在线状态。
     *
     * @param hostInfo 若非 null，则只检测指定主机；为 null 时检测全部主机
     */
    public void checkHosts(HostInfo hostInfo) {
        log.info("start to check host info");
        List<ClusterInfoEntity> clusterList = clusterInfoService.getReadyClusterList();
        for (ClusterInfoEntity cluster : clusterList) {
            if (ClusterArchType.physical.equals(cluster.getArchType())) {
                try {
                    checkCluster(cluster, hostInfo);
                } catch (Exception ex) {
                    log.error("检查集群{}状态失败，{}", cluster.getClusterName(), ex.getMessage(), ex);
                }
            }
        }
    }
    
    private void checkCluster(ClusterInfoEntity cluster, HostInfo hostInfo) {
        ClusterServiceRoleInstanceEntity prometheusInstance =
                roleInstanceService.getOneServiceRole("Prometheus", "", cluster.getId());
        boolean promReady = prometheusInstance != null
                && ServiceRoleState.RUNNING.equals(prometheusInstance.getServiceRoleState());
        String promUrl = promReady
                ? "http://" + prometheusInstance.getHostname() + ":9090/api/v1/query"
                : null;
        
        List<ClusterHostDO> list = clusterHostService.getHostListByClusterId(cluster.getId());
        List<ClusterHostDO> updates = new ArrayList<>();
        for (ClusterHostDO host : list) {
            if (hostInfo != null && !StringUtils.equals(host.getHostname(), hostInfo.getHostname())) {
                continue;
            }
            updates.add(host);
        }
        
        // 阻塞的 ping/Prometheus 调用按主机 fan-out 到 masterExecutor，
        // 避免在 5 线程的调度池里串行累积（单轮耗时 ≈ 最慢主机而非 Σ 所有主机）。
        List<CompletableFuture<Void>> futures = new ArrayList<>(updates.size());
        for (ClusterHostDO host : updates) {
            Runnable task = () -> {
                checkHostByPingPong(host);
                if (!HostState.OFFLINE.equals(host.getHostState()) && promUrl != null) {
                    checkHostByPrometheus(host, promUrl);
                } else if (!HostState.OFFLINE.equals(host.getHostState())) {
                    checkHostByOtel(cluster.getId(), host);
                }
            };
            try {
                futures.add(CompletableFuture.runAsync(task, masterExecutor));
            } catch (RejectedExecutionException e) {
                // 池满时退化为调用线程串行执行，等价旧行为
                task.run();
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        if (!updates.isEmpty()) {
            clusterHostService.updateBatchById(updates);
        }
    }
    
    private void checkHostByPingPong(ClusterHostDO host) {
        host.setCheckTime(new Date());
        try {
            ExecResult execResult = workerCommandClient.ping(host.getHostname());
            host.setManaged(MANAGED.YES);
            if (execResult.getExecResult()) {
                host.setHostState(HostState.RUNNING);
                log.info("ping host: {} success", host.getHostname());
            } else {
                host.setHostState(HostState.OFFLINE);
                host.setManaged(MANAGED.YES);
                log.warn("ping host: {} fail, reason: {}", host.getHostname(), execResult.getExecOut());
            }
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                log.warn("ping: {} timeout, it maybe offline", host.getHostname());
            } else {
                log.error("ping host: {} error, cause: {}", host.getHostname(), e.getMessage());
            }
            host.setHostState(HostState.OFFLINE);
        }
    }
    
    private void checkHostByPrometheus(ClusterHostDO host, String promUrl) {
        try {
            String hostname = host.getHostname();
            String totalMemPromQl = "node_memory_MemTotal_bytes{job=~\"node\",instance=\"" + hostname + ":9100\"}/1024/1024/1024";
            String totalMemStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, totalMemPromQl);
            if (StringUtils.isNotBlank(totalMemStr)) {
                host.setTotalMem(Double.valueOf(totalMemStr).intValue());
            }
            String memAvailablePromQl = "node_memory_MemAvailable_bytes{job=~\"node\",instance=\"" + hostname + ":9100\"}/1024/1024/1024";
            String memAvailableStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, memAvailablePromQl);
            if (StringUtils.isNotBlank(memAvailableStr)) {
                int memAvailable = Double.valueOf(memAvailableStr).intValue();
                host.setUsedMem(host.getTotalMem() - memAvailable);
            }
            String totalDiskPromQl = "sum(node_filesystem_size_bytes{instance=\"" + hostname
                    + ":9100\",fstype=~\"ext4|xfs\",mountpoint !~\".*pod.*\"})/1024/1024/1024";
            String totalDiskStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, totalDiskPromQl);
            if (StringUtils.isNotBlank(totalDiskStr)) {
                host.setTotalDisk(Double.valueOf(totalDiskStr).intValue());
            }
            String diskUsedPromQl = "sum(node_filesystem_size_bytes{instance=\"" + hostname
                    + ":9100\",fstype=~\"ext.*|xfs\",mountpoint !~\".*pod.*\"}-node_filesystem_free_bytes{instance=\""
                    + hostname + ":9100\",fstype=~\"ext.*|xfs\",mountpoint !~\".*pod.*\"})/1024/1024/1024";
            String diskUsed = PromInfoUtils.getSinglePrometheusMetric(promUrl, diskUsedPromQl);
            if (StringUtils.isNotBlank(diskUsed)) {
                host.setUsedDisk(Double.valueOf(diskUsed).intValue());
            }
            String cpuLoadPromQl = "node_load5{job=~\"node\",instance=\"" + hostname + ":9100\"}";
            String cpuLoad = PromInfoUtils.getSinglePrometheusMetric(promUrl, cpuLoadPromQl);
            if (StringUtils.isNotBlank(cpuLoad)) {
                host.setAverageLoad(cpuLoad);
            }
        } catch (Exception e) {
            log.warn("check cluster state error, cause: {}", e.getMessage());
            host.setHostState(HostState.EXISTS_ALARM);
        }
    }
    
    private void checkHostByOtel(Integer clusterId, ClusterHostDO host) {
        try {
            String instance = host.getHostname();
            // hostmetrics 的 memory/filesystem "usage" 指标是 non-monotonic sum（按 state 维度分类），
            // 落 otel_metrics_sum 表；只有 cpu.load_average 是 gauge，见 OtelMetricsQueryService#queryInstant(...,table)。
            Double totalMem = queryOtelMetric(clusterId, "system.memory.usage", "sum", instance, "sum");
            if (totalMem != null) {
                host.setTotalMem(bytesToGiB(totalMem));
            }
            Double memAvailable = queryOtelMetric(clusterId, "system.linux.memory.available", null, instance, "sum");
            if (memAvailable != null && host.getTotalMem() != null) {
                host.setUsedMem(host.getTotalMem() - bytesToGiB(memAvailable));
            }
            Double totalDisk = queryOtelMetric(clusterId, "system.filesystem.usage", "sum", instance,
                    FILESYSTEM_FILTERS, FILESYSTEM_FILTERS_NE, "sum");
            if (totalDisk != null) {
                host.setTotalDisk(bytesToGiB(totalDisk));
            }
            Double usedDisk = queryOtelMetric(clusterId, "system.filesystem.usage", "sum", instance,
                    DISK_USED_FILTERS, FILESYSTEM_FILTERS_NE, "sum");
            if (usedDisk != null) {
                host.setUsedDisk(bytesToGiB(usedDisk));
            }
            Double cpuLoad = queryOtelMetric(clusterId, "system.cpu.load_average.5m", null, instance, "gauge");
            if (cpuLoad != null) {
                host.setAverageLoad(String.valueOf(cpuLoad));
            }
        } catch (Exception e) {
            log.warn("check host {} metrics from otel error, cause: {}", host.getHostname(), e.getMessage());
        }
    }
    
    private Double queryOtelMetric(Integer clusterId, String metric, String agg, String instance, String table) {
        return queryOtelMetric(clusterId, metric, agg, instance, Map.of(), Map.of(), table);
    }
    
    private Double queryOtelMetric(Integer clusterId, String metric, String agg, String instance,
                                   Map<String, String> filters, Map<String, String> filtersNe, String table) {
        PrometheusVectorResult result = metricsQueryService.queryInstant(clusterId, metric, agg, 1.0d,
                instance, "node", filters, filtersNe, System.currentTimeMillis() / 1000, table);
        if (result == null || result.result().isEmpty()) {
            return null;
        }
        Object value = result.result().get(0).value()[1];
        return value == null ? null : Double.valueOf(String.valueOf(value));
    }
    
    private static int bytesToGiB(Double bytes) {
        return Double.valueOf(bytes / 1024 / 1024 / 1024).intValue();
    }
}
