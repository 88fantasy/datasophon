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
        assertThat(sql).contains("ROW_NUMBER() OVER(PARTITION BY trace_id ORDER BY timestamp ASC) AS rn");
        assertThat(sql).contains("WHERE rn = 1");
        assertThat(sql).doesNotContain("QUALIFY");
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
        assertThat(sql).contains("MAX(duration) AS max_duration_ns");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("GROUP BY service_name");
    }

    @Test
    void serviceSummaryStatsSql_filtersByServiceNameWithoutGroupBy() {
        String sql = OtelTracesQueryService.buildServiceSummaryStatsSql();

        assertThat(sql).contains("otel.otel_traces");
        assertThat(sql).contains("service_name = :serviceName");
        assertThat(sql).contains("MAX(duration) AS max_duration_ns");
        assertThat(sql).contains("PERCENTILE_APPROX(duration, 0.99) AS p99_duration_ns");
        assertThat(sql).doesNotContain("GROUP BY");
    }

    @Test
    void serviceSummarySeriesSql_bucketsByMinuteForOneService() {
        String sql = OtelTracesQueryService.buildServiceSummarySeriesSql();

        assertThat(sql).contains("date_trunc(timestamp, 'MINUTE') AS bucket");
        assertThat(sql).contains("service_name = :serviceName");
        assertThat(sql).contains("GROUP BY bucket");
        assertThat(sql).contains("ORDER BY bucket");
    }

    @Test
    void toTopologyGraph_mapsRowsAndBackfillsMissingEdgeEndpoints() {
        List<Map<String, Object>> nodeRows = List.of(
                Map.of("service_name", "datasophon-api", "span_count", 100L, "error_count", 2L,
                        "avg_duration_ns", 1_500_000.0, "p99_duration_ns", 9_000_000.0,
                        "max_duration_ns", 20_000_000.0));
        List<Map<String, Object>> edgeRows = List.of(
                Map.of("caller_service_name", "datasophon-api", "callee_service_name", "datasophon-worker",
                        "call_count", 40L, "error_count", 1L));

        OtelTracesQueryService.TopologyGraph graph =
                OtelTracesQueryService.toTopologyGraph(nodeRows, edgeRows);

        assertThat(graph.edges()).containsExactly(
                new OtelTracesQueryService.TopologyEdge("datasophon-api", "datasophon-worker", 40L, 1L));
        assertThat(graph.nodes()).containsExactly(
                new OtelTracesQueryService.TopologyNode(
                        "datasophon-api", 100L, 2L, 1_500_000.0, 9_000_000.0, 20_000_000.0, false, ""),
                new OtelTracesQueryService.TopologyNode("datasophon-worker", 0L, 0L, 0D, 0D, 0D, false, ""));
    }

    @Test
    void toTopologyGraph_emptyRowsYieldEmptyGraph() {
        OtelTracesQueryService.TopologyGraph graph =
                OtelTracesQueryService.toTopologyGraph(List.of(), List.of());

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void externalDependencyEdgesSql_coversAnyClientSpanWithServerEndpoint() {
        String sql = OtelTracesQueryService.buildExternalDependencyEdgesSql();

        assertThat(sql).contains("otel.otel_traces");
        assertThat(sql).contains("span_kind = 'SPAN_KIND_CLIENT'");
        assertThat(sql).contains("span_attributes['db.system']");
        assertThat(sql).contains("span_attributes['http.request.method']");
        assertThat(sql).contains("span_attributes['server.address']");
        assertThat(sql).contains("span_attributes['server.port']");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("GROUP BY caller, db_system, server_addr, server_port");
        // 不再要求 db.system 非空——只要有 server.address 就纳入，不按类型限制
        assertThat(sql).doesNotContain("cast(span_attributes['db.system'] as string) IS NOT NULL");
    }

    @Test
    void mergeExternalDependencies_addsExternalNodeAndCallerEdge() {
        OtelTracesQueryService.TopologyGraph base = new OtelTracesQueryService.TopologyGraph(
                List.of(new OtelTracesQueryService.TopologyNode(
                        "datasophon-api", 100L, 2L, 1_500_000.0, 9_000_000.0, 20_000_000.0, false, "")),
                List.of());
        List<Map<String, Object>> externalRows = List.of(
                Map.of("caller", "datasophon-api", "db_system", "mysql",
                        "server_addr", "127.0.0.1", "server_port", "3306",
                        "call_count", 4807L, "error_count", 0L,
                        "avg_duration_ns", 800_000.0, "p99_duration_ns", 2_000_000.0, "max_duration_ns", 5_000_000.0));

        OtelTracesQueryService.TopologyGraph merged =
                OtelTracesQueryService.mergeExternalDependencies(base, externalRows);

        assertThat(merged.nodes()).contains(new OtelTracesQueryService.TopologyNode(
                "mysql@127.0.0.1:3306", 4807L, 0L, 800_000.0, 2_000_000.0, 5_000_000.0, true, "mysql"));
        assertThat(merged.edges()).containsExactly(
                new OtelTracesQueryService.TopologyEdge("datasophon-api", "mysql@127.0.0.1:3306", 4807L, 0L));
    }

    @Test
    void mergeExternalDependencies_emptyRowsReturnsSameGraph() {
        OtelTracesQueryService.TopologyGraph base = new OtelTracesQueryService.TopologyGraph(List.of(), List.of());

        OtelTracesQueryService.TopologyGraph merged =
                OtelTracesQueryService.mergeExternalDependencies(base, List.of());

        assertThat(merged).isSameAs(base);
    }

    @Test
    void mergeExternalDependencies_aggregatesSharedExternalNodeAcrossCallers() {
        OtelTracesQueryService.TopologyGraph base = new OtelTracesQueryService.TopologyGraph(
                List.of(
                        new OtelTracesQueryService.TopologyNode(
                                "datasophon-api", 100L, 0L, 1_000_000.0, 2_000_000.0, 3_000_000.0, false, ""),
                        new OtelTracesQueryService.TopologyNode(
                                "datasophon-worker", 80L, 0L, 1_500_000.0, 2_500_000.0, 4_000_000.0, false, "")),
                List.of());
        List<Map<String, Object>> externalRows = List.of(
                Map.of("caller", "datasophon-api", "db_system", "mysql",
                        "server_addr", "127.0.0.1", "server_port", "3306",
                        "call_count", 10L, "error_count", 1L,
                        "avg_duration_ns", 1_000_000.0, "p99_duration_ns", 4_000_000.0, "max_duration_ns", 6_000_000.0),
                Map.of("caller", "datasophon-worker", "db_system", "mysql",
                        "server_addr", "127.0.0.1", "server_port", "3306",
                        "call_count", 30L, "error_count", 2L,
                        "avg_duration_ns", 3_000_000.0, "p99_duration_ns", 8_000_000.0, "max_duration_ns", 9_000_000.0));

        OtelTracesQueryService.TopologyGraph merged =
                OtelTracesQueryService.mergeExternalDependencies(base, externalRows);

        assertThat(merged.nodes()).contains(new OtelTracesQueryService.TopologyNode(
                "mysql@127.0.0.1:3306", 40L, 3L, 2_500_000.0, 8_000_000.0, 9_000_000.0, true, "mysql"));
        assertThat(merged.edges()).contains(
                new OtelTracesQueryService.TopologyEdge("datasophon-api", "mysql@127.0.0.1:3306", 10L, 1L),
                new OtelTracesQueryService.TopologyEdge("datasophon-worker", "mysql@127.0.0.1:3306", 30L, 2L));
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
