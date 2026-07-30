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

package com.datasophon.api.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasophon.api.lineage.LineageGraphQuery.Direction;
import com.datasophon.api.lineage.LineageGraphQuery.GraphData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

class LineageGraphQueryTest {

    private final LineageGraphQuery query = new LineageGraphQuery();

    @Test
    void queryIsDeterministicAcrossRepeatedRunsAndInsertionOrders() {
        LineageGraphSnapshot forward = complexSnapshot(false);
        LineageGraphSnapshot reverse = complexSnapshot(true);

        GraphData expected = query.query(forward, 1, 5, Direction.BOTH);
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(query.query(forward, 1, 5, Direction.BOTH)).isEqualTo(expected);
            assertThat(query.query(reverse, 1, 5, Direction.BOTH)).isEqualTo(expected);
        }

        assertThat(expected.nodes()).extracting(NodeMeta::id).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(expected.edges()).hasSize(7);
        assertThat(expected.truncated()).isFalse();
    }

    @Test
    void remainingZeroCollapsesEveryNonEmptyFrontierBranch() {
        SnapshotBuilder builder = new SnapshotBuilder();
        for (long nodeId = 1; nodeId <= 300; nodeId++) {
            builder.node(nodeId, "DWD");
        }
        for (long nodeId = 2; nodeId <= 300; nodeId++) {
            builder.edge(1, nodeId);
        }
        builder.node(301, "ADS").edge(2, 301);

        GraphData result = query.query(builder.build(7), 1, 2, Direction.DOWNSTREAM);

        assertThat(result.nodes()).hasSize(300);
        assertThat(result.collapsed())
                .anySatisfy(collapsed -> {
                    assertThat(collapsed.nodeId()).isEqualTo(2);
                    assertThat(collapsed.hiddenCount()).isEqualTo(1);
                });
        assertThat(result.nodes()).extracting(NodeMeta::id).doesNotContain(301L);
    }

    @Test
    void remainingOneIsAssignedByStableFrontierOrderAndOtherBranchCollapses() {
        SnapshotBuilder builder = new SnapshotBuilder();
        for (long nodeId = 1; nodeId <= 299; nodeId++) {
            builder.node(nodeId, "DWD");
        }
        for (long nodeId = 2; nodeId <= 299; nodeId++) {
            builder.edge(1, nodeId);
        }
        builder.node(300, "ADS").node(301, "ADS");
        builder.edge(2, 300).edge(3, 301);

        GraphData result = query.query(builder.build(8), 1, 2, Direction.DOWNSTREAM);

        assertThat(result.nodes()).extracting(NodeMeta::id).contains(300L).doesNotContain(301L);
        assertThat(result.collapsed())
                .singleElement()
                .satisfies(collapsed -> {
                    assertThat(collapsed.nodeId()).isEqualTo(3);
                    assertThat(collapsed.hiddenCount()).isEqualTo(1);
                });
    }

    @Test
    void frontierMinusOneBudgetProducesAnExplicitZeroBudgetCollapse() {
        SnapshotBuilder builder = new SnapshotBuilder().node(1, "ODS");
        LongStream.rangeClosed(2, 151).forEach(node -> {
            builder.node(node, "DWD").node(1000 + node, "ADS");
            builder.edge(1, node).edge(node, 1000 + node);
        });

        GraphData result = query.query(builder.build(9), 1, 2, Direction.DOWNSTREAM);

        assertThat(result.nodes()).hasSize(300);
        assertThat(result.nodes()).extracting(NodeMeta::id).contains(1150L).doesNotContain(1151L);
        assertThat(result.collapsed())
                .singleElement()
                .satisfies(collapsed -> assertThat(collapsed.nodeId()).isEqualTo(151));
    }

    @Test
    void nodeWhoseDegreeExceedsNonZeroShareIsFullyCollapsedAndRefundsBudget() {
        SnapshotBuilder builder = new SnapshotBuilder()
                .node(1, "ODS")
                .node(2, "DWD")
                .node(3, "DWD");
        builder.edge(1, 2).edge(1, 3);
        LongStream.rangeClosed(10, 159).forEach(node -> builder.node(node, "ADS").edge(2, node));
        LongStream.rangeClosed(200, 348).forEach(node -> builder.node(node, "ADS").edge(3, node));

        GraphData result = query.query(builder.build(10), 1, 2, Direction.DOWNSTREAM);

        assertThat(result.nodes()).hasSize(152);
        assertThat(result.nodes()).extracting(NodeMeta::id).contains(1L, 2L, 3L, 200L, 348L);
        assertThat(result.nodes()).extracting(NodeMeta::id).doesNotContain(10L, 159L);
        assertThat(result.collapsed())
                .singleElement()
                .satisfies(collapsed -> {
                    assertThat(collapsed.nodeId()).isEqualTo(2);
                    assertThat(collapsed.hiddenCount()).isEqualTo(150);
                    assertThat(collapsed.token()).isEqualTo("n:2:down:g10");
                });
    }

    @Test
    void expansionUsesTokenNodeOnlyAndRejectsMixedGenerations() {
        SnapshotBuilder builder = new SnapshotBuilder().node(1, "ODS").node(2, "DWD").node(3, "DWD");
        builder.edge(1, 2).edge(1, 3);
        LongStream.rangeClosed(10, 208).forEach(node -> builder.node(node, "ADS").edge(2, node));
        LongStream.rangeClosed(300, 498).forEach(node -> builder.node(node, "ADS").edge(3, node));
        LineageGraphSnapshot snapshot = builder.build(11);

        GraphData initial = query.query(snapshot, 1, 2, Direction.DOWNSTREAM);
        String token = initial.collapsed().getFirst().token();
        GraphData expanded = query.expand(snapshot, token);

        assertThat(expanded.nodes()).hasSize(200);
        assertThat(expanded.nodes()).extracting(NodeMeta::id).contains(2L, 10L, 208L).doesNotContain(1L);
        assertThat(expanded.collapsed()).isEmpty();
        assertThatThrownBy(() -> query.expand(builder.build(12), token))
                .isInstanceOf(LineageGraphQuery.StaleExpansionTokenException.class);
        assertThatThrownBy(() -> query.expand(snapshot, "broken"))
                .isInstanceOf(LineageGraphQuery.InvalidExpansionTokenException.class);
    }

    @Test
    void rootConsumesOneOfTheThreeHundredNodeSlots() {
        SnapshotBuilder builder = new SnapshotBuilder().node(1, "ODS");
        LongStream.rangeClosed(2, 301).forEach(node -> builder.node(node, "DWD").edge(1, node));

        GraphData result = query.query(builder.build(13), 1, 1, Direction.DOWNSTREAM);

        assertThat(result.nodes()).containsExactly(builder.meta(1));
        assertThat(result.collapsed())
                .singleElement()
                .satisfies(collapsed -> assertThat(collapsed.hiddenCount()).isEqualTo(300));
    }

    @Test
    void overviewIncludesCrossLayerReverseAndUnknownCombinations() {
        SnapshotBuilder builder = new SnapshotBuilder()
                .node(1, "ODS")
                .node(2, "ADS")
                .node(3, "DWS")
                .node(4, "DWD")
                .node(5, null);
        builder.edge(1, 2).edge(3, 4).edge(5, 1).edge(2, 5);

        LineageGraphQuery.OverviewData overview = query.overview(builder.build(14));

        assertThat(overview.layers())
                .anySatisfy(layer -> {
                    assertThat(layer.layer()).isEqualTo("UNKNOWN");
                    assertThat(layer.nodeCount()).isEqualTo(1);
                });
        assertThat(overview.edges())
                .extracting(edge -> edge.srcLayer() + "->" + edge.dstLayer())
                .containsExactly("ODS->ADS", "DWS->DWD", "ADS->UNKNOWN", "UNKNOWN->ODS");
    }

    @Test
    void dagSelfLoopAndNonTrivialCycleRemainQueryableAndAreClassifiedSeparately() {
        LineageGraphSnapshot dag = new SnapshotBuilder()
                .node(1, "ODS")
                .node(2, "DWD")
                .node(3, "ADS")
                .edge(1, 2)
                .edge(2, 3)
                .build(20);
        LineageGraphSnapshot selfLoop =
                new SnapshotBuilder().node(1, "DWD").edge(1, 1).build(21);
        LineageGraphSnapshot cycle = new SnapshotBuilder()
                .node(1, "DWD")
                .node(2, "DWS")
                .edge(1, 2)
                .edge(2, 1)
                .build(22);

        assertThat(dag.meta().selfLoopCount()).isZero();
        assertThat(dag.meta().hasNonTrivialCycle()).isFalse();
        assertThat(query.query(dag, 1, 5, Direction.DOWNSTREAM).nodes()).hasSize(3);
        assertThat(query.query(dag, 3, 5, Direction.UPSTREAM).nodes()).hasSize(3);
        assertThat(selfLoop.meta().selfLoopCount()).isEqualTo(1);
        assertThat(selfLoop.meta().hasNonTrivialCycle()).isFalse();
        assertThat(query.query(selfLoop, 1, 5, Direction.DOWNSTREAM).nodes()).hasSize(1);
        assertThat(cycle.meta().selfLoopCount()).isZero();
        assertThat(cycle.meta().hasNonTrivialCycle()).isTrue();
        assertThat(query.query(cycle, 1, 5, Direction.DOWNSTREAM).nodes()).hasSize(2);
    }

    private static LineageGraphSnapshot complexSnapshot(boolean reverseInsertion) {
        SnapshotBuilder builder = new SnapshotBuilder();
        List<Long> nodes = new ArrayList<>(List.of(1L, 2L, 3L, 4L, 5L));
        List<long[]> edges = new ArrayList<>(List.of(
                new long[]{1, 2},
                new long[]{1, 3},
                new long[]{2, 4},
                new long[]{3, 4},
                new long[]{4, 5},
                new long[]{5, 2},
                new long[]{3, 3}));
        if (reverseInsertion) {
            nodes = nodes.reversed();
            edges = edges.reversed();
        }
        nodes.forEach(node -> builder.node(node, node == 1 ? "ODS" : "DWD"));
        edges.forEach(edge -> builder.edge(edge[0], edge[1]));
        return builder.build(6);
    }

    private static final class SnapshotBuilder {

        private final MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        private final Map<Long, NodeMeta> nodes = new LinkedHashMap<>();

        SnapshotBuilder node(long id, String layer) {
            graph.addNode(id);
            nodes.put(id, meta(id, layer));
            return this;
        }

        SnapshotBuilder edge(long source, long target) {
            long edgeId = source * 10_000 + target;
            graph.putEdgeValue(
                    source,
                    target,
                    new EdgeValue(List.of(new JobRef(edgeId, edgeId, 1, edgeId % 2 == 0 ? "BATCH" : "STREAM"))));
            return this;
        }

        NodeMeta meta(long id) {
            return nodes.get(id);
        }

        LineageGraphSnapshot build(long generation) {
            return LineageGraphSnapshot.copyOf(
                    graph,
                    nodes,
                    generation,
                    Instant.parse("2026-07-30T00:00:00Z"));
        }

        private static NodeMeta meta(long id, String layer) {
            return new NodeMeta(
                    id,
                    "paimon",
                    "prod",
                    "db",
                    "table_" + id,
                    "paimon://prod/db/table_" + id,
                    layer);
        }
    }
}
