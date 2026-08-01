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

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

class LineageGraphSnapshotHolderTest {

    private static final Instant BUILT_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

    @Test
    void getForQueryIsEmptyForAClusterThatHasNeverPublished() {
        assertThat(holder.getForQuery(1L)).isEmpty();
        assertThat(holder.currentGeneration(1L)).isEqualTo(-1);
    }

    @Test
    void publishingOneClusterDoesNotAffectAnotherClustersVisibility() {
        LineageGraphSnapshot clusterOneSnapshot = snapshot(1, 5);
        holder.publishIfNotOlder(1L, clusterOneSnapshot);

        assertThat(holder.getForQuery(1L)).contains(clusterOneSnapshot);
        // 集群 2 从未发布过，即便集群 1 已经有数据，集群 2 仍然读不到任何东西。
        assertThat(holder.getForQuery(2L)).isEmpty();
        assertThat(holder.currentGeneration(2L)).isEqualTo(-1);

        LineageGraphSnapshot clusterTwoSnapshot = snapshot(2, 9);
        holder.publishIfNotOlder(2L, clusterTwoSnapshot);

        // 两个集群各自持有独立快照，互不覆盖。
        assertThat(holder.getForQuery(1L)).contains(clusterOneSnapshot);
        assertThat(holder.getForQuery(2L)).contains(clusterTwoSnapshot);
    }

    @Test
    void newerOrEqualGenerationIsPublishedAndReplacesThePreviousSnapshot() {
        LineageGraphSnapshot first = snapshot(1, 5);
        LineageGraphSnapshotHolder.PublishResult firstResult = holder.publishIfNotOlder(1L, first);
        assertThat(firstResult.published()).isTrue();
        assertThat(firstResult.currentGeneration()).isEqualTo(5);

        LineageGraphSnapshot newer = snapshot(1, 6);
        LineageGraphSnapshotHolder.PublishResult secondResult = holder.publishIfNotOlder(1L, newer);
        assertThat(secondResult.published()).isTrue();
        assertThat(holder.getForQuery(1L)).contains(newer);
        assertThat(holder.currentGeneration(1L)).isEqualTo(6);
    }

    @Test
    void olderGenerationIsRejectedAndThePublishedSnapshotIsUnchanged() {
        LineageGraphSnapshot current = snapshot(1, 10);
        holder.publishIfNotOlder(1L, current);

        LineageGraphSnapshot stale = snapshot(1, 9);
        LineageGraphSnapshotHolder.PublishResult result = holder.publishIfNotOlder(1L, stale);

        assertThat(result.published()).isFalse();
        // 拒绝时返回的是"赢家"（已发布的那个）代际，不是被拒绝快照自己的代际。
        assertThat(result.currentGeneration()).isEqualTo(10);
        assertThat(holder.getForQuery(1L)).contains(current);
        assertThat(holder.currentGeneration(1L)).isEqualTo(10);
    }

    private static LineageGraphSnapshot snapshot(long clusterId, long generation) {
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        long nodeId = clusterId * 1000 + generation;
        graph.addNode(nodeId);
        NodeMeta node = new NodeMeta(
                nodeId, clusterId, "paimon", "prod", "db", "table_" + nodeId,
                "paimon://prod/db/table_" + nodeId, null);
        return LineageGraphSnapshot.copyOf(graph, Map.of(nodeId, node), generation, BUILT_AT);
    }
}
