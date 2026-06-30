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

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.QuotaState;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class OtelAlertSchedulerTest {
    
    @Test
    void emitsOneFiringAlertUntilQueueWatermarkResolves() {
        AtomicReference<List<NodeOtelMetrics>> metrics = new AtomicReference<>(List.of(
                healthy("worker-1", new OtelSelfMetrics(90, 100, 100, 0, 0, 0))));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(metrics, alerts);
        
        scheduler.checkCollectors();
        scheduler.checkCollectors();
        metrics.set(List.of(healthy("worker-1", new OtelSelfMetrics(10, 100, 100, 0, 0, 0))));
        scheduler.checkCollectors();
        
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0)).contains("\"status\":\"firing\"")
                .contains("OtelCollectorQueueHigh");
        assertThat(alerts.get(1)).contains("\"status\":\"resolved\"")
                .contains("OtelCollectorQueueHigh");
    }
    
    @Test
    void emitsSendFailureAlertOnlyAboveConfiguredRate() {
        AtomicReference<List<NodeOtelMetrics>> metrics = new AtomicReference<>(List.of(
                healthy("worker-1", new OtelSelfMetrics(0, 100, 90, 10, 0, 0))));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(metrics, alerts);
        
        scheduler.checkCollectors();
        
        assertThat(alerts).singleElement().asString()
                .contains("OtelCollectorSendFailureRateHigh")
                .contains("\"status\":\"firing\"");
    }
    
    @Test
    void emitsOneMetricFiringAlertUntilQuotaResolves() {
        MutableMetricQueryService query = new MutableMetricQueryService();
        query.instant = vector(sample(Map.of("instance", "nexus-1:8081", "job", "nexus"), 100, "1.0"));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(query, alerts, List.of(quota(
                "Nexus实例只读", "NEXUS", "readonly_enabled", AlertLevel.EXCEPTION,
                ">", 0, "Nexus repository is read-only", "NexusRepository")));
        
        scheduler.checkMetricRules();
        scheduler.checkMetricRules();
        query.instant = vector(sample(Map.of("instance", "nexus-1:8081", "job", "nexus"), 160, "0.0"));
        scheduler.checkMetricRules();
        
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0)).contains("\"status\":\"firing\"")
                .contains("Nexus实例只读")
                .contains("NexusRepository")
                .contains("critical")
                .contains("nexus-1:8081");
        assertThat(alerts.get(1)).contains("\"status\":\"resolved\"")
                .contains("Nexus实例只读");
    }
    
    @Test
    void evaluatesRatioRulesByMatchingLabels() {
        MutableMetricQueryService query = new MutableMetricQueryService();
        query.instantByMetric.put("doris_be_disks_local_used_capacity", vector(
                sample(Map.of("instance", "be-1:8040", "job", "doris-be"), 100, "90"),
                sample(Map.of("instance", "be-2:8040", "job", "doris-be"), 100, "30")));
        query.instantByMetric.put("doris_be_disks_total_capacity", vector(
                sample(Map.of("instance", "be-1:8040", "job", "doris-be"), 100, "100"),
                sample(Map.of("instance", "be-2:8040", "job", "doris-be"), 100, "100")));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(query, alerts, List.of(quota(
                "DorisBE磁盘使用率", "DORIS", "doris_be_disks_local_used_capacity", AlertLevel.EXCEPTION,
                ">", 85, "Clean Doris BE disk", "DorisBE")));
        
        scheduler.checkMetricRules();
        
        assertThat(alerts).singleElement().asString()
                .contains("DorisBE磁盘使用率")
                .contains("be-1:8040")
                .contains("90.0");
    }
    
    @Test
    void rateRulesUseLastRangePoint() {
        MutableMetricQueryService query = new MutableMetricQueryService();
        query.rangeByMetric.put("doris_fe_query_err", matrix(
                series(Map.of("instance", "fe-1:8030", "job", "doris-fe"),
                        point(100, "1.0"), point(160, "6.0"))));
        query.rangeByMetric.put("doris_fe_query_total", matrix(
                series(Map.of("instance", "fe-1:8030", "job", "doris-fe"),
                        point(100, "100.0"), point(160, "100.0"))));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(query, alerts, List.of(quota(
                "Doris查询错误率", "DORIS", "doris_fe_query_err", AlertLevel.WARN,
                ">", 5, "Check Doris FE query errors", "DorisFE")));
        
        scheduler.checkMetricRules();
        
        assertThat(alerts).singleElement().asString()
                .contains("Doris查询错误率")
                .contains("warning")
                .contains("6.0");
    }
    
    @Test
    void supportsLessThanAndNotEqualsCompareMethods() {
        MutableMetricQueryService query = new MutableMetricQueryService();
        query.instantByMetric.put("jvm_memory_heap_usage", vector(
                sample(Map.of("instance", "nexus-1:8081", "job", "nexus"), 100, "0.10")));
        query.instantByMetric.put("jvm_thread_states_deadlock_count", vector(
                sample(Map.of("instance", "nexus-1:8081", "job", "nexus"), 100, "1.0")));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(query, alerts, List.of(
                quota("Nexus堆内存使用率", "NEXUS", "jvm_memory_heap_usage", AlertLevel.WARN,
                        "<", 20, "Heap too low for test", "NexusRepository"),
                quota("Nexus线程死锁", "NEXUS", "jvm_thread_states_deadlock_count", AlertLevel.EXCEPTION,
                        "!=", 0, "Thread deadlock", "NexusRepository")));
        
        scheduler.checkMetricRules();
        
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0)).contains("Nexus堆内存使用率");
        assertThat(alerts.get(1)).contains("Nexus线程死锁");
    }
    
    @Test
    void metricRuleQueryFailureDoesNotSkipOtherRules() {
        MutableMetricQueryService query = new MutableMetricQueryService();
        query.throwMetrics.add("readonly_enabled");
        query.instantByMetric.put("jvm_thread_states_deadlock_count", vector(
                sample(Map.of("instance", "nexus-1:8081", "job", "nexus"), 100, "1.0")));
        List<String> alerts = new ArrayList<>();
        OtelAlertScheduler scheduler = scheduler(query, alerts, List.of(
                quota("Nexus实例只读", "NEXUS", "readonly_enabled", AlertLevel.EXCEPTION,
                        ">", 0, "Read only", "NexusRepository"),
                quota("Nexus线程死锁", "NEXUS", "jvm_thread_states_deadlock_count", AlertLevel.EXCEPTION,
                        ">", 0, "Thread deadlock", "NexusRepository")));
        
        scheduler.checkMetricRules();
        
        assertThat(alerts).singleElement().asString().contains("Nexus线程死锁");
    }
    
    private static OtelAlertScheduler scheduler(AtomicReference<List<NodeOtelMetrics>> metrics,
                                                List<String> alerts) {
        OtelMonitorService monitor = new OtelMonitorService(null, null) {
            @Override
            public List<NodeOtelMetrics> collectAll(Integer clusterId) {
                return metrics.get();
            }
        };
        ClusterInfoService clusters = proxy(ClusterInfoService.class, (method, args) -> {
            if ("runningClusterList".equals(method)) {
                ClusterInfoEntity cluster = new ClusterInfoEntity();
                cluster.setId(7);
                return List.of(cluster);
            }
            return null;
        });
        ClusterAlertHistoryService history = proxy(ClusterAlertHistoryService.class, (method, args) -> {
            if ("saveAlertHistory".equals(method)) {
                alerts.add((String) args[0]);
            }
            return null;
        });
        return new OtelAlertScheduler(monitor, clusters, history, null, null, 0.8d, 0.05d);
    }
    
    private static OtelAlertScheduler scheduler(MutableMetricQueryService query, List<String> alerts,
                                                List<ClusterAlertQuota> quotas) {
        OtelMonitorService monitor = new OtelMonitorService(null, null);
        ClusterInfoService clusters = clusters();
        ClusterAlertHistoryService history = proxy(ClusterAlertHistoryService.class, (method, args) -> {
            if ("saveAlertHistory".equals(method)) {
                alerts.add((String) args[0]);
            }
            return null;
        });
        return new OtelAlertScheduler(monitor, clusters, history, query, null, 0.8d, 0.05d) {
            @Override
            List<ClusterAlertQuota> metricQuotas() {
                return quotas;
            }
        };
    }
    
    private static ClusterInfoService clusters() {
        return proxy(ClusterInfoService.class, (method, args) -> {
            if ("runningClusterList".equals(method)) {
                ClusterInfoEntity cluster = new ClusterInfoEntity();
                cluster.setId(7);
                return List.of(cluster);
            }
            return null;
        });
    }
    
    private static ClusterAlertQuota quota(String name, String service, String expr, AlertLevel level,
                                           String compareMethod, long threshold, String advice,
                                           String roleName) {
        ClusterAlertQuota quota = new ClusterAlertQuota();
        quota.setAlertQuotaName(name);
        quota.setServiceCategory(service);
        quota.setAlertExpr(expr);
        quota.setAlertLevel(level);
        quota.setAlertAdvice(advice);
        quota.setCompareMethod(compareMethod);
        quota.setAlertThreshold(threshold);
        quota.setServiceRoleName(roleName);
        quota.setQuotaState(QuotaState.RUNNING);
        return quota;
    }
    
    private static PrometheusVectorResult vector(PrometheusVectorResult.VectorSample... samples) {
        return PrometheusVectorResult.of(List.of(samples));
    }
    
    private static PrometheusVectorResult.VectorSample sample(Map<String, String> labels, long ts, String value) {
        return new PrometheusVectorResult.VectorSample(labels, new Object[]{ts, value});
    }
    
    private static PrometheusMatrixResult matrix(PrometheusMatrixResult.MatrixSeries... series) {
        return PrometheusMatrixResult.of(List.of(series));
    }
    
    private static PrometheusMatrixResult.MatrixSeries series(Map<String, String> labels, Object[]... values) {
        return new PrometheusMatrixResult.MatrixSeries(labels, List.of(values));
    }
    
    private static Object[] point(long ts, String value) {
        return new Object[]{ts, value};
    }
    
    private static NodeOtelMetrics healthy(String hostname, OtelSelfMetrics metrics) {
        return new NodeOtelMetrics(hostname, true, null, metrics);
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }
    
    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
    
    private static class MutableMetricQueryService extends OtelMetricsQueryService {
        
        private PrometheusVectorResult instant = vector();
        private final Map<String, PrometheusVectorResult> instantByMetric = new java.util.HashMap<>();
        private final Map<String, PrometheusMatrixResult> rangeByMetric = new java.util.HashMap<>();
        private final List<String> throwMetrics = new ArrayList<>();
        
        MutableMetricQueryService() {
            super(null, null);
        }
        
        @Override
        public PrometheusVectorResult queryInstant(Integer clusterId, String metric, String agg, double scale,
                                                   String instance, String job, Map<String, String> filters,
                                                   Map<String, String> filtersNe, long evalTime) {
            if (throwMetrics.contains(metric)) {
                throw new IllegalStateException("boom");
            }
            return instantByMetric.getOrDefault(metric, instant);
        }
        
        @Override
        public PrometheusMatrixResult queryRange(Integer clusterId, String metric, String rateWindow, double scale,
                                                 String instance, String job, Map<String, String> filters,
                                                 Map<String, String> filtersNe, List<String> groupByKeys,
                                                 long start, long end, long step, String table, double quantile) {
            if (throwMetrics.contains(metric)) {
                throw new IllegalStateException("boom");
            }
            return rangeByMetric.getOrDefault(metric, matrix());
        }
    }
}
