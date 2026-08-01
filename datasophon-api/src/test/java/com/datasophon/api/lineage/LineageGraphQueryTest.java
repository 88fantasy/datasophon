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
import com.datasophon.api.lineage.LineageGraphQuery.TablePage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    /**
     * L3/B3 回归验收：修复缺陷 1（{@code CanonicalNameResolver} 曾恒传 {@code null} dwLayer）
     * 之前，每个节点的 {@code layerDistance()} 都是 {@code Integer.MAX_VALUE}，分层优先级形同
     * 虚设，frontier 顺序纯粹退化成度数/id 排序。这里刻意让两个候选节点的度数（149）与竞争关系
     * 完全相同，唯一差异是层级——DWD 离根 ODS 距离 1，ADS 离根 ODS 距离 3——用来证明
     * {@code layerDistance()} 真的在起作用：如果它退化回 {@code MAX_VALUE}，两个节点会因为度数
     * 相同而打平，谁被折叠将纯粹取决于 id 排序（更小 id 的节点 2 会赢），结果会和这里断言的
     * 相反。
     */
    @Test
    void layerDistancePrioritizesFrontierNodeCloserToRootLayerWhenDegreesAreTied() {
        SnapshotBuilder builder = new SnapshotBuilder()
                .node(1, "ODS")
                .node(2, "DWD")
                .node(3, "ADS");
        builder.edge(1, 2).edge(1, 3);
        LongStream.rangeClosed(10, 158).forEach(node -> builder.node(node, "TMP").edge(2, node));
        LongStream.rangeClosed(200, 348).forEach(node -> builder.node(node, "TMP").edge(3, node));

        GraphData result = query.query(builder.build(11), 1, 2, Direction.DOWNSTREAM);

        // node 2（DWD，与根 ODS 距离 1）优先分到预算，149 个子节点全部展开。
        assertThat(result.nodes()).extracting(NodeMeta::id).contains(10L, 158L);
        // node 3（ADS，与根 ODS 距离 3）度数与 node 2 完全相同，但距离更远，budget 不够被整体折叠。
        assertThat(result.nodes()).extracting(NodeMeta::id).doesNotContain(200L, 348L);
        assertThat(result.collapsed())
                .singleElement()
                .satisfies(collapsed -> {
                    assertThat(collapsed.nodeId()).isEqualTo(3);
                    assertThat(collapsed.hiddenCount()).isEqualTo(149);
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
    void listFiltersByKeywordLayerConnectorAndDatabaseCombination() {
        LineageGraphSnapshot snapshot = listFixture();

        assertThat(names(query.list(snapshot, "orders", null, null, null, 1, 20)))
                .containsExactly("hive://prod/dwd/orders", "hive://prod/ods/orders");
        assertThat(names(query.list(snapshot, null, "ODS", null, null, 1, 20)))
                .containsExactly("hive://prod/ods/orders", "paimon://prod/ods/users");
        assertThat(names(query.list(snapshot, null, null, "paimon", null, 1, 20)))
                .containsExactly("paimon://prod/dwd/users", "paimon://prod/ods/users");
        assertThat(names(query.list(snapshot, null, null, null, "dwd", 1, 20)))
                .containsExactly("hive://prod/dwd/orders", "paimon://prod/dwd/users");
        // 组合过滤：connector + layer 同时生效，取交集而不是并集。
        assertThat(names(query.list(snapshot, null, "ODS", "hive", null, 1, 20)))
                .containsExactly("hive://prod/ods/orders");
    }

    @Test
    void listReturnsEmptyPageWhenNoNodeMatchesTheFilter() {
        TablePage page = query.list(listFixture(), "no-such-keyword", null, null, null, 1, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void listSortsByCanonicalNameAscendingAndPaginatesAcrossPageBoundaries() {
        LineageGraphSnapshot snapshot = listFixture();

        assertThat(names(query.list(snapshot, null, null, null, null, 1, 2)))
                .containsExactly("hive://prod/dwd/orders", "hive://prod/ods/orders");
        assertThat(names(query.list(snapshot, null, null, null, null, 2, 2)))
                .containsExactly("hive://prod/ods/products", "paimon://prod/dwd/users");
        assertThat(names(query.list(snapshot, null, null, null, null, 3, 2)))
                .containsExactly("paimon://prod/ods/users");

        TablePage beyondLastPage = query.list(snapshot, null, null, null, null, 4, 2);
        assertThat(beyondLastPage.list()).isEmpty();
        // total 仍然反映过滤后的真实总数，不因为翻过头就变。
        assertThat(beyondLastPage.total()).isEqualTo(5);
    }

    @Test
    void listCapsSizeAtTheConfiguredMaximumEvenWhenMoreRowsExist() {
        Map<Long, NodeMeta> nodes = IntStream.rangeClosed(1, 250).boxed()
                .collect(Collectors.toMap(
                        id -> (long) id,
                        id -> new NodeMeta(id, 1L, "hive", "prod", "db", "t" + id,
                                String.format("hive://prod/db/t%03d", id), null)));
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        nodes.keySet().forEach(graph::addNode);
        LineageGraphSnapshot snapshot = LineageGraphSnapshot.copyOf(graph, nodes, 1, Instant.parse("2026-08-01T00:00:00Z"));

        TablePage page = query.list(snapshot, null, null, null, null, 1, 1000);

        assertThat(page.list()).hasSize(LineageGraphQuery.MAX_PAGE_SIZE);
        assertThat(page.total()).isEqualTo(250);
    }

    @Test
    void listRejectsNonPositivePageOrSize() {
        LineageGraphSnapshot snapshot = listFixture();

        assertThatThrownBy(() -> query.list(snapshot, null, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query.list(snapshot, null, null, null, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> names(TablePage page) {
        return page.list().stream().map(NodeMeta::canonicalName).toList();
    }

    /**
     * 5 个节点跨 2 个 connector / 2 个 database / 2 个已知层 + 1 个 UNKNOWN，
     * 足够覆盖过滤组合与排序边界，不需要任何边。
     */
    private static LineageGraphSnapshot listFixture() {
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        Map<Long, NodeMeta> nodes = new LinkedHashMap<>();
        nodes.put(1L, new NodeMeta(1, 1L, "hive", "prod", "ods", "orders", "hive://prod/ods/orders", "ODS"));
        nodes.put(2L, new NodeMeta(2, 1L, "hive", "prod", "dwd", "orders", "hive://prod/dwd/orders", "DWD"));
        nodes.put(3L, new NodeMeta(3, 1L, "paimon", "prod", "ods", "users", "paimon://prod/ods/users", "ODS"));
        nodes.put(4L, new NodeMeta(4, 1L, "paimon", "prod", "dwd", "users", "paimon://prod/dwd/users", "DWD"));
        nodes.put(5L, new NodeMeta(5, 1L, "hive", "prod", "ods", "products", "hive://prod/ods/products", null));
        nodes.keySet().forEach(graph::addNode);
        return LineageGraphSnapshot.copyOf(graph, nodes, 1, Instant.parse("2026-08-01T00:00:00Z"));
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
                    1L,
                    "paimon",
                    "prod",
                    "db",
                    "table_" + id,
                    "paimon://prod/db/table_" + id,
                    layer);
        }
    }
}
