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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.ds.DsStreamMetricRepository.StreamMetricCursor;
import com.datasophon.api.dto.v2.DsDagNodeVO;
import com.datasophon.api.dto.v2.DsTaskMetricsVO;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusMatrixResult.MatrixSeries;
import com.datasophon.api.observability.PrometheusVectorResult;
import com.datasophon.api.observability.PrometheusVectorResult.VectorSample;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class DsTaskMetricsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T06:00:00Z");
    private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

    private final GravitinoLineageClient lineageClient = mock(GravitinoLineageClient.class);
    private final OtelMetricsQueryService queryService = mock(OtelMetricsQueryService.class);
    private final DsStreamMetricAccumulator streamMetricAccumulator = mock(DsStreamMetricAccumulator.class);
    private final DsTaskMetricsService service = new DsTaskMetricsService(
            new DsBatchMetricsProvider(lineageClient),
            new DsStreamMetricsProvider(
                    queryService, streamMetricAccumulator, Clock.fixed(NOW, ZoneOffset.UTC)));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(queryService.queryInstant(anyInt(), anyString(), anyString(), anyDouble(), anyString(), anyString(),
                anyMap(), anyMap(), anyMap(), anyMap(), anyLong(), anyString(), anyList()))
                .thenReturn(PrometheusVectorResult.of(List.of()));
        when(queryService.queryRangeSum(anyInt(), anyString(), isNull(), anyDouble(), anyString(), anyString(),
                anyMap(), anyMap(), anyMap(), anyMap(), anyList(), anyLong(), anyLong(), anyLong(),
                anyString(), anyDouble(), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of()));
        when(queryService.queryRange(anyInt(), anyString(), anyString(), anyDouble(), anyString(), anyString(),
                anyMap(), anyMap(), anyMap(), anyMap(), anyList(), anyLong(), anyLong(), anyLong(),
                anyString(), anyDouble(), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of()));
        when(streamMetricAccumulator.registerAndRead(anyInt(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void mapsAllBatchOutputsWithoutSummingThem() throws Exception {
        when(lineageClient.getRunByExternalKey(7, "ds-7-11")).thenReturn(objectMapper.readTree("""
                {"externalRunKey":"ds-7-11","runCount":7,"outputs":[
                {"namespace":"file","name":"synthetic/source","rowCount":700,"size":7096,"jobName":"write-a"},
                {"namespace":"file","name":"synthetic/target","rowCount":234,"size":3450,"jobName":"write-b"}]}
                """));

        DsTaskMetricsVO metrics = service.metrics(7, node(11, "BATCH"));

        assertThat(metrics.getKind()).isEqualTo("BATCH");
        assertThat(metrics.getRunCount()).isEqualTo(7);
        assertThat(metrics.getOutputs()).extracting("name", "rowCount", "size")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("synthetic/source", 700L, 7096L),
                        org.assertj.core.groups.Tuple.tuple("synthetic/target", 234L, 3450L));
    }

    @Test
    void discoversStreamJobByTaskPrefixAndCalculatesGenericOutputRate() {
        String jobName = "ds-7-12-synthetic-stream";
        PrometheusVectorResult discovery = PrometheusVectorResult.of(List.of(
                new VectorSample(Map.of("job_id", JOB_ID, "job_name", jobName),
                        new Object[]{NOW.getEpochSecond(), "1"})));
        when(queryService.queryInstant(eq(7), eq("flink.taskmanager.job.task.operator.numRecordsOut"),
                eq("max"), eq(1.0), eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()),
                eq(Map.of("job_name", "^ds-7-12-.*$")), eq(Map.of()), eq(NOW.getEpochSecond()),
                eq("sum"), eq(List.of("job_id", "job_name"))))
                .thenReturn(discovery);
        when(queryService.queryRangeSum(eq(7),
                eq("flink.taskmanager.job.task.operator.numRecordsOut"), isNull(), eq(1.0 / 60),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()),
                eq(Map.of("job_id", "^(?:" + JOB_ID + ")$")), eq(Map.of()), eq(List.of("job_id")),
                eq(NOW.getEpochSecond() - 120), eq(NOW.getEpochSecond() - 1), eq(60L),
                eq("sum"), eq(0.5), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of(
                        new MatrixSeries(Map.of("job_id", JOB_ID),
                                List.<Object[]>of(new Object[]{NOW.getEpochSecond() - 60, "22.8"})))));
        when(streamMetricAccumulator.registerAndRead(7, JOB_ID, jobName))
                .thenReturn(Optional.of(new StreamMetricCursor(
                        7, JOB_ID, jobName, NOW.minusSeconds(600), NOW, 1234)));

        DsTaskMetricsVO metrics = service.metrics(7, node(12, "STREAM"));

        assertThat(metrics.getKind()).isEqualTo("STREAM");
        assertThat(metrics.getJobId()).isEqualTo(JOB_ID);
        assertThat(metrics.getJobName()).isEqualTo(jobName);
        assertThat(metrics.getRowsPerSecond()).isEqualTo(22.8);
        assertThat(metrics.getApproximate()).isTrue();
        assertThat(metrics.getProcessedApprox()).isEqualTo(1234);
        assertThat(metrics.getSince()).isEqualTo("2026-08-25T05:50:00Z");
        verify(queryService).queryRangeSum(eq(7),
                eq("flink.taskmanager.job.task.operator.numRecordsOut"), isNull(), eq(1.0 / 60),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()),
                eq(List.of("job_id")), anyLong(), anyLong(), eq(60L), eq("sum"), eq(0.5), isNull());
    }

    @Test
    void prefersOtlpDeltaReporterWhenBothReporterPathsExist() {
        String jobName = "ds-7-12-synthetic-stream";
        PrometheusVectorResult discovery = PrometheusVectorResult.of(List.of(
                new VectorSample(Map.of("job_id", JOB_ID, "job_name", jobName),
                        new Object[]{NOW.getEpochSecond(), "1"})));
        when(queryService.queryInstant(eq(7), anyString(), eq("max"), eq(1.0), eq(".+"), eq(".+"),
                eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()), eq(NOW.getEpochSecond()),
                anyString(), eq(List.of("job_id", "job_name"))))
                .thenReturn(discovery);
        when(queryService.queryRangeSum(eq(7),
                eq("flink.taskmanager.job.task.operator.numRecordsOut"), isNull(), eq(1.0 / 60),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()),
                eq(List.of("job_id")), anyLong(), anyLong(), eq(60L), eq("sum"), eq(0.5), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of(
                        new MatrixSeries(Map.of("job_id", JOB_ID),
                                List.<Object[]>of(new Object[]{NOW.getEpochSecond() - 60, "10"})))));
        when(queryService.queryRange(eq(7),
                eq("flink_taskmanager_job_task_operator_numRecordsOut"), eq("1m"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()),
                eq(List.of("job_id")), anyLong(), anyLong(), eq(60L), eq("gauge"), eq(0.5), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of(
                        new MatrixSeries(Map.of("job_id", JOB_ID),
                                List.<Object[]>of(new Object[]{NOW.getEpochSecond() - 60, "20"})))));

        DsTaskMetricsVO metrics = service.metrics(7, node(12, "STREAM"));

        assertThat(metrics.getRowsPerSecond()).isEqualTo(10);
    }

    @Test
    void hidesLifetimeTotalWhileHistoricalPeriodsAreStillCatchingUp() {
        String jobName = "ds-7-12-synthetic-stream";
        when(queryService.queryInstant(eq(7), eq("flink.taskmanager.job.task.operator.numRecordsOut"),
                eq("max"), eq(1.0), eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()),
                eq(NOW.getEpochSecond()), eq("sum"), eq(List.of("job_id", "job_name"))))
                .thenReturn(PrometheusVectorResult.of(List.of(
                        new VectorSample(Map.of("job_id", JOB_ID, "job_name", jobName),
                                new Object[]{NOW.getEpochSecond(), "1"}))));
        when(queryService.queryRangeSum(eq(7), anyString(), isNull(), anyDouble(), eq(".+"), eq(".+"),
                eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()), eq(List.of("job_id")),
                anyLong(), anyLong(), eq(60L), eq("sum"), eq(0.5), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of(
                        new MatrixSeries(Map.of("job_id", JOB_ID),
                                List.<Object[]>of(new Object[]{NOW.getEpochSecond() - 60, "10"})))));
        when(streamMetricAccumulator.registerAndRead(7, JOB_ID, jobName))
                .thenReturn(Optional.of(new StreamMetricCursor(
                        7, JOB_ID, jobName, NOW.minusSeconds(86_400), NOW.minusSeconds(60), 1234)));

        DsTaskMetricsVO metrics = service.metrics(7, node(12, "STREAM"));

        assertThat(metrics.getProcessedApprox()).isNull();
        assertThat(metrics.getSince()).isEqualTo("2026-08-24T06:00:00Z");
    }

    private static DsDagNodeVO node(int taskInstanceId, String flowType) {
        DsDagNodeVO node = new DsDagNodeVO();
        node.setTaskInstanceId(taskInstanceId);
        node.setFlowType(flowType);
        return node;
    }
}
