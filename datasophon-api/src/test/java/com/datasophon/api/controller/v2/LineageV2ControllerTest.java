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

package com.datasophon.api.controller.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.lineage.metrics.LineageJobMetricsService;
import com.datasophon.api.lineage.metrics.LineageJobMetricsService.JobMetrics;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient.NodeInjection;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class LineageV2ControllerTest {

    private final GravitinoLineageClient client = mock(GravitinoLineageClient.class);
    private final LineageJobMetricsService jobMetricsService = mock(LineageJobMetricsService.class);
    private final LineageV2Controller controller = new LineageV2Controller(client, jobMetricsService);
    private final ObjectNode response = new ObjectMapper().createObjectNode();

    @Test
    void forwardsAllSevenCompatibilityEndpoints() {
        when(client.get(anyLong(), anyString(), anyMap(),
                any(NodeInjection.class))).thenReturn(response);
        when(client.getJob(7L, 9L)).thenReturn(response);
        when(client.post(7L, "lineage/rebuild")).thenReturn(response);
        when(client.getRunByExternalKey(7L, "DSTI-99887766")).thenReturn(response);

        assertThat(controller.tables(7L, 1, 20, null, null, null, null)).isSameAs(response);
        assertThat(controller.graph(7L, 2L, 3, "both", "n:2:both:g8")).isSameAs(response);
        assertThat(controller.overview(7L)).isSameAs(response);
        assertThat(controller.table(7L, 2L)).isSameAs(response);
        assertThat(controller.job(7L, 9L)).isSameAs(response);
        assertThat(controller.impact(7L, 2L, 3)).isSameAs(response);
        assertThat(controller.rebuild(7L).getStatusCode().value()).isEqualTo(202);
        assertThat(controller.externalRun(7L, "DSTI-99887766")).isSameAs(response);

        verify(client).getJob(7L, 9L);
        verify(client).post(7L, "lineage/rebuild");
        verify(client).getRunByExternalKey(7L, "DSTI-99887766");
    }

    @Test
    void injectsClusterIdAtTheExplicitPathForEachEndpointShape() {
        when(client.get(anyLong(), anyString(), anyMap(),
                any(NodeInjection.class))).thenReturn(response);

        controller.tables(7L, 1, 20, null, null, null, null);
        verify(client).get(eq(7L), eq("lineage/tables"), anyMap(), eq(NodeInjection.TABLE_LIST));

        controller.graph(7L, 2L, 3, "both", "n:2:both:g8");
        verify(client).get(eq(7L), eq("lineage/graph"), anyMap(), eq(NodeInjection.GRAPH_NODES));

        controller.overview(7L);
        verify(client).get(eq(7L), eq("lineage/overview"), anyMap(), eq(NodeInjection.NONE));

        controller.table(7L, 2L);
        verify(client).get(eq(7L), eq("lineage/table/2"), anyMap(), eq(NodeInjection.SINGLE_TABLE));

        controller.impact(7L, 2L, 3);
        verify(client).get(eq(7L), eq("lineage/impact"), anyMap(), eq(NodeInjection.GRAPH_NODES));
    }

    @Test
    void forwardsGraphParametersWithoutRenaming() {
        when(client.get(eq(7L), eq("lineage/graph"), anyMap(),
                eq(NodeInjection.GRAPH_NODES))).thenReturn(response);
        controller.graph(7L, 2L, 3, "downstream", "n:2:down:g8");
        verify(client).get(eq(7L), eq("lineage/graph"),
                argThat(query ->
                        query.get("rootNodeId").equals(2L)
                                && query.get("depth").equals(3)
                                && query.get("direction").equals("downstream")
                                && query.get("expand").equals("n:2:down:g8")),
                eq(NodeInjection.GRAPH_NODES));
    }

    @Test
    void jobMetricsParsesAppIdsAndDoesNotCallGravitino() {
        JobMetrics metrics = new JobMetrics(12, 2, 60_000_000, 2_204_955_464L,
                51_234.5, 1, Instant.parse("2026-08-04T03:01:44Z"), "SPARK", null);
        when(jobMetricsService.getJobMetrics(7, java.util.List.of("app-1", "app-2")))
                .thenReturn(Map.of("app-1", metrics));

        assertThat(controller.jobMetrics(7, "app-1,app-2")).containsEntry("app-1", metrics);

        verify(jobMetricsService).getJobMetrics(7, java.util.List.of("app-1", "app-2"));
        verify(client, org.mockito.Mockito.never()).get(anyLong(), anyString(), anyMap(), any());
    }

    @Test
    void jobMetricsReturnsEmptyMapForEmptyAppIds() {
        when(jobMetricsService.getJobMetrics(7, java.util.List.of())).thenReturn(Map.of());

        assertThat(controller.jobMetrics(7, "")).isEmpty();
    }

    @Test
    void jobMetricsHidesDorisFailureDetails() {
        when(jobMetricsService.getJobMetrics(7, java.util.List.of("app-1")))
                .thenThrow(new IllegalStateException("SELECT secret FROM otel_metrics_sum"));

        assertThatThrownBy(() -> controller.jobMetrics(7, "app-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("任务指标查询失败")
                .hasMessageNotContaining("SELECT secret");
    }

    @Test
    void jobRateHistoryRejectsOversizedWindowsWith400() {
        // 参数越界要和"Doris 查询挂了"区分开：前者是调用方该改请求，后者才是服务端故障
        when(jobMetricsService.getJobRateHistory(7, "app-1", 0L, 999_999_999L, 1L))
                .thenThrow(new IllegalArgumentException("Rate history window is too large"));

        assertThatThrownBy(() -> controller.jobRateHistory(7, "app-1", 0L, 999_999_999L, 1L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(e -> ((org.springframework.web.server.ResponseStatusException) e).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void jobRateHistoryHidesDorisFailureDetails() {
        when(jobMetricsService.getJobRateHistory(7, "app-1", 1000L, 2000L, 60L))
                .thenThrow(new IllegalStateException("SELECT secret FROM otel_metrics_sum"));

        assertThatThrownBy(() -> controller.jobRateHistory(7, "app-1", 1000L, 2000L, 60L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("任务速率查询失败")
                .hasMessageNotContaining("SELECT secret");
    }
}
