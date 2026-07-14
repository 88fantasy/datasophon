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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse;
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse.InitializationPhase;
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

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PhysicalClusterInitializationServiceImplTest {

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ClusterHostService clusterHostService = mock(ClusterHostService.class);
    private final WorkerRegistry workerRegistry = mock(WorkerRegistry.class);
    private final WorkerCommandClient workerCommandClient = mock(WorkerCommandClient.class);
    private final ServiceInstallService serviceInstallService = mock(ServiceInstallService.class);
    private final ExtRepoInstallDelegateService installDelegateService = mock(ExtRepoInstallDelegateService.class);
    private final ClusterServiceCommandService commandService = mock(ClusterServiceCommandService.class);
    private final ClusterServiceInstanceService serviceInstanceService = mock(ClusterServiceInstanceService.class);
    private final ClusterServiceRoleInstanceService roleInstanceService =
            mock(ClusterServiceRoleInstanceService.class);
    private final OtelSelfMetricsClient metricsClient = mock(OtelSelfMetricsClient.class);
    private final RustfsEndpointProvider rustfsEndpointProvider = mock(RustfsEndpointProvider.class);

    private final PhysicalClusterInitializationServiceImpl service =
            new PhysicalClusterInitializationServiceImpl(clusterInfoService, clusterHostService, workerRegistry,
                    workerCommandClient, serviceInstallService, installDelegateService, commandService,
                    serviceInstanceService, roleInstanceService, metricsClient, rustfsEndpointProvider);

    private ClusterInfoEntity cluster;
    private ClusterHostDO host;

    @BeforeEach
    void setUp() {
        cluster = new ClusterInfoEntity();
        cluster.setId(7);
        cluster.setArchType(ClusterArchType.physical);
        cluster.setClusterState(ClusterState.NEED_CONFIG);
        when(clusterInfoService.getById(7)).thenReturn(cluster);

        host = new ClusterHostDO();
        host.setHostname("ddh-01");
        host.setIp("192.168.10.131");
        host.setClusterId(7);
        host.setManaged(MANAGED.YES);
        when(clusterHostService.getHostListByClusterId(7)).thenReturn(List.of(host));
        when(rustfsEndpointProvider.getEndpoint()).thenReturn("http://192.168.10.131:9040");
    }

    @Test
    void startsCollectorInstallAfterWorkerIpPingAndUsesIpEndpoint() {
        when(workerRegistry.getEndpoint("ddh-01"))
                .thenReturn(Optional.of(new WorkerEndpoint("ddh-01", "192.168.10.131", 18082, "x86_64", 7)));
        when(workerCommandClient.ping("ddh-01")).thenReturn(ExecResult.success());
        when(commandService.getLatestCommand(7, "OTELCOLLECTOR")).thenReturn(null);
        ServiceConfig endpoint = config("s3Endpoint", "http://192.168.10.131:9040");
        when(serviceInstallService.getServiceConfigFromDdl(7, "OTELCOLLECTOR"))
                .thenReturn(List.of(endpoint));
        when(installDelegateService.generateGenericInstallCommand(7, List.of("OTELCOLLECTOR")))
                .thenReturn("dag-1");

        PhysicalClusterInitializationResponse response = service.start(7);

        assertThat(response.getDagId()).isEqualTo("dag-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServiceRoleHostMapping>> mappings = ArgumentCaptor.forClass(List.class);
        verify(serviceInstallService).saveServiceRoleHostMapping(org.mockito.ArgumentMatchers.eq(7), mappings.capture());
        assertThat(mappings.getValue()).singleElement().satisfies(mapping -> {
            assertThat(mapping.getServiceRole()).isEqualTo("OtelCollector");
            assertThat(mapping.getHosts()).containsExactly("ddh-01");
        });
        verify(serviceInstallService).saveServiceConfig(7, "OTELCOLLECTOR", List.of(endpoint), -1);
        ArgumentCaptor<RunDagDto> runDag = ArgumentCaptor.forClass(RunDagDto.class);
        verify(installDelegateService).redeploy(runDag.capture());
        assertThat(runDag.getValue().getDagId()).isEqualTo("dag-1");
    }

    @Test
    void rejectsWorkerThatRegisteredWithoutItsIp() {
        when(workerRegistry.getEndpoint("ddh-01"))
                .thenReturn(Optional.of(new WorkerEndpoint("ddh-01", "", 18082, "x86_64", 7)));

        assertThatThrownBy(() -> service.start(7))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未使用节点 IP 注册");

        verify(installDelegateService, never()).generateGenericInstallCommand(any(), any());
    }

    @Test
    void completesClusterOnlyWhenWorkerAndCollectorAreHealthy() {
        when(workerRegistry.getEndpoint("ddh-01"))
                .thenReturn(Optional.of(new WorkerEndpoint("ddh-01", "192.168.10.131", 18082, "x86_64", 7)));
        when(workerCommandClient.ping("ddh-01")).thenReturn(ExecResult.success());
        ClusterServiceCommandEntity command = command(CommandState.SUCCESS);
        when(commandService.getLatestCommand(7, "OTELCOLLECTOR")).thenReturn(command);
        when(serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(7, "OTELCOLLECTOR"))
                .thenReturn(new ClusterServiceInstanceEntity());
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname("ddh-01");
        role.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "OtelCollector"))
                .thenReturn(List.of(role));
        when(metricsClient.fetch("192.168.10.131"))
                .thenReturn(new OtelSelfMetrics(0, 10, 20, 10, 0, 0, 60, 0));

        PhysicalClusterInitializationResponse response = service.getStatus(7);

        assertThat(response.getPhase()).isEqualTo(InitializationPhase.COMPLETED);
        assertThat(response.isCompleted()).isTrue();
        verify(clusterInfoService).updateClusterState(7, ClusterState.RUNNING.getValue());
    }

    @Test
    void keepsClusterPendingWhenCollectorMetricsAreNotHealthy() {
        when(workerRegistry.getEndpoint("ddh-01"))
                .thenReturn(Optional.of(new WorkerEndpoint("ddh-01", "192.168.10.131", 18082, "x86_64", 7)));
        when(workerCommandClient.ping("ddh-01")).thenReturn(ExecResult.success());
        when(commandService.getLatestCommand(7, "OTELCOLLECTOR")).thenReturn(command(CommandState.SUCCESS));
        when(serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(7, "OTELCOLLECTOR"))
                .thenReturn(new ClusterServiceInstanceEntity());
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname("ddh-01");
        role.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "OtelCollector"))
                .thenReturn(List.of(role));
        when(metricsClient.fetch("192.168.10.131"))
                .thenReturn(new OtelSelfMetrics(0, 10, 0, 1, 0, 0, 60, 0));

        PhysicalClusterInitializationResponse response = service.getStatus(7);

        assertThat(response.getPhase()).isEqualTo(InitializationPhase.VERIFYING);
        assertThat(response.isCompleted()).isFalse();
        verify(clusterInfoService, never()).updateClusterState(any(), any());
    }

    @Test
    void validatesIpv4WithoutResolvingHostnames() {
        assertThat(PhysicalClusterInitializationServiceImpl.isIpAddress("192.168.10.131")).isTrue();
        assertThat(PhysicalClusterInitializationServiceImpl.isIpAddress("ddh-01")).isFalse();
        assertThat(PhysicalClusterInitializationServiceImpl.isIpAddress("192.168.10.999")).isFalse();
    }

    private static ServiceConfig config(String name, String value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }

    private static ClusterServiceCommandEntity command(CommandState state) {
        ClusterServiceCommandEntity command = new ClusterServiceCommandEntity();
        command.setCommandState(state);
        command.setEndTime(new Date());
        return command;
    }
}
