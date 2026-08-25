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
import com.datasophon.api.dto.v2.DsBatchOutputVO;
import com.datasophon.api.dto.v2.DsDagNodeVO;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusVectorResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** Binds one DS task instance to its batch lineage or streaming OTel metrics. */
@Service
public class DsTaskMetricsService {

    private static final long STREAM_RATE_WINDOW_SECONDS = 60;
    private static final MetricSource[] STREAM_DISCOVERY_METRICS = {
            new MetricSource("flink.taskmanager.job.task.operator.numRecordsOut", "sum", true),
            new MetricSource("flink_taskmanager_job_task_operator_numRecordsOut", "gauge", false)
    };

    private final GravitinoLineageClient lineageClient;
    private final OtelMetricsQueryService queryService;
    private final DsStreamMetricAccumulator streamMetricAccumulator;
    private final Clock clock;

    @Autowired
    public DsTaskMetricsService(GravitinoLineageClient lineageClient,
                                OtelMetricsQueryService queryService,
                                DsStreamMetricAccumulator streamMetricAccumulator) {
        this(lineageClient, queryService, streamMetricAccumulator, Clock.systemUTC());
    }

    DsTaskMetricsService(GravitinoLineageClient lineageClient,
                         OtelMetricsQueryService queryService,
                         DsStreamMetricAccumulator streamMetricAccumulator,
                         Clock clock) {
        this.lineageClient = lineageClient;
        this.queryService = queryService;
        this.streamMetricAccumulator = streamMetricAccumulator;
        this.clock = clock;
    }

    public DsTaskMetricsVO metrics(Integer clusterId, DsDagNodeVO node) {
        if (node.getTaskInstanceId() == null) {
            throw new NotBoundException();
        }
        return "STREAM".equals(node.getFlowType())
                ? streamMetrics(clusterId, node.getTaskInstanceId())
                : batchMetrics(clusterId, node.getTaskInstanceId());
    }

    private DsTaskMetricsVO batchMetrics(Integer clusterId, int taskInstanceId) {
        JsonNode summary;
        try {
            summary = lineageClient.getRunByExternalKey(clusterId, externalKey(clusterId, taskInstanceId));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) {
                throw new NotBoundException();
            }
            throw e;
        }
        List<DsBatchOutputVO> outputs = new ArrayList<>();
        for (JsonNode item : summary.path("outputs")) {
            DsBatchOutputVO output = new DsBatchOutputVO();
            output.setNamespace(text(item, "namespace"));
            output.setName(text(item, "name"));
            output.setRowCount(nullableLong(item.path("rowCount")));
            output.setSize(nullableLong(item.path("size")));
            output.setJobName(text(item, "jobName"));
            outputs.add(output);
        }
        DsTaskMetricsVO metrics = new DsTaskMetricsVO();
        metrics.setKind("BATCH");
        metrics.setRunCount(nullableLong(summary.path("runCount")));
        metrics.setOutputs(outputs);
        return metrics;
    }

    private DsTaskMetricsVO streamMetrics(Integer clusterId, int taskInstanceId) {
        String prefix = externalKey(clusterId, taskInstanceId) + "-";
        String jobNameRegex = "^" + prefix + ".*$";
        List<StreamJob> candidates = new ArrayList<>();
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
        }
        StreamJob selected = candidates.stream()
                .max(Comparator.comparingLong(StreamJob::sampledAt).thenComparing(StreamJob::jobId))
                .orElseThrow(NotBoundException::new);
        Double rowsPerSecond = streamRate(clusterId, selected.jobId(), sampledAt);
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

    private static void applyAccumulated(DsTaskMetricsVO metrics, StreamMetricCursor cursor) {
        metrics.setProcessedApprox(cursor.processedApprox());
        metrics.setSince(cursor.since().toString());
    }

    private Double streamRate(Integer clusterId, String jobId, long sampledAt) {
        long end = sampledAt - Math.floorMod(sampledAt, STREAM_RATE_WINDOW_SECONDS);
        long start = end - 2 * STREAM_RATE_WINDOW_SECONDS;
        String jobIdRegex = "^(?:" + jobId + ")$";
        Double total = null;
        for (MetricSource metric : STREAM_DISCOVERY_METRICS) {
            PrometheusMatrixResult result = metric.deltaSamples()
                    ? queryService.queryRangeSum(clusterId, metric.name(), null,
                            1.0 / STREAM_RATE_WINDOW_SECONDS, ".+", ".+", Map.of(), Map.of(),
                            Map.of("job_id", jobIdRegex), Map.of(), List.of("job_id"),
                            start, end, STREAM_RATE_WINDOW_SECONDS, metric.table(), 0.5, null)
                    : queryService.queryRange(clusterId, metric.name(), "1m", 1.0,
                            ".+", ".+", Map.of(), Map.of(), Map.of("job_id", jobIdRegex), Map.of(),
                            List.of("job_id"), start, end, STREAM_RATE_WINDOW_SECONDS,
                            metric.table(), 0.5, null);
            for (PrometheusMatrixResult.MatrixSeries series : result.result()) {
                List<Object[]> values = series.values();
                if (jobId.equals(series.metric().get("job_id")) && !values.isEmpty()) {
                    Double value = numericValue(values.get(values.size() - 1));
                    if (value != null) {
                        total = total == null ? value : total + value;
                    }
                }
            }
        }
        return total;
    }

    static String externalKey(Integer clusterId, int taskInstanceId) {
        return "ds-" + clusterId + "-" + taskInstanceId;
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Long nullableLong(JsonNode value) {
        return value != null && value.isNumber() ? value.asLong() : null;
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

    static class NotBoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
