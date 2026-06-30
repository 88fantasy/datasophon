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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusVectorResult;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HostCheckServiceTest {

    @Test
    void updatesHostResourcesFromOtelWhenPrometheusServiceIsAbsent() {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(7);
        cluster.setClusterName("c1");
        cluster.setArchType(ClusterArchType.physical);
        ClusterHostDO host = new ClusterHostDO();
        host.setHostname("node-1");

        ClusterInfoService clusterInfoService = proxy(ClusterInfoService.class, (proxy, method, args) ->
                "getReadyClusterList".equals(method.getName()) ? List.of(cluster) : null);
        CapturingHostService hostServiceHandler = new CapturingHostService(host);
        ClusterHostService clusterHostService = proxy(ClusterHostService.class, hostServiceHandler);
        ClusterServiceRoleInstanceService roleInstanceService = proxy(
                ClusterServiceRoleInstanceService.class, (proxy, method, args) -> null);
        WorkerCommandClient workerCommandClient = new WorkerCommandClient(null, null) {
            @Override
            public ExecResult ping(String hostname) {
                ExecResult ping = new ExecResult();
                ping.setExecResult(true);
                return ping;
            }
        };
        CapturingMetricsQueryService metricsQueryService = new CapturingMetricsQueryService();

        HostCheckService service = new HostCheckService(clusterInfoService, clusterHostService,
                roleInstanceService, workerCommandClient, metricsQueryService, Runnable::run);
        service.checkHosts(null);

        ClusterHostDO updated = hostServiceHandler.updatedHosts.get(0);
        assertThat(updated.getTotalMem()).isEqualTo(16);
        assertThat(updated.getUsedMem()).isEqualTo(10);
        assertThat(updated.getTotalDisk()).isEqualTo(200);
        assertThat(updated.getUsedDisk()).isEqualTo(120);
        assertThat(updated.getAverageLoad()).isEqualTo("1.25");

        assertThat(metricsQueryService.filesystemFilters).allSatisfy(filters ->
                assertThat(filters).containsEntry("fstype", "ext.*|xfs"));
        assertThat(metricsQueryService.filesystemFiltersNe).allSatisfy(filtersNe ->
                assertThat(filtersNe).containsEntry("mountpoint", ".*pod.*"));
    }

    private static PrometheusVectorResult vector(double value) {
        return PrometheusVectorResult.of(List.of(new PrometheusVectorResult.VectorSample(
                java.util.Map.of("instance", "node-1:9100"),
                new Object[]{1L, String.valueOf(value)})));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static class CapturingHostService implements java.lang.reflect.InvocationHandler {
        private final ClusterHostDO host;
        private List<ClusterHostDO> updatedHosts = List.of();

        CapturingHostService(ClusterHostDO host) {
            this.host = host;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if ("getHostListByClusterId".equals(method.getName())) {
                return List.of(host);
            }
            if ("updateBatchById".equals(method.getName())) {
                updatedHosts = (List<ClusterHostDO>) args[0];
                return true;
            }
            return null;
        }
    }

    private static class CapturingMetricsQueryService extends OtelMetricsQueryService {
        private final List<Map<String, String>> filesystemFilters = new ArrayList<>();
        private final List<Map<String, String>> filesystemFiltersNe = new ArrayList<>();

        CapturingMetricsQueryService() {
            super(null, null);
        }

        @Override
        public PrometheusVectorResult queryInstant(Integer clusterId, String metric, String agg, double scale,
                                                   String instance, String job, Map<String, String> filters,
                                                   Map<String, String> filtersNe, long evalTime) {
            if (metric.startsWith("node_filesystem_")) {
                filesystemFilters.add(filters);
                filesystemFiltersNe.add(filtersNe);
            }
            return switch (metric) {
                case "node_memory_MemTotal_bytes" -> vector(16d * 1024 * 1024 * 1024);
                case "node_memory_MemAvailable_bytes" -> vector(6d * 1024 * 1024 * 1024);
                case "node_filesystem_size_bytes" -> vector(200d * 1024 * 1024 * 1024);
                case "node_filesystem_free_bytes" -> vector(80d * 1024 * 1024 * 1024);
                case "node_load5" -> vector(1.25d);
                default -> PrometheusVectorResult.of(List.of());
            };
        }
    }
}
