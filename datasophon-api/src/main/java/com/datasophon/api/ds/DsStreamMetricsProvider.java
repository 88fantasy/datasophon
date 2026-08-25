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

package com.datasophon.api.ds;

import com.datasophon.api.ds.DsStreamMetricRepository.StreamMetricCursor;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusVectorResult;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Discovers one Flink job and reads its rate and resumable observed total. */
@Component
public class DsStreamMetricsProvider {

    private static final long STREAM_RATE_WINDOW_SECONDS = 60;
    private static final MetricSource[] STREAM_DISCOVERY_METRICS = {
            new MetricSource("flink.taskmanager.job.task.operator.numRecordsOut", "sum", true),
            new MetricSource("flink_taskmanager_job_task_operator_numRecordsOut", "gauge", false)
    };

    private final OtelMetricsQueryService queryService;
    private final DsStreamMetricAccumulator streamMetricAccumulator;
    private final Clock clock;

    @Autowired
    public DsStreamMetricsProvider(OtelMetricsQueryService queryService,
                                   DsStreamMetricAccumulator streamMetricAccumulator) {
        this(queryService, streamMetricAccumulator, Clock.systemUTC());
    }

    DsStreamMetricsProvider(OtelMetricsQueryService queryService,
                            DsStreamMetricAccumulator streamMetricAccumulator,
                            Clock clock) {
        this.queryService = queryService;
        this.streamMetricAccumulator = streamMetricAccumulator;
        this.clock = clock;
    }

    public DsTaskMetricsVO metrics(Integer clusterId, int taskInstanceId) {
        String prefix = DsBatchMetricsProvider.externalKey(clusterId, taskInstanceId) + "-";
        String jobNameRegex = "^" + prefix + ".*$";
        List<StreamJob> candidates = new ArrayList<>();
        MetricSource selectedMetric = null;
        long sampledAt = clock.instant().getEpochSecond();
        for (MetricSource metric : STREAM_DISCOVERY_METRICS) {
            PrometheusVectorResult result = queryService.queryInstant(
                    clusterId, metric.name(), "max", 1.0, ".+", ".+", Map.of(), Map.of(),
                    Map.of("job_name", jobNameRegex), Map.of(), sampledAt, metric.table(),
                    List.of("job_id", "job_name"));
            for (PrometheusVectorResult.VectorSample sample : result.result()) {
                String jobId = sample.metric().get("job_id");
                String jobName = sample.metric().get("job_name");
                if (jobId != null && jobName != null && jobName.startsWith(prefix)) {
                    candidates.add(new StreamJob(jobId, jobName, timestamp(sample.value())));
                }
            }
            if (!candidates.isEmpty()) {
                selectedMetric = metric;
                break;
            }
        }
        StreamJob selected = candidates.stream()
                .max(Comparator.comparingLong(StreamJob::sampledAt).thenComparing(StreamJob::jobId))
                .orElseThrow(DsTaskMetricsService.NotBoundException::new);
        Double rowsPerSecond = streamRate(clusterId, selected.jobId(), sampledAt, selectedMetric);
        if (rowsPerSecond == null) {
            throw new IllegalStateException("Flink job rate is unavailable");
        }
        DsTaskMetricsVO metrics = new DsTaskMetricsVO();
        metrics.setKind("STREAM");
        metrics.setJobId(selected.jobId());
        metrics.setJobName(selected.jobName());
        metrics.setRowsPerSecond(rowsPerSecond);
        metrics.setApproximate(true);
        streamMetricAccumulator.registerAndRead(clusterId, selected.jobId(), selected.jobName())
                .ifPresent(cursor -> applyAccumulated(metrics, cursor));
        return metrics;
    }

    private void applyAccumulated(DsTaskMetricsVO metrics, StreamMetricCursor cursor) {
        metrics.setSince(cursor.since().toString());
        Instant completedThrough = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        if (!cursor.cursor().isBefore(completedThrough)) {
            metrics.setProcessedApprox(cursor.processedApprox());
        }
    }

    private Double streamRate(Integer clusterId, String jobId, long sampledAt, MetricSource metric) {
        long end = sampledAt - Math.floorMod(sampledAt, STREAM_RATE_WINDOW_SECONDS);
        long start = end - 2 * STREAM_RATE_WINDOW_SECONDS;
        long inclusiveEnd = end - 1;
        String jobIdRegex = "^(?:" + jobId + ")$";
        PrometheusMatrixResult result = metric.deltaSamples()
                ? queryService.queryRangeSum(clusterId, metric.name(), null,
                        1.0 / STREAM_RATE_WINDOW_SECONDS, ".+", ".+", Map.of(), Map.of(),
                        Map.of("job_id", jobIdRegex), Map.of(), List.of("job_id"),
                        start, inclusiveEnd, STREAM_RATE_WINDOW_SECONDS, metric.table(), 0.5, null)
                : queryService.queryRange(clusterId, metric.name(), "1m", 1.0,
                        ".+", ".+", Map.of(), Map.of(), Map.of("job_id", jobIdRegex), Map.of(),
                        List.of("job_id"), start, inclusiveEnd, STREAM_RATE_WINDOW_SECONDS,
                        metric.table(), 0.5, null);
        for (PrometheusMatrixResult.MatrixSeries series : result.result()) {
            List<Object[]> values = series.values();
            if (jobId.equals(series.metric().get("job_id")) && !values.isEmpty()) {
                Double value = numericValue(values.get(values.size() - 1));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static long timestamp(Object[] value) {
        return value != null && value.length > 0 && value[0] instanceof Number number
                ? number.longValue()
                : 0;
    }

    private static Double numericValue(Object[] value) {
        if (value == null || value.length < 2 || value[1] == null) {
            return null;
        }
        try {
            return Double.valueOf(value[1].toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record MetricSource(String name, String table, boolean deltaSamples) {
    }

    private record StreamJob(String jobId, String jobName, long sampledAt) {
    }
}
