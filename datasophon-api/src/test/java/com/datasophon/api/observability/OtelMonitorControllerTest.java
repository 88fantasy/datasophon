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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.controller.observability.OtelMonitorController;
import com.datasophon.common.utils.Result;

import java.util.List;

import org.junit.jupiter.api.Test;

class OtelMonitorControllerTest {

    @Test
    void returnsCollectedNodeMetricsInStandardResult() {
        OtelMonitorService service = mock(OtelMonitorService.class);
        List<NodeOtelMetrics> metrics = List.of(
                new NodeOtelMetrics("worker-1", true, null, new OtelSelfMetrics(1, 10, 20, 0, 0, 0)));
        when(service.collectAll(7)).thenReturn(metrics);

        OtelMonitorController controller = new OtelMonitorController(
                service, mock(OtelTracesQueryService.class), mock(OtelLogsQueryService.class));
        Result result = controller.monitor(7);

        assertThat(controller).isInstanceOf(ApiController.class);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(metrics);
        verify(service).collectAll(7);
    }

    @Test
    void traceTopologyDelegatesToQueryService() {
        OtelTracesQueryService tracesQueryService = mock(OtelTracesQueryService.class);
        OtelTracesQueryService.TopologyGraph graph = new OtelTracesQueryService.TopologyGraph(
                List.of(new OtelTracesQueryService.TopologyNode(
                        "datasophon-api", 10L, 0L, 1_000_000.0, 2_000_000.0, 3_000_000.0)),
                List.of());
        when(tracesQueryService.getTopology(7, 100L, 200L)).thenReturn(graph);

        OtelMonitorController controller = new OtelMonitorController(
                mock(OtelMonitorService.class), tracesQueryService, mock(OtelLogsQueryService.class));
        Result result = controller.traceTopology(7, 100L, 200L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(graph);
        verify(tracesQueryService).getTopology(7, 100L, 200L);
    }

    @Test
    void traceServiceSummaryDelegatesToQueryService() {
        OtelTracesQueryService tracesQueryService = mock(OtelTracesQueryService.class);
        OtelTracesQueryService.ServiceSummary summary = new OtelTracesQueryService.ServiceSummary(
                new OtelTracesQueryService.ServiceSummaryStats(10L, 1L, 1_000_000.0, 2_000_000.0, 3_000_000.0),
                new OtelTracesQueryService.ServiceSummaryStats(8L, 0L, 900_000.0, 1_800_000.0, 2_500_000.0),
                List.of());
        when(tracesQueryService.getServiceSummary(7, 100L, 200L, "datasophon-api")).thenReturn(summary);

        OtelMonitorController controller = new OtelMonitorController(
                mock(OtelMonitorService.class), tracesQueryService, mock(OtelLogsQueryService.class));
        Result result = controller.traceServiceSummary(7, 100L, 200L, "datasophon-api");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo(summary);
        verify(tracesQueryService).getServiceSummary(7, 100L, 200L, "datasophon-api");
    }
}
