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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OtelTracesQueryServiceTest {

    @Test
    void listSql_withoutOptionalFilters_containsTimeWindowAndPagination() {
        String sql = OtelTracesQueryService.buildListTracesSql(false, null, null, null, null);

        assertThat(sql).contains("otel.otel_traces");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("parent_span_id IS NULL OR parent_span_id = ''");
        assertThat(sql).contains("LIMIT :limit OFFSET :offset");
        assertThat(sql).doesNotContain(":serviceName");
        assertThat(sql).doesNotContain(":status");
        assertThat(sql).doesNotContain(":spanName");
        assertThat(sql).doesNotContain(":traceId");
    }

    @Test
    void listSql_withFilters_appendsNamedParamsOnly() {
        String sql = OtelTracesQueryService.buildListTracesSql(
                false, "datasophon-api", "ERROR", "cluster/list", "abc");

        assertThat(sql).contains("service_name = :serviceName");
        assertThat(sql).contains("span_name LIKE CONCAT('%', :spanName, '%')");
        assertThat(sql).contains("trace_id = :traceId");
        assertThat(sql).contains("status = :status");
        assertThat(sql).doesNotContain("datasophon-api");
        assertThat(sql).doesNotContain("cluster/list");
        assertThat(sql).doesNotContain("abc");
    }

    @Test
    void countSql_omitsPagination() {
        String sql = OtelTracesQueryService.buildListTracesSql(true, null, "OK", null, null);

        assertThat(sql).contains("SELECT COUNT(*) FROM traces");
        assertThat(sql).contains("status = :status");
        assertThat(sql).doesNotContain("LIMIT :limit");
        assertThat(sql).doesNotContain("OFFSET :offset");
    }

    @Test
    void traceDetailSql_filtersByTraceIdAndOrdersByTimestamp() {
        String sql = OtelTracesQueryService.buildTraceDetailSql();

        assertThat(sql).contains("span_attributes");
        assertThat(sql).contains("resource_attributes");
        assertThat(sql).contains("events");
        assertThat(sql).contains("WHERE trace_id = :traceId");
        assertThat(sql).contains("ORDER BY timestamp");
    }

    @Test
    void topologyEdgesSql_aggregatesGraphTableByCallerCallee() {
        String sql = OtelTracesQueryService.buildTopologyEdgesSql();

        assertThat(sql).contains("otel.otel_traces_graph");
        assertThat(sql).contains("SUM(`count`) AS call_count");
        assertThat(sql).contains("SUM(error_count) AS error_count");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("GROUP BY caller_service_name, callee_service_name");
    }

    @Test
    void topologyNodesSql_aggregatesTracesByService() {
        String sql = OtelTracesQueryService.buildTopologyNodesSql();

        assertThat(sql).contains("otel.otel_traces");
        assertThat(sql).contains("PERCENTILE_APPROX(duration, 0.99) AS p99_duration_ns");
        assertThat(sql).contains("AVG(duration) AS avg_duration_ns");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("GROUP BY service_name");
    }

    @Test
    void toTopologyGraph_mapsRowsAndBackfillsMissingEdgeEndpoints() {
        List<Map<String, Object>> nodeRows = List.of(
                Map.of("service_name", "datasophon-api", "span_count", 100L, "error_count", 2L,
                        "avg_duration_ns", 1_500_000.0, "p99_duration_ns", 9_000_000.0));
        List<Map<String, Object>> edgeRows = List.of(
                Map.of("caller_service_name", "datasophon-api", "callee_service_name", "datasophon-worker",
                        "call_count", 40L, "error_count", 1L));

        OtelTracesQueryService.TopologyGraph graph =
                OtelTracesQueryService.toTopologyGraph(nodeRows, edgeRows);

        assertThat(graph.edges()).containsExactly(
                new OtelTracesQueryService.TopologyEdge("datasophon-api", "datasophon-worker", 40L, 1L));
        assertThat(graph.nodes()).containsExactly(
                new OtelTracesQueryService.TopologyNode("datasophon-api", 100L, 2L, 1_500_000.0, 9_000_000.0),
                new OtelTracesQueryService.TopologyNode("datasophon-worker", 0L, 0L, 0D, 0D));
    }

    @Test
    void toTopologyGraph_emptyRowsYieldEmptyGraph() {
        OtelTracesQueryService.TopologyGraph graph =
                OtelTracesQueryService.toTopologyGraph(List.of(), List.of());

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void validateStatus_rejectsNonWhitelistStatus() {
        assertThatThrownBy(() -> OtelTracesQueryService.validateStatus("OK' OR 1=1 --"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toMap_parsesJsonObjectAndFallsBackToRawValue() {
        assertThat(OtelTracesQueryService.toMap("{\"http.method\":\"GET\"}"))
                .containsEntry("http.method", "GET");
        assertThat(OtelTracesQueryService.toMap("not-json"))
                .containsEntry("_raw", "not-json");
        assertThat(OtelTracesQueryService.toMap(Map.of("key", "value")))
                .containsEntry("key", "value");
    }
}
