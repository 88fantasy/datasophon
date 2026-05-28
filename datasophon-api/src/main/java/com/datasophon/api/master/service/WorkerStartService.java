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

import cn.hutool.core.util.ObjectUtil;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.extrepo.VosProductInstallService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.GenerateHostPrometheusConfig;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.InstallState;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.model.StartWorkerMessage;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.domain.host.enums.MANAGED;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Worker 节点启动/停止事件处理 Spring Service，业务逻辑来自 {@link WorkerStartActor}。
 *
 * <p>在 gRPC 路径下，由 {@code WorkerRegistryGrpcService.register()} 调用；
 * 在 Pekko 路径下（Phase 5 前），仍由 WorkerStartActor 处理。</p>
 */
@Service
public class WorkerStartService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerStartService.class);

    private final ClusterHostService clusterHostService;
    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final VosProductInstallService vosProductActionService;
    private final PrometheusService prometheusService;

    public WorkerStartService(ClusterHostService clusterHostService,
                               ClusterInfoService clusterInfoService,
                               ClusterServiceRoleInstanceService roleInstanceService,
                               VosProductInstallService vosProductActionService,
                               PrometheusService prometheusService) {
        this.clusterHostService = clusterHostService;
        this.clusterInfoService = clusterInfoService;
        this.roleInstanceService = roleInstanceService;
        this.vosProductActionService = vosProductActionService;
        this.prometheusService = prometheusService;
    }

    /**
     * 异步处理 Worker 首次启动事件（替代 WorkerStartActor.tell(StartWorkerMessage)）。
     * 在 gRPC 路径下由 WorkerRegistryGrpcService.register() 触发。
     */
    @Async("masterExecutor")
    public void handleWorkerRegistration(StartWorkerMessage msg) {
        String hostname = msg.getHostname();
        Integer clusterId = msg.getClusterId();
        logger.info("Handling worker registration: {}", hostname);

        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster == null) {
            logger.warn("Cluster {} not found for worker {}", clusterId, hostname);
            return;
        }

        logger.info("Host install set to 100%");
        if (CacheUtils.containsKey(cluster.getClusterCode() + Constants.HOST_MAP)) {
            Map<String, HostInfo> map =
                    (Map<String, HostInfo>) CacheUtils.get(cluster.getClusterCode() + Constants.HOST_MAP);
            HostInfo hostInfo = map.get(hostname);
            if (Objects.nonNull(hostInfo)) {
                hostInfo.setProgress(Constants.ONE_HUNDRRD);
                hostInfo.setInstallState(InstallState.SUCCESS);
                hostInfo.setInstallStateCode(InstallState.SUCCESS.getValue());
                hostInfo.setManaged(true);
            }
        }

        ClusterHostDO hostEntity = clusterHostService.getClusterHostByHostname(hostname);
        if (ObjectUtil.isNull(hostEntity)) {
            ProcessUtils.saveHostInstallInfo(msg, cluster.getClusterCode(), clusterHostService);
            logger.info("Host install save to database");
        } else {
            hostEntity.setCpuArchitecture(msg.getCpuArchitecture());
            hostEntity.setManaged(MANAGED.YES);
            clusterHostService.updateById(hostEntity);
        }

        // Trigger Prometheus config regeneration
        GenerateHostPrometheusConfig prometheusCmd = new GenerateHostPrometheusConfig();
        prometheusCmd.setClusterId(cluster.getId());
        prometheusService.generateHostPrometheusConfig(prometheusCmd);

        // Auto-start services on the new worker
        autoAddServiceOperatorNeeded(hostname, cluster.getId(), CommandType.START_SERVICE, false);
    }

    /**
     * 异步触发主机上服务的自动启动/停止（替代 WorkerStartActor.tell(WorkerServiceMessage)）。
     */
    @Async("masterExecutor")
    public void autoManageHostServices(String hostname, Integer clusterId,
                                        CommandType commandType, boolean needRestart) {
        autoAddServiceOperatorNeeded(hostname, clusterId, commandType, needRestart);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private void autoAddServiceOperatorNeeded(String hostname, Integer clusterId,
                                               CommandType commandType, boolean needRestart) {
        List<ClusterServiceRoleInstanceEntity> serviceRoleList = null;

        if (CommandType.START_SERVICE.equals(commandType)) {
            serviceRoleList = roleInstanceService
                    .listStoppedServiceRoleListByHostnameAndClusterId(hostname, clusterId);
            if (needRestart) {
                roleInstanceService.updateToNeedRestartByHost(hostname);
            }
        } else if (CommandType.STOP_SERVICE.equals(commandType)) {
            serviceRoleList = roleInstanceService
                    .getServiceRoleListByHostnameAndClusterId(hostname, clusterId).stream()
                    .filter(r -> (!ServiceRoleState.STOP.equals(r.getServiceRoleState())
                            && !ServiceRoleState.DECOMMISSIONED.equals(r.getServiceRoleState())))
                    .collect(toList());
        }

        if (CollectionUtils.isEmpty(serviceRoleList)) {
            logger.info("No services need to start at host {}.", hostname);
            return;
        }

        Map<Integer, List<Integer>> serviceRoleMap = serviceRoleList.stream()
                .collect(groupingBy(ClusterServiceRoleInstanceEntity::getServiceId,
                        mapping(ClusterServiceRoleInstanceEntity::getId, toList())));
        try {
            vosProductActionService.generateAndExecSrvRoleCommands(clusterId, commandType, serviceRoleMap);
            logger.info("Auto-start services successful for host {}", hostname);
        } catch (Exception e) {
            logger.warn("Some service auto-start failed for host {}, check service logs.", hostname, e);
        }
    }
}
