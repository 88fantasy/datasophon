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

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.domain.alert.model.AlertLabels;
import com.datasophon.domain.alert.model.AlertMessage;
import com.datasophon.domain.alert.model.Alerts;
import com.datasophon.domain.alert.model.Annotations;

import java.util.List;
import java.util.Set;
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
    
    private final OtelMonitorService monitorService;
    private final ClusterInfoService clusterInfoService;
    private final ClusterAlertHistoryService alertHistoryService;
    private final double queueWatermark;
    private final double sendFailedRate;
    private final Set<String> firingAlerts = ConcurrentHashMap.newKeySet();
    
    public OtelAlertScheduler(OtelMonitorService monitorService,
                              ClusterInfoService clusterInfoService,
                              ClusterAlertHistoryService alertHistoryService,
                              @Value("${datasophon.observability.otel-alert.queue-watermark:0.8}") double queueWatermark,
                              @Value("${datasophon.observability.otel-alert.send-failed-rate:0.05}") double sendFailedRate) {
        this.monitorService = monitorService;
        this.clusterInfoService = clusterInfoService;
        this.alertHistoryService = alertHistoryService;
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
    
    private void updateAlert(Integer clusterId, String hostname, String alertName,
                             boolean firing, String description, String summary) {
        String key = clusterId + "|" + hostname + "|" + alertName;
        if (firing && !firingAlerts.contains(key)) {
            alertHistoryService.saveAlertHistory(alertMessage(
                    "firing", clusterId, hostname, alertName, description, summary));
            firingAlerts.add(key);
        } else if (!firing && firingAlerts.contains(key)) {
            alertHistoryService.saveAlertHistory(alertMessage(
                    "resolved", clusterId, hostname, alertName, description, summary));
            firingAlerts.remove(key);
        }
    }
    
    private static String alertMessage(String status, Integer clusterId, String hostname,
                                       String alertName, String description, String summary) {
        AlertLabels labels = new AlertLabels();
        labels.setAlertname(alertName);
        labels.setClusterId(clusterId);
        labels.setServiceRoleName("OtelCollector");
        labels.setInstance(hostname + ":8888");
        labels.setJob("otelcol-self-metrics");
        labels.setSeverity("warning");
        
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
}
