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

package com.datasophon.api.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import org.junit.jupiter.api.Test;

class OtelMonitorServiceTest {

    private final ClusterServiceRoleInstanceService roleInstanceService =
            mock(ClusterServiceRoleInstanceService.class);
    private final ClusterHostService clusterHostService = mock(ClusterHostService.class);
    private final OtelSelfMetricsClient metricsClient = mock(OtelSelfMetricsClient.class);
    private final OtelMonitorService service =
            new OtelMonitorService(roleInstanceService, clusterHostService, metricsClient);

    @Test
    void collectsOnlyRunningCollectorInstances() {
        ClusterServiceRoleInstanceEntity running = role("worker-1", ServiceRoleState.RUNNING);
        ClusterServiceRoleInstanceEntity stopped = role("worker-2", ServiceRoleState.STOP);
        OtelSelfMetrics metrics = new OtelSelfMetrics(1, 10, 20, 0, 0, 0, 60, 0);
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "OtelCollector"))
                .thenReturn(List.of(running, stopped));
        when(clusterHostService.getHostListByClusterId(7))
                .thenReturn(List.of(host("worker-1", "192.168.10.131"), host("worker-2", "192.168.10.132")));
        when(metricsClient.fetch("192.168.10.131")).thenReturn(metrics);

        List<NodeOtelMetrics> result = service.collectAll(7);

        assertThat(result).containsExactly(new NodeOtelMetrics("worker-1", true, null, metrics));
        verify(metricsClient).fetch("192.168.10.131");
    }

    @Test
    void marksFailedNodeUnhealthyWithoutAbortingOtherNodes() {
        ClusterServiceRoleInstanceEntity failed = role("worker-1", ServiceRoleState.RUNNING);
        ClusterServiceRoleInstanceEntity healthy = role("worker-2", ServiceRoleState.RUNNING);
        OtelSelfMetrics metrics = new OtelSelfMetrics(0, 10, 20, 0, 0, 0, 60, 0);
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "OtelCollector"))
                .thenReturn(List.of(failed, healthy));
        when(clusterHostService.getHostListByClusterId(7))
                .thenReturn(List.of(host("worker-1", "192.168.10.131"), host("worker-2", "192.168.10.132")));
        when(metricsClient.fetch("192.168.10.131")).thenThrow(new IllegalStateException("connection refused"));
        when(metricsClient.fetch("192.168.10.132")).thenReturn(metrics);

        List<NodeOtelMetrics> result = service.collectAll(7);

        assertThat(result.get(0).hostname()).isEqualTo("worker-1");
        assertThat(result.get(0).healthy()).isFalse();
        assertThat(result.get(0).error()).isEqualTo("connection refused");
        assertThat(result.get(0).metrics()).isNull();
        assertThat(result.get(1)).isEqualTo(new NodeOtelMetrics("worker-2", true, null, metrics));
    }

    @Test
    void returnsEmptyWhenNoCollectorInstancesExist() {
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "OtelCollector"))
                .thenReturn(List.of());
        when(clusterHostService.getHostListByClusterId(7)).thenReturn(List.of());
        
        assertThat(service.collectAll(7)).isEmpty();
        verifyNoInteractions(metricsClient);
    }

    private static ClusterServiceRoleInstanceEntity role(String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }

    private static ClusterHostDO host(String hostname, String ip) {
        ClusterHostDO host = new ClusterHostDO();
        host.setHostname(hostname);
        host.setIp(ip);
        return host;
    }
}
