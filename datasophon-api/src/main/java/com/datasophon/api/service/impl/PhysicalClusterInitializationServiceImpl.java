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

package com.datasophon.api.service.impl;

import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse;
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse.InitializationPhase;
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse.NodeStatus;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.grpc.WorkerEndpoint;
import com.datasophon.api.grpc.WorkerRegistry;
import com.datasophon.api.observability.OtelSelfMetrics;
import com.datasophon.api.observability.OtelSelfMetricsClient;
import com.datasophon.api.observability.RustfsEndpointProvider;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.PhysicalClusterInitializationService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.domain.host.enums.MANAGED;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PhysicalClusterInitializationServiceImpl implements PhysicalClusterInitializationService {

    static final String OTEL_SERVICE = "OTELCOLLECTOR";
    static final String OTEL_ROLE = "OtelCollector";
    private static final Duration VERIFY_TIMEOUT = Duration.ofMinutes(2);

    private final ClusterInfoService clusterInfoService;
    private final ClusterHostService clusterHostService;
    private final WorkerRegistry workerRegistry;
    private final WorkerCommandClient workerCommandClient;
    private final ServiceInstallService serviceInstallService;
    private final ExtRepoInstallDelegateService installDelegateService;
    private final ClusterServiceCommandService commandService;
    private final ClusterServiceInstanceService serviceInstanceService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final OtelSelfMetricsClient metricsClient;
    private final RustfsEndpointProvider rustfsEndpointProvider;

    public PhysicalClusterInitializationServiceImpl(
                                                    ClusterInfoService clusterInfoService,
                                                    ClusterHostService clusterHostService,
                                                    WorkerRegistry workerRegistry,
                                                    WorkerCommandClient workerCommandClient,
                                                    ServiceInstallService serviceInstallService,
                                                    ExtRepoInstallDelegateService installDelegateService,
                                                    ClusterServiceCommandService commandService,
                                                    ClusterServiceInstanceService serviceInstanceService,
                                                    ClusterServiceRoleInstanceService roleInstanceService,
                                                    OtelSelfMetricsClient metricsClient,
                                                    RustfsEndpointProvider rustfsEndpointProvider) {
        this.clusterInfoService = clusterInfoService;
        this.clusterHostService = clusterHostService;
        this.workerRegistry = workerRegistry;
        this.workerCommandClient = workerCommandClient;
        this.serviceInstallService = serviceInstallService;
        this.installDelegateService = installDelegateService;
        this.commandService = commandService;
        this.serviceInstanceService = serviceInstanceService;
        this.roleInstanceService = roleInstanceService;
        this.metricsClient = metricsClient;
        this.rustfsEndpointProvider = rustfsEndpointProvider;
    }

    @Override
    public synchronized PhysicalClusterInitializationResponse start(Integer clusterId) {
        ClusterInfoEntity cluster = requirePhysicalCluster(clusterId);
        if (ClusterState.RUNNING.equals(cluster.getClusterState())) {
            return getStatus(clusterId);
        }
        if (!ClusterState.NEED_CONFIG.equals(cluster.getClusterState())) {
            throw new ServiceException(400, "当前集群状态不允许执行初始化");
        }

        List<ClusterHostDO> hosts = managedHosts(clusterId);
        if (hosts.isEmpty()) {
            throw new ServiceException(400, "集群没有已纳管节点");
        }
        requireWorkersHealthy(hosts);

        ClusterServiceCommandEntity latest = latestOtelCommand(clusterId);
        if (latest != null && (CommandState.WAIT.equals(latest.getCommandState())
                || CommandState.RUNNING.equals(latest.getCommandState()))) {
            return getStatus(clusterId);
        }

        ServiceRoleHostMapping mapping = new ServiceRoleHostMapping();
        mapping.setServiceRole(OTEL_ROLE);
        mapping.setHosts(hosts.stream().map(ClusterHostDO::getHostname).toList());
        serviceInstallService.saveServiceRoleHostMapping(clusterId, List.of(mapping));

        List<ServiceConfig> configs = serviceInstallService.getServiceConfigFromDdl(clusterId, OTEL_SERVICE);
        applyS3Endpoint(configs);
        serviceInstallService.saveServiceConfig(clusterId, OTEL_SERVICE, configs, -1);

        String dagId = installDelegateService.generateGenericInstallCommand(clusterId, List.of(OTEL_SERVICE));
        RunDagDto runDag = new RunDagDto();
        runDag.setDagId(dagId);
        installDelegateService.redeploy(runDag);

        PhysicalClusterInitializationResponse response = getStatus(clusterId);
        response.setDagId(dagId);
        return response;
    }

    @Override
    public PhysicalClusterInitializationResponse getStatus(Integer clusterId) {
        ClusterInfoEntity cluster = requirePhysicalCluster(clusterId);
        List<ClusterHostDO> hosts = managedHosts(clusterId);
        ClusterServiceCommandEntity latest = latestOtelCommand(clusterId);
        Map<String, ClusterServiceRoleInstanceEntity> roles = roleMap(clusterId);

        PhysicalClusterInitializationResponse response = new PhysicalClusterInitializationResponse();
        if (ClusterState.RUNNING.equals(cluster.getClusterState())) {
            response.setPhase(InitializationPhase.COMPLETED);
            response.setCompleted(true);
        } else if (latest == null) {
            response.setPhase(InitializationPhase.READY);
        } else if (CommandState.WAIT.equals(latest.getCommandState())
                || CommandState.RUNNING.equals(latest.getCommandState())) {
            response.setPhase(InitializationPhase.COLLECTOR_INSTALLING);
        } else if (CommandState.FAILED.equals(latest.getCommandState())
                || CommandState.CANCEL.equals(latest.getCommandState())) {
            response.setPhase(InitializationPhase.FAILED);
            response.setCanRetry(true);
        } else {
            response.setPhase(InitializationPhase.VERIFYING);
        }

        boolean verifyCollector = latest != null && CommandState.SUCCESS.equals(latest.getCommandState());
        for (ClusterHostDO host : hosts) {
            response.getNodes().add(nodeStatus(host, roles.get(host.getHostname()), latest, verifyCollector));
        }

        boolean allHealthy = !response.getNodes().isEmpty()
                && response.getNodes().stream().allMatch(node -> node.isWorkerHealthy()
                        && node.isCollectorInstalled() && node.isCollectorHealthy());
        if (verifyCollector && allHealthy && ClusterState.NEED_CONFIG.equals(cluster.getClusterState())) {
            clusterInfoService.updateClusterState(clusterId, ClusterState.RUNNING.getValue());
            response.setPhase(InitializationPhase.COMPLETED);
            response.setCompleted(true);
            response.setCanRetry(false);
        } else if (verifyCollector && !allHealthy && verificationTimedOut(latest)) {
            response.setPhase(InitializationPhase.FAILED);
            response.setCanRetry(true);
        }
        return response;
    }

    private ClusterInfoEntity requirePhysicalCluster(Integer clusterId) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster == null) {
            throw new ServiceException(404, "集群不存在");
        }
        if (!ClusterArchType.physical.equals(cluster.getArchType())) {
            throw new ServiceException(400, "仅物理集群支持此初始化流程");
        }
        return cluster;
    }

    private List<ClusterHostDO> managedHosts(Integer clusterId) {
        List<ClusterHostDO> hosts = clusterHostService.getHostListByClusterId(clusterId).stream()
                .filter(host -> MANAGED.YES.equals(host.getManaged()))
                .sorted(Comparator.comparing(ClusterHostDO::getHostname))
                .toList();
        for (ClusterHostDO host : hosts) {
            if (!isIpAddress(host.getIp())) {
                throw new ServiceException(400, "节点 " + host.getHostname() + " 缺少有效 IP");
            }
        }
        return hosts;
    }

    private void requireWorkersHealthy(List<ClusterHostDO> hosts) {
        for (ClusterHostDO host : hosts) {
            WorkerEndpoint endpoint = workerRegistry.getEndpoint(host.getHostname())
                    .orElseThrow(() -> new ServiceException(409, "Worker 未在线: " + host.getHostname()));
            if (host.getClusterId() == null || endpoint.getClusterId() != host.getClusterId()
                    || !host.getIp().equals(endpoint.getIp())) {
                throw new ServiceException(409, "Worker 未使用节点 IP 注册: " + host.getHostname());
            }
            ExecResult ping = workerCommandClient.ping(host.getHostname());
            if (ping == null || !Boolean.TRUE.equals(ping.getExecResult())) {
                throw new ServiceException(409, "Worker 回拨失败: " + host.getHostname());
            }
        }
    }

    private NodeStatus nodeStatus(ClusterHostDO host, ClusterServiceRoleInstanceEntity role,
                                  ClusterServiceCommandEntity command, boolean verifyCollector) {
        boolean workerHealthy = registeredByIp(host);
        if (verifyCollector && workerHealthy) {
            workerHealthy = pingWorker(host.getHostname());
        }
        boolean collectorInstalled = role != null && ServiceRoleState.RUNNING.equals(role.getServiceRoleState());
        boolean collectorHealthy = false;
        String message;

        if (!workerHealthy) {
            message = "Worker 未在线或 IP 不匹配";
        } else if (command == null) {
            message = "等待安装 Collector";
        } else if (CommandState.FAILED.equals(command.getCommandState())
                || CommandState.CANCEL.equals(command.getCommandState())) {
            message = "Collector 安装失败";
        } else if (!verifyCollector) {
            message = "Collector 安装中";
        } else if (!collectorInstalled) {
            message = "Collector 角色未运行";
        } else {
            try {
                OtelSelfMetrics metrics = metricsClient.fetch(host.getIp());
                collectorHealthy = metrics.processUptime() > 0
                        && metrics.sentTotal() > 0
                        && metrics.queueSize() == 0
                        && metrics.receiverFailedTotal() == 0;
                if (collectorHealthy) {
                    message = "正常";
                } else if (metrics.sentTotal() == 0) {
                    message = "Collector 尚未产生成功导出";
                } else if (metrics.queueSize() > 0) {
                    message = "Collector 导出队列尚未清空";
                } else {
                    message = "Collector 接收数据失败";
                }
            } catch (RuntimeException e) {
                message = "Collector 自监控不可达";
            }
        }
        return new NodeStatus(host.getHostname(), host.getIp(), workerHealthy,
                collectorInstalled, collectorHealthy, message);
    }

    private boolean registeredByIp(ClusterHostDO host) {
        return workerRegistry.getEndpoint(host.getHostname())
                .map(endpoint -> host.getClusterId() != null && endpoint.getClusterId() == host.getClusterId()
                        && host.getIp().equals(endpoint.getIp()))
                .orElse(false);
    }

    private boolean pingWorker(String hostname) {
        try {
            ExecResult result = workerCommandClient.ping(hostname);
            return result != null && Boolean.TRUE.equals(result.getExecResult());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ClusterServiceCommandEntity latestOtelCommand(Integer clusterId) {
        return commandService.getLatestCommand(clusterId, OTEL_SERVICE);
    }

    private Map<String, ClusterServiceRoleInstanceEntity> roleMap(Integer clusterId) {
        ClusterServiceInstanceEntity service =
                serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterId, OTEL_SERVICE);
        if (service == null) {
            return Map.of();
        }
        Map<String, ClusterServiceRoleInstanceEntity> roles = new HashMap<>();
        for (ClusterServiceRoleInstanceEntity role : roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, OTEL_ROLE)) {
            roles.put(role.getHostname(), role);
        }
        return roles;
    }

    private static boolean verificationTimedOut(ClusterServiceCommandEntity command) {
        return command.getEndTime() != null
                && command.getEndTime().toInstant().isBefore(Instant.now().minus(VERIFY_TIMEOUT));
    }

    private void applyS3Endpoint(List<ServiceConfig> configs) {
        ServiceConfig endpoint = configs.stream()
                .filter(config -> "s3Endpoint".equals(config.getName()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(500, "OTELCOLLECTOR 缺少 s3Endpoint 配置"));
        String configuredEndpoint = rustfsEndpointProvider.getEndpoint();
        String ip = StringUtils.substringBetween(configuredEndpoint, "://", ":");
        String port = StringUtils.substringAfterLast(configuredEndpoint, ":");
        if (!isIpAddress(ip)) {
            throw new ServiceException(400, "OTELCOLLECTOR 的 S3 端点必须使用 IP");
        }
        try {
            int portNumber = Integer.parseInt(port);
            if (portNumber < 1 || portNumber > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new ServiceException(400, "RustFS 端口配置无效");
        }
        endpoint.setValue(configuredEndpoint);
    }

    static boolean isIpAddress(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255 || !String.valueOf(number).equals(part)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
