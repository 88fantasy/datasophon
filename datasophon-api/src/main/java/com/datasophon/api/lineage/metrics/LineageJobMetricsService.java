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
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads live job metrics (Spark or Flink) from the cluster's Doris OTel store.
 *
 * <p>Flink jobs are told apart from Spark ones purely by {@code appId} shape: a Flink JobID is a
 * fixed 32-char lowercase hex string ({@link #FLINK_JOB_ID}); Spark app IDs never match that
 * (they look like {@code local-<epoch>} / {@code application_<epoch>_<seq>}). This lets the same
 * {@code appIds} request param carry both engines' identifiers without a separate parameter, per
 * T16 (docs/data-lineage-Flink实时链路验证-实施方案-2026-08-05.md §4.2).
 *
 * <p>Flink exposes two independent metric-naming conventions depending on which OTel reporter a
 * given standalone cluster uses (see T12 findings in the same doc): the native OTLP push reporter
 * (FLIP-385) writes dotted names ({@code flink.taskmanager.job.task.operator.numRecordsOut}); the
 * Prometheus-scrape fallback (used where FLIP-385 isn't shipped, e.g. Flink 1.20) writes
 * underscored names via the OTel Prometheus receiver's usual conversion. A given {@code job_id}
 * only ever has data under one of the two, so querying both and summing the per-job results is
 * safe — no double counting, and no need to know in advance which reporter a job used.
 *
 * <p>The two conventions also land in different Doris tables. Flink's built-in Prometheus reporter
 * mislabels every {@code Counter} metric (including {@code numRecordsOut}) as {@code # TYPE ...
 * gauge} in its {@code /metrics} exposition — a known Flink limitation, not a Collector bug — so
 * the OTel Prometheus receiver honors that hint and the doris exporter routes it to {@code
 * otel_metrics_gauge}. The native OTLP push reporter uses correct Sum semantics and lands in
 * {@code otel_metrics_sum}. Each entry in {@link #FLINK_RECORDS_OUT}/{@link #FLINK_BYTES_OUT}
 * pairs a metric name with the table it actually lands in (verified against live Doris data during
 * T16 follow-up, 2026-08-07) — querying the wrong table silently returns zero rows, not an error.
 *
 * <p>Flink has no equivalent of Spark's batch task/stage lifecycle. For a running Flink job,
 * {@code activeTasks} is the live count of distinct {@code task_id} labels, while
 * {@code completeTasks}/{@code runningStages} remain unavailable.
 */
@Service
public class LineageJobMetricsService {

    static final int MAX_APP_IDS = 50;
    private static final long RANGE_SECONDS = 120;
    private static final long RANGE_STEP_SECONDS = 15;
    private static final long FLINK_RATE_STEP_SECONDS = 60;

    private static final String COMPLETE_TASKS = "spark_threadpool_completeTasks";
    private static final String ACTIVE_TASKS = "spark_threadpool_activeTasks";
    private static final String RECORDS_WRITTEN = "spark_executor_recordsWritten";
    private static final String BYTES_WRITTEN = "spark_executor_bytesWritten";
    private static final String RUNNING_STAGES = "spark_dagscheduler_stage_runningStages";

    private static final Pattern FLINK_JOB_ID = Pattern.compile("^[0-9a-f]{32}$");
    /** Paimon 的真实输出在 Committer；Doris 的行数在 Writer 输入端。 */
    private static final String FLINK_SINK_OPERATOR_REGEX = ".*(Writer|Committer).*";
    private static final String FLINK_WRITER_OPERATOR_REGEX = ".*Writer.*";

    private record FlinkMetric(String name, String table, String operatorRegex, boolean deltaSamples) {
    }

    private static final FlinkMetric[] FLINK_RECORDS_OUT = {
            // Flink 2.x native OTLP reports Doris Writer numRecordsIn as delta Sum samples. A Writer has
            // no numRecordsOut (and its paired Committer always has 0), so sum samples per bucket.
            new FlinkMetric("flink.taskmanager.job.task.operator.numRecordsIn", "sum",
                    FLINK_WRITER_OPERATOR_REGEX, true),
            new FlinkMetric("flink_taskmanager_job_task_operator_numRecordsOut", "gauge",
                    FLINK_SINK_OPERATOR_REGEX, false)
    };
    private static final FlinkMetric[] FLINK_BYTES_OUT = {
            new FlinkMetric("flink.taskmanager.job.task.operator.numBytesOut", "sum",
                    FLINK_SINK_OPERATOR_REGEX, false),
            new FlinkMetric("flink_taskmanager_job_task_operator.numBytesOut", "gauge",
                    FLINK_SINK_OPERATOR_REGEX, false)
    };

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

        List<String> flinkJobIds = appIds.stream().filter(id -> FLINK_JOB_ID.matcher(id).matches()).toList();
        List<String> sparkAppIds = appIds.stream().filter(id -> !FLINK_JOB_ID.matcher(id).matches()).toList();

        long sampledAt = clock.instant().getEpochSecond();
        Map<String, JobMetrics> metricsByApp = new LinkedHashMap<>();
        metricsByApp.putAll(getSparkJobMetrics(clusterId, sparkAppIds, sampledAt));
        metricsByApp.putAll(getFlinkJobMetrics(clusterId, flinkJobIds, sampledAt));
        return metricsByApp;
    }

    private Map<String, JobMetrics> getSparkJobMetrics(Integer clusterId, List<String> appIds, long sampledAt) {
        if (appIds.isEmpty()) {
            return Map.of();
        }
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

    private Map<String, JobMetrics> getFlinkJobMetrics(Integer clusterId, List<String> jobIds, long sampledAt) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        String jobIdRegex = exactRegex(jobIds);
        Map<String, Double> recordsWritten = new LinkedHashMap<>();
        Map<String, Double> bytesWritten = new LinkedHashMap<>();
        Map<String, Double> recordsWrittenRate = new LinkedHashMap<>();
        for (FlinkMetric metric : FLINK_RECORDS_OUT) {
            mergeSum(recordsWritten,
                    queryInstantByFlinkJobId(clusterId, metric, jobIdRegex, sampledAt));
            mergeSum(recordsWrittenRate,
                    queryRateByFlinkJobId(clusterId, metric, jobIdRegex, sampledAt));
        }
        for (FlinkMetric metric : FLINK_BYTES_OUT) {
            mergeSum(bytesWritten,
                    queryInstantByFlinkJobId(clusterId, metric, jobIdRegex, sampledAt));
        }
        Map<String, Long> activeTasks = queryFlinkTaskCounts(clusterId, jobIdRegex, sampledAt);

        Map<String, JobMetrics> metricsByJob = new LinkedHashMap<>();
        for (String jobId : jobIds) {
            if (!recordsWritten.containsKey(jobId) && !bytesWritten.containsKey(jobId)
                    && !recordsWrittenRate.containsKey(jobId)) {
                continue;
            }
            metricsByJob.put(jobId, new JobMetrics(
                    0L,
                    activeTasks.getOrDefault(jobId, 0L),
                    toLong(recordsWritten.get(jobId)),
                    toLong(bytesWritten.get(jobId)),
                    recordsWrittenRate.get(jobId),
                    0L,
                    Instant.ofEpochSecond(sampledAt)));
        }
        return metricsByJob;
    }

    /** Counts running Flink task vertices from the reporter's stable {@code task_id} label. */
    private Map<String, Long> queryFlinkTaskCounts(Integer clusterId, String jobIdRegex, long sampledAt) {
        Map<String, LinkedHashSet<String>> taskIdsByJob = new LinkedHashMap<>();
        for (FlinkMetric metric : FLINK_RECORDS_OUT) {
            PrometheusVectorResult result = queryService.queryInstant(
                    clusterId, metric.name(), "count", 1.0, ".+", ".+", Map.of(), Map.of(),
                    Map.of("job_id", jobIdRegex), Map.of(), sampledAt, metric.table(),
                    List.of("job_id", "task_id"));
            for (PrometheusVectorResult.VectorSample sample : result.result()) {
                String jobId = sample.metric().get("job_id");
                String taskId = sample.metric().get("task_id");
                if (jobId != null && taskId != null) {
                    taskIdsByJob.computeIfAbsent(jobId, ignored -> new LinkedHashSet<>()).add(taskId);
                }
            }
        }
        Map<String, Long> taskCountsByJob = new LinkedHashMap<>();
        taskIdsByJob.forEach((jobId, taskIds) -> taskCountsByJob.put(jobId, (long) taskIds.size()));
        return taskCountsByJob;
    }

    private static void mergeSum(Map<String, Double> target, Map<String, Double> source) {
        source.forEach((key, value) -> target.merge(key, value, Double::sum));
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

    private Map<String, Double> queryInstantByFlinkJobId(Integer clusterId, FlinkMetric metric,
                                                         String jobIdRegex, long sampledAt) {
        PrometheusVectorResult result = queryService.queryInstant(
                clusterId, metric.name(), "sum", 1.0, ".+", ".+", Map.of(), Map.of(),
                Map.of("job_id", jobIdRegex, "operator_name", metric.operatorRegex()), Map.of(),
                sampledAt, metric.table(), List.of("job_id"));
        Map<String, Double> valuesByJob = new LinkedHashMap<>();
        for (PrometheusVectorResult.VectorSample sample : result.result()) {
            String jobId = sample.metric().get("job_id");
            Double value = numericValue(sample.value());
            if (jobId != null && value != null) {
                valuesByJob.merge(jobId, value, Double::sum);
            }
        }
        return valuesByJob;
    }

    private Map<String, Double> queryRateByFlinkJobId(Integer clusterId, FlinkMetric metric,
                                                      String jobIdRegex, long sampledAt) {
        long endOfLastCompleteMinute = sampledAt - Math.floorMod(sampledAt, FLINK_RATE_STEP_SECONDS);
        PrometheusMatrixResult result = metric.deltaSamples()
                ? queryService.queryRangeSum(clusterId, metric.name(), null, 1.0 / FLINK_RATE_STEP_SECONDS,
                        ".+", ".+", Map.of(), Map.of(),
                        Map.of("job_id", jobIdRegex, "operator_name", metric.operatorRegex()), Map.of(),
                        List.of("job_id"), endOfLastCompleteMinute - RANGE_SECONDS,
                        endOfLastCompleteMinute, FLINK_RATE_STEP_SECONDS,
                        metric.table(), 0.5, null)
                : queryService.queryRange(clusterId, metric.name(), "1m", 1.0, ".+", ".+", Map.of(), Map.of(),
                        Map.of("job_id", jobIdRegex, "operator_name", metric.operatorRegex()), Map.of(),
                        List.of("job_id"), endOfLastCompleteMinute - RANGE_SECONDS,
                        endOfLastCompleteMinute, FLINK_RATE_STEP_SECONDS,
                        metric.table(), 0.5, null);
        Map<String, Double> valuesByJob = new LinkedHashMap<>();
        for (PrometheusMatrixResult.MatrixSeries series : result.result()) {
            String jobId = series.metric().get("job_id");
            List<Object[]> values = series.values();
            if (jobId == null || values.isEmpty()) {
                continue;
            }
            Double value = numericValue(values.get(values.size() - 1));
            if (value != null) {
                valuesByJob.merge(jobId, value, Double::sum);
            }
        }
        return valuesByJob;
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
