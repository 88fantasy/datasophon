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

package com.datasophon.api.lineage.metrics;

import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusVectorResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Reads live Spark job metrics from the cluster's Doris OTel store. */
@Service
public class LineageJobMetricsService {

    static final int MAX_APP_IDS = 50;
    private static final long RANGE_SECONDS = 120;
    private static final long RANGE_STEP_SECONDS = 15;

    private static final String COMPLETE_TASKS = "spark_threadpool_completeTasks";
    private static final String ACTIVE_TASKS = "spark_threadpool_activeTasks";
    private static final String RECORDS_WRITTEN = "spark_executor_recordsWritten";
    private static final String BYTES_WRITTEN = "spark_executor_bytesWritten";
    private static final String RUNNING_STAGES = "spark_dagscheduler_stage_runningStages";

    private static final Logger log = LoggerFactory.getLogger(LineageJobMetricsService.class);

    private final OtelMetricsQueryService queryService;
    private final Clock clock;

    @Autowired
    public LineageJobMetricsService(OtelMetricsQueryService queryService) {
        this(queryService, Clock.systemUTC());
    }

    LineageJobMetricsService(OtelMetricsQueryService queryService, Clock clock) {
        this.queryService = queryService;
        this.clock = clock;
    }

    public Map<String, JobMetrics> getJobMetrics(Integer clusterId, List<String> requestedAppIds) {
        List<String> appIds = normalizeAppIds(requestedAppIds);
        if (appIds.isEmpty()) {
            return Map.of();
        }
        if (requestedAppIds != null && requestedAppIds.size() > MAX_APP_IDS) {
            log.warn("Lineage job metrics appIds truncated: requested={}, limit={}, clusterId={}",
                    requestedAppIds.size(), MAX_APP_IDS, clusterId);
        }

        long sampledAt = clock.instant().getEpochSecond();
        String appIdRegex = exactRegex(appIds);
        Map<String, Double> completeTasks = queryInstantByApp(
                clusterId, COMPLETE_TASKS, "gauge", appIdRegex, sampledAt);
        Map<String, Double> activeTasks = queryInstantByApp(
                clusterId, ACTIVE_TASKS, "gauge", appIdRegex, sampledAt);
        Map<String, Double> recordsWritten = queryInstantByApp(
                clusterId, RECORDS_WRITTEN, "sum", appIdRegex, sampledAt);
        Map<String, Double> bytesWritten = queryInstantByApp(
                clusterId, BYTES_WRITTEN, "sum", appIdRegex, sampledAt);
        Map<String, Double> runningStages = queryInstantByApp(
                clusterId, RUNNING_STAGES, "gauge", appIdRegex, sampledAt);
        Map<String, Double> recordsWrittenRate = queryRateByApp(
                clusterId, RECORDS_WRITTEN, appIdRegex, sampledAt);

        Map<String, JobMetrics> metricsByApp = new LinkedHashMap<>();
        for (String appId : appIds) {
            if (!completeTasks.containsKey(appId) && !activeTasks.containsKey(appId)
                    && !recordsWritten.containsKey(appId) && !bytesWritten.containsKey(appId)
                    && !runningStages.containsKey(appId) && !recordsWrittenRate.containsKey(appId)) {
                continue;
            }
            metricsByApp.put(appId, new JobMetrics(
                    toLong(completeTasks.get(appId)),
                    toLong(activeTasks.get(appId)),
                    toLong(recordsWritten.get(appId)),
                    toLong(bytesWritten.get(appId)),
                    recordsWrittenRate.get(appId),
                    toLong(runningStages.get(appId)),
                    Instant.ofEpochSecond(sampledAt)));
        }
        return metricsByApp;
    }

    static List<String> normalizeAppIds(List<String> requestedAppIds) {
        if (requestedAppIds == null || requestedAppIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String appId : requestedAppIds) {
            if (appId == null || appId.isBlank()) {
                continue;
            }
            distinct.add(appId.trim());
            if (distinct.size() == MAX_APP_IDS) {
                break;
            }
        }
        return new ArrayList<>(distinct);
    }

    private Map<String, Double> queryInstantByApp(Integer clusterId, String metric, String table,
                                                  String appIdRegex, long sampledAt) {
        PrometheusVectorResult result = queryService.queryInstant(
                clusterId, metric, "sum", 1.0, ".+", ".+", Map.of(), Map.of(),
                Map.of("app_id", appIdRegex), Map.of(), sampledAt, table, List.of("app_id"));
        Map<String, Double> valuesByApp = new LinkedHashMap<>();
        for (PrometheusVectorResult.VectorSample sample : result.result()) {
            String appId = sample.metric().get("app_id");
            Double value = numericValue(sample.value());
            if (appId != null && value != null) {
                valuesByApp.merge(appId, value, Double::sum);
            }
        }
        return valuesByApp;
    }

    private Map<String, Double> queryRateByApp(Integer clusterId, String metric,
                                               String appIdRegex, long sampledAt) {
        PrometheusMatrixResult result = queryService.queryRange(
                clusterId, metric, "1m", 1.0, ".+", ".+", Map.of(), Map.of(),
                Map.of("app_id", appIdRegex), Map.of(), List.of("app_id"),
                sampledAt - RANGE_SECONDS, sampledAt, RANGE_STEP_SECONDS, "sum", 0.5, null);
        Map<String, Double> valuesByApp = new LinkedHashMap<>();
        for (PrometheusMatrixResult.MatrixSeries series : result.result()) {
            String appId = series.metric().get("app_id");
            List<Object[]> values = series.values();
            if (appId == null || values.isEmpty()) {
                continue;
            }
            Double value = numericValue(values.get(values.size() - 1));
            if (value != null) {
                valuesByApp.merge(appId, value, Double::sum);
            }
        }
        return valuesByApp;
    }

    static String exactRegex(List<String> values) {
        StringBuilder regex = new StringBuilder("^(?:");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                regex.append('|');
            }
            for (char ch : values.get(i).toCharArray()) {
                if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0) {
                    regex.append('\\');
                }
                regex.append(ch);
            }
        }
        return regex.append(")$").toString();
    }

    private static Double numericValue(Object[] sample) {
        if (sample == null || sample.length < 2 || sample[1] == null) {
            return null;
        }
        try {
            return Double.valueOf(sample[1].toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long toLong(Double value) {
        return value == null ? 0L : value.longValue();
    }

    public record JobMetrics(
                             long completeTasks,
                             long activeTasks,
                             long recordsWritten,
                             long bytesWritten,
                             Double recordsWrittenRate,
                             long runningStages,
                             Instant sampledAt) {
    }
}
