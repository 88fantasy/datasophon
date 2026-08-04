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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.lineage.metrics.LineageJobMetricsService.JobMetrics;
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
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class LineageJobMetricsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T03:01:44Z");

    private final OtelMetricsQueryService queryService = mock(OtelMetricsQueryService.class);
    private final LineageJobMetricsService service =
            new LineageJobMetricsService(queryService, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void returnEmptyResultsByDefault() {
        when(queryService.queryInstant(anyInt(), anyString(), anyString(), anyDouble(),
                anyString(), anyString(), anyMap(), anyMap(), anyMap(), anyMap(),
                anyLong(), anyString(), anyList()))
                .thenReturn(PrometheusVectorResult.of(List.of()));
        when(queryService.queryRange(anyInt(), anyString(), anyString(), anyDouble(),
                anyString(), anyString(), anyMap(), anyMap(), anyMap(), anyMap(), anyList(),
                anyLong(), anyLong(), anyLong(), anyString(), anyDouble(), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of()));
    }

    @Test
    void returnsContractFieldsAndSumsLatestPerInstanceRates() {
        stubInstant("spark_threadpool_completeTasks", "gauge", 12);
        stubInstant("spark_threadpool_activeTasks", "gauge", 2);
        stubInstant("spark_executor_recordsWritten", "sum", 60_000_000);
        stubInstant("spark_executor_bytesWritten", "sum", 2_204_955_464L);
        stubInstant("spark_dagscheduler_stage_runningStages", "gauge", 1);
        when(queryService.queryRange(eq(7), eq("spark_executor_recordsWritten"), eq("1m"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()),
                eq(Map.of("app_id", "^(?:app-1)$")), eq(Map.of()), eq(List.of("app_id")),
                eq(NOW.getEpochSecond() - 120), eq(NOW.getEpochSecond()), eq(15L), eq("sum"), eq(0.5), isNull()))
                .thenReturn(PrometheusMatrixResult.of(List.of(
                        matrixSeries("app-1", "collector-1", 3.0, 4.5),
                        matrixSeries("app-1", "collector-2", 1.0, 1.5))));

        Map<String, JobMetrics> result = service.getJobMetrics(7, List.of("app-1"));

        assertThat(result).containsOnlyKeys("app-1");
        assertThat(result.get("app-1"))
                .isEqualTo(new JobMetrics(12, 2, 60_000_000, 2_204_955_464L,
                        6.0, 1, NOW));
        verify(queryService).queryRange(eq(7), eq("spark_executor_recordsWritten"), eq("1m"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()),
                eq(Map.of("app_id", "^(?:app-1)$")), eq(Map.of()), eq(List.of("app_id")),
                eq(NOW.getEpochSecond() - 120), eq(NOW.getEpochSecond()), eq(15L), eq("sum"), eq(0.5), isNull());
    }

    @Test
    void returnsEmptyMapWhenAppIdsAreEmptyOrHaveNoData() {
        assertThat(service.getJobMetrics(7, List.of())).isEmpty();
        assertThat(service.getJobMetrics(7, List.of("app-without-data"))).isEmpty();
    }

    @Test
    void normalizesDistinctAppIdsAndCapsAtFifty() {
        List<String> requested = IntStream.rangeClosed(1, 55)
                .mapToObj(i -> " app-" + i + " ")
                .toList();

        List<String> normalized = LineageJobMetricsService.normalizeAppIds(requested);

        assertThat(normalized).hasSize(LineageJobMetricsService.MAX_APP_IDS);
        assertThat(normalized.getFirst()).isEqualTo("app-1");
        assertThat(normalized.getLast()).isEqualTo("app-50");
    }

    @Test
    void queriesEachMetricOnceForCappedAppIds() {
        // 名字曾叫 queriesFiftyAppsInSixBatches，但服务里没有任何分批逻辑——55 个 appId 会被
        // normalizeAppIds 截断到 50 个后拼进同一个 regex，一次查完。这里验证的是「5 个 instant
        // 指标各查一次 + 1 次 range」，times(5) 里的 5 = 指标个数，不是批次数。
        List<String> requested = IntStream.rangeClosed(1, 55)
                .mapToObj(i -> "app-" + i)
                .toList();

        assertThat(service.getJobMetrics(7, requested)).isEmpty();

        verify(queryService, times(5)).queryInstant(eq(7), anyString(), eq("sum"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()),
                eq(NOW.getEpochSecond()), anyString(), eq(List.of("app_id")));
        verify(queryService).queryRange(eq(7), eq("spark_executor_recordsWritten"), eq("1m"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()), anyMap(), eq(Map.of()), eq(List.of("app_id")),
                eq(NOW.getEpochSecond() - 120), eq(NOW.getEpochSecond()), eq(15L), eq("sum"), eq(0.5), isNull());
    }

    @Test
    void springContextSelectsTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OtelMetricsQueryService.class, () -> queryService);
            context.register(LineageJobMetricsService.class);

            context.refresh();

            assertThat(context.getBean(LineageJobMetricsService.class)).isNotNull();
        }
    }

    private void stubInstant(String metric, String table, Number value) {
        when(queryService.queryInstant(eq(7), eq(metric), eq("sum"), eq(1.0),
                eq(".+"), eq(".+"), eq(Map.of()), eq(Map.of()),
                eq(Map.of("app_id", "^(?:app-1)$")), eq(Map.of()),
                eq(NOW.getEpochSecond()), eq(table), eq(List.of("app_id"))))
                .thenReturn(vector("app-1", value));
    }

    private static PrometheusVectorResult vector(String appId, Number value) {
        return PrometheusVectorResult.of(List.of(
                new VectorSample(Map.of("app_id", appId), new Object[]{NOW.getEpochSecond(), value.toString()})));
    }

    private static MatrixSeries matrixSeries(String appId, String instance, double first, double last) {
        return new MatrixSeries(Map.of("app_id", appId, "instance", instance), List.of(
                new Object[]{NOW.getEpochSecond() - 15, Double.toString(first)},
                new Object[]{NOW.getEpochSecond(), Double.toString(last)}));
    }
}
