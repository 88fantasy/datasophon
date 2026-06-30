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

import com.datasophon.api.observability.PrometheusMatrixResult.MatrixSeries;
import com.datasophon.api.observability.PrometheusVectorResult.VectorSample;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.QuotaState;
import com.datasophon.domain.alert.model.AlertLabels;
import com.datasophon.domain.alert.model.AlertMessage;
import com.datasophon.domain.alert.model.Alerts;
import com.datasophon.domain.alert.model.Annotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

@Component
public class OtelAlertScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(OtelAlertScheduler.class);
    private static final String QUEUE_ALERT = "OtelCollectorQueueHigh";
    private static final String SEND_FAILURE_ALERT = "OtelCollectorSendFailureRateHigh";
    private static final String DEFAULT_INSTANCE = ".+";
    private static final String DEFAULT_JOB = ".+";
    private static final long RANGE_SECONDS = 120L;
    private static final long RANGE_STEP_SECONDS = 60L;
    private static final double DEFAULT_QUANTILE = 0.95d;
    private static final Map<String, OtelAlertRuleSpec> METRIC_RULE_SPECS = metricRuleSpecs();
    
    private final OtelMonitorService monitorService;
    private final ClusterInfoService clusterInfoService;
    private final ClusterAlertHistoryService alertHistoryService;
    private final OtelMetricsQueryService metricsQueryService;
    private final ClusterAlertQuotaService alertQuotaService;
    private final double queueWatermark;
    private final double sendFailedRate;
    private final Set<String> firingAlerts = ConcurrentHashMap.newKeySet();
    
    public OtelAlertScheduler(OtelMonitorService monitorService,
                              ClusterInfoService clusterInfoService,
                              ClusterAlertHistoryService alertHistoryService,
                              OtelMetricsQueryService metricsQueryService,
                              ClusterAlertQuotaService alertQuotaService,
                              @Value("${datasophon.observability.otel-alert.queue-watermark:0.8}") double queueWatermark,
                              @Value("${datasophon.observability.otel-alert.send-failed-rate:0.05}") double sendFailedRate) {
        this.monitorService = monitorService;
        this.clusterInfoService = clusterInfoService;
        this.alertHistoryService = alertHistoryService;
        this.metricsQueryService = metricsQueryService;
        this.alertQuotaService = alertQuotaService;
        this.queueWatermark = queueWatermark;
        this.sendFailedRate = sendFailedRate;
    }
    
    @Scheduled(initialDelayString = "${datasophon.observability.otel-alert.initial-delay-ms:30000}", fixedDelayString = "${datasophon.observability.otel-alert.interval-ms:30000}")
    public void checkCollectors() {
        for (ClusterInfoEntity cluster : clusterInfoService.runningClusterList()) {
            try {
                evaluate(cluster.getId(), monitorService.collectAll(cluster.getId()));
            } catch (RuntimeException e) {
                log.warn("Failed to evaluate OtelCollector alerts for cluster {}: {}",
                        cluster.getId(), e.getMessage(), e);
            }
        }
    }
    
    @Scheduled(initialDelayString = "${datasophon.observability.otel-metric-alert.initial-delay-ms:60000}", fixedDelayString = "${datasophon.observability.otel-metric-alert.interval-ms:60000}")
    public void checkMetricRules() {
        List<ClusterAlertQuota> quotas = metricQuotas();
        if (quotas.isEmpty()) {
            return;
        }
        for (ClusterInfoEntity cluster : clusterInfoService.runningClusterList()) {
            for (ClusterAlertQuota quota : quotas) {
                try {
                    evaluateMetricRule(cluster.getId(), quota);
                } catch (RuntimeException e) {
                    log.warn("Failed to evaluate OTel metric alert {} for cluster {}: {}",
                            quota.getAlertQuotaName(), cluster.getId(), e.getMessage(), e);
                }
            }
        }
    }
    
    private void evaluate(Integer clusterId, List<NodeOtelMetrics> nodes) {
        for (NodeOtelMetrics node : nodes) {
            if (!node.healthy() || node.metrics() == null) {
                continue;
            }
            OtelSelfMetrics metrics = node.metrics();
            // queueCapacity==0 表示指标暂不可用（采集器启动中或版本差异），跳过本轮队列评估
            if (metrics.queueCapacity() > 0) {
                double queueRatio = (double) metrics.queueSize() / metrics.queueCapacity();
                updateAlert(clusterId, node.hostname(), QUEUE_ALERT,
                        queueRatio >= queueWatermark,
                        "Collector queue usage is " + queueRatio,
                        "Check Doris/S3 availability and exporter throughput");
            }
            long attempts = metrics.sentTotal() + metrics.sendFailedTotal();
            double failureRate = attempts == 0 ? 0d : (double) metrics.sendFailedTotal() / attempts;
            updateAlert(clusterId, node.hostname(), SEND_FAILURE_ALERT,
                    failureRate >= sendFailedRate,
                    "Collector send failure rate is " + failureRate,
                    "Check exporter endpoint, credentials, and network connectivity");
        }
    }
    
    void evaluateMetricRule(Integer clusterId, ClusterAlertQuota quota) {
        OtelAlertRuleSpec spec = METRIC_RULE_SPECS.get(quota.getAlertQuotaName());
        if (spec == null || metricsQueryService == null) {
            return;
        }
        Map<String, MetricValue> values = spec.denomMetric == null
                ? latestPerSeries(clusterId, spec.metric, spec.table, spec.rateWindow, spec.agg, spec.filters)
                : ratioPerSeries(clusterId, spec);
        for (Map.Entry<String, MetricValue> entry : values.entrySet()) {
            MetricValue value = entry.getValue();
            double scaledValue = value.value * spec.scale;
            String alertName = quota.getAlertQuotaName();
            updateAlert(clusterId, entry.getKey(), alertName,
                    compare(scaledValue, quota.getCompareMethod(), quota.getAlertThreshold()),
                    description(alertName, scaledValue, quota.getCompareMethod(), quota.getAlertThreshold()),
                    advice(quota),
                    quota.getServiceRoleName(),
                    instance(value.labels),
                    "otel-metric-rules",
                    severity(quota.getAlertLevel()));
        }
    }
    
    private void updateAlert(Integer clusterId, String hostname, String alertName,
                             boolean firing, String description, String summary) {
        updateAlert(clusterId, hostname, alertName, firing, description, summary,
                "OtelCollector", hostname + ":8888", "otelcol-self-metrics", "warning");
    }
    
    private void updateAlert(Integer clusterId, String seriesKey, String alertName,
                             boolean firing, String description, String summary,
                             String serviceRoleName, String instance, String job, String severity) {
        String key = clusterId + "|" + seriesKey + "|" + alertName;
        if (firing && !firingAlerts.contains(key)) {
            alertHistoryService.saveAlertHistory(alertMessage(
                    "firing", clusterId, alertName, description, summary,
                    serviceRoleName, instance, job, severity));
            firingAlerts.add(key);
        } else if (!firing && firingAlerts.contains(key)) {
            alertHistoryService.saveAlertHistory(alertMessage(
                    "resolved", clusterId, alertName, description, summary,
                    serviceRoleName, instance, job, severity));
            firingAlerts.remove(key);
        }
    }
    
    private static String alertMessage(String status, Integer clusterId, String alertName,
                                       String description, String summary, String serviceRoleName,
                                       String instance, String job, String severity) {
        AlertLabels labels = new AlertLabels();
        labels.setAlertname(alertName);
        labels.setClusterId(clusterId);
        labels.setServiceRoleName(serviceRoleName);
        labels.setInstance(instance);
        labels.setJob(job);
        labels.setSeverity(severity);
        
        Annotations annotations = new Annotations();
        annotations.setDescription(description);
        annotations.setSummary(summary);
        
        Alerts alert = new Alerts();
        alert.setStatus(status);
        alert.setLabels(labels);
        alert.setAnnotations(annotations);
        
        AlertMessage message = new AlertMessage();
        message.setStatus(status);
        message.setAlerts(List.of(alert));
        return JSONObject.toJSONString(message);
    }
    
    List<ClusterAlertQuota> metricQuotas() {
        if (alertQuotaService == null) {
            return Collections.emptyList();
        }
        return alertQuotaService.lambdaQuery()
                .in(ClusterAlertQuota::getServiceCategory, "NEXUS", "DORIS")
                .eq(ClusterAlertQuota::getQuotaState, QuotaState.RUNNING)
                .list();
    }
    
    private Map<String, MetricValue> ratioPerSeries(Integer clusterId, OtelAlertRuleSpec spec) {
        Map<String, MetricValue> numerators = latestPerSeries(
                clusterId, spec.metric, spec.table, spec.rateWindow, spec.agg, spec.filters);
        Map<String, MetricValue> denominators = latestPerSeries(
                clusterId, spec.denomMetric, spec.denomTable, spec.rateWindow, spec.denomAgg, spec.denomFilters);
        Map<String, MetricValue> ratios = new LinkedHashMap<>();
        for (Map.Entry<String, MetricValue> entry : numerators.entrySet()) {
            MetricValue denom = denominators.get(entry.getKey());
            if (denom == null || denom.value == 0d) {
                continue;
            }
            ratios.put(entry.getKey(), new MetricValue(entry.getValue().value / denom.value, entry.getValue().labels));
        }
        return ratios;
    }
    
    private Map<String, MetricValue> latestPerSeries(Integer clusterId, String metric, String table,
                                                     String rateWindow, String agg,
                                                     Map<String, String> filters) {
        return rateWindow == null || rateWindow.isBlank()
                ? latestVector(clusterId, metric, agg, filters)
                : latestMatrix(clusterId, metric, table, rateWindow, filters);
    }
    
    private Map<String, MetricValue> latestVector(Integer clusterId, String metric, String agg,
                                                  Map<String, String> filters) {
        PrometheusVectorResult result = metricsQueryService.queryInstant(
                clusterId, metric, agg, 1d, DEFAULT_INSTANCE, DEFAULT_JOB, filters, null,
                System.currentTimeMillis() / 1000);
        Map<String, MetricValue> values = new LinkedHashMap<>();
        for (VectorSample sample : result.result()) {
            values.put(seriesKey(sample.metric()), new MetricValue(toDouble(sample.value()[1]), sample.metric()));
        }
        return values;
    }
    
    private Map<String, MetricValue> latestMatrix(Integer clusterId, String metric, String table,
                                                  String rateWindow, Map<String, String> filters) {
        long end = System.currentTimeMillis() / 1000;
        PrometheusMatrixResult result = metricsQueryService.queryRange(
                clusterId, metric, rateWindow, 1d, DEFAULT_INSTANCE, DEFAULT_JOB, filters, null,
                List.of(), end - RANGE_SECONDS, end, RANGE_STEP_SECONDS, table, DEFAULT_QUANTILE);
        Map<String, MetricValue> values = new LinkedHashMap<>();
        for (MatrixSeries series : result.result()) {
            if (series.values().isEmpty()) {
                continue;
            }
            Object[] last = series.values().get(series.values().size() - 1);
            values.put(seriesKey(series.metric()), new MetricValue(toDouble(last[1]), series.metric()));
        }
        return values;
    }
    
    private static boolean compare(double value, String compareMethod, Long threshold) {
        double limit = threshold == null ? 0d : threshold.doubleValue();
        return switch (compareMethod) {
            case "<" -> value < limit;
            case "!=" -> Double.compare(value, limit) != 0;
            default -> value > limit;
        };
    }
    
    private static String description(String alertName, double value, String compareMethod, Long threshold) {
        return alertName + " value is " + value + ", threshold is " + compareMethod + " " + threshold;
    }
    
    private static String advice(ClusterAlertQuota quota) {
        return quota.getAlertAdvice() == null ? quota.getAlertQuotaName() : quota.getAlertAdvice();
    }
    
    private static String severity(AlertLevel level) {
        return AlertLevel.EXCEPTION.equals(level) ? "critical" : "warning";
    }
    
    private static String instance(Map<String, String> labels) {
        String instance = labels.get("instance");
        return instance == null || instance.isBlank() ? "unknown" : instance;
    }
    
    private static String seriesKey(Map<String, String> labels) {
        return JSONObject.toJSONString(new TreeMap<>(labels));
    }
    
    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(Objects.toString(value));
    }
    
    private static Map<String, OtelAlertRuleSpec> metricRuleSpecs() {
        Map<String, OtelAlertRuleSpec> specs = new HashMap<>();
        specs.put("Nexus实例只读", gauge("readonly_enabled", "max", 1d, Map.of()));
        specs.put("Nexus线程死锁", gauge("jvm_thread_states_deadlock_count", "max", 1d, Map.of()));
        specs.put("Nexus堆内存使用率", gauge("jvm_memory_heap_usage", "max", 100d, Map.of()));
        specs.put("Nexus文件描述符使用率", gauge("jvm_fd_usage", "max", 100d, Map.of()));
        specs.put("DorisBE磁盘使用率", ratio(
                "doris_be_disks_local_used_capacity", "gauge", null, "sum", Map.of("group", "be"),
                "doris_be_disks_total_capacity", "gauge", "sum", Map.of("group", "be"), 100d));
        specs.put("DorisFE堆内存使用率", ratio(
                "jvm_heap_size_bytes", "gauge", null, null, Map.of("group", "fe", "type", "used"),
                "jvm_heap_size_bytes", "gauge", null, Map.of("group", "fe", "type", "max"), 100d));
        specs.put("Doris查询错误率", ratio(
                "doris_fe_query_err", "sum", "2m", null, Map.of("group", "fe"),
                "doris_fe_query_total", "sum", null, Map.of("group", "fe"), 100d));
        return Collections.unmodifiableMap(specs);
    }
    
    private static OtelAlertRuleSpec gauge(String metric, String agg, double scale, Map<String, String> filters) {
        return new OtelAlertRuleSpec(metric, "gauge", null, agg, filters, null, null, null, null, scale);
    }
    
    private static OtelAlertRuleSpec ratio(String metric, String table, String rateWindow, String agg,
                                           Map<String, String> filters, String denomMetric, String denomTable,
                                           String denomAgg, Map<String, String> denomFilters, double scale) {
        return new OtelAlertRuleSpec(metric, table, rateWindow, agg, filters,
                denomMetric, denomTable, denomAgg, denomFilters, scale);
    }
    
    private record MetricValue(double value, Map<String, String> labels) {
    }
    
    private record OtelAlertRuleSpec(String metric, String table, String rateWindow, String agg,
                                     Map<String, String> filters, String denomMetric, String denomTable,
                                     String denomAgg, Map<String, String> denomFilters, double scale) {
    }
}
