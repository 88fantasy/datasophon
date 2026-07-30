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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.ValueGraph;

/**
 * MySQL 权威数据的不可变血缘图快照。
 *
 * <p>禁止调用 {@link Graphs#transitiveClosure}：该算法复杂度为 O(V·E)，遇到超级节点时还会产生
 * 爆炸式结果集。上下游查询必须使用带深度与节点预算的分层 BFS。</p>
 *
 * <p>本类不提供任何增量增删边 API；快照只能由一次完整重建产生并整体替换。</p>
 */
public final class LineageGraphSnapshot {

    private final ImmutableValueGraph<Long, EdgeValue> graph;
    private final ImmutableMap<Long, NodeMeta> nodeMeta;
    private final LineageSnapshotMeta meta;

    public LineageGraphSnapshot(ImmutableValueGraph<Long, EdgeValue> graph, ImmutableMap<Long, NodeMeta> nodeMeta,
                                LineageSnapshotMeta meta) {
        this(graph, nodeMeta, meta, countPhysicalEdges(Objects.requireNonNull(graph, "graph")));
    }

    /** {@link #copyOf} 专用：物理边数已由同一张图算出，避免每次重建重复一次 O(E) 遍历。 */
    private LineageGraphSnapshot(ImmutableValueGraph<Long, EdgeValue> graph, ImmutableMap<Long, NodeMeta> nodeMeta,
                                 LineageSnapshotMeta meta, long physicalEdgeCount) {
        this.graph = graph;
        this.nodeMeta = Objects.requireNonNull(nodeMeta, "nodeMeta");
        this.meta = Objects.requireNonNull(meta, "meta");
        validate(physicalEdgeCount);
    }

    public static LineageGraphSnapshot copyOf(ValueGraph<Long, EdgeValue> graph, Map<Long, NodeMeta> nodeMeta,
                                              long generation, Instant builtAt) {
        return copyOf(graph, nodeMeta, generation, builtAt, LineageRebuildCoordinator.RebuildMetrics.NOOP);
    }

    public static LineageGraphSnapshot copyOf(ValueGraph<Long, EdgeValue> graph, Map<Long, NodeMeta> nodeMeta,
                                              long generation, Instant builtAt,
                                              LineageRebuildCoordinator.RebuildMetrics metrics) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(nodeMeta, "nodeMeta");
        Objects.requireNonNull(metrics, "metrics");
        long copyStartedAt = System.nanoTime();
        long cycleNanos = 0;
        boolean cycleMeasured = false;
        try {
            ImmutableValueGraph<Long, EdgeValue> immutableGraph = ImmutableValueGraph.copyOf(graph);
            ImmutableMap<Long, NodeMeta> immutableNodeMeta = ImmutableMap.copyOf(nodeMeta);
            long selfLoopCount = immutableGraph.edges().stream()
                    .filter(edge -> edge.nodeU().equals(edge.nodeV()))
                    .count();
            MutableGraph<Long> stripped = GraphBuilder.directed().allowsSelfLoops(false).build();
            immutableGraph.nodes().forEach(stripped::addNode);
            immutableGraph.edges().stream()
                    .filter(edge -> !edge.nodeU().equals(edge.nodeV()))
                    .forEach(edge -> stripped.putEdge(edge.nodeU(), edge.nodeV()));
            long cycleStartedAt = System.nanoTime();
            boolean hasNonTrivialCycle;
            try {
                hasNonTrivialCycle = Graphs.hasCycle(stripped);
            } finally {
                cycleNanos = System.nanoTime() - cycleStartedAt;
                cycleMeasured = true;
            }
            long physicalEdgeCount = countPhysicalEdges(immutableGraph);
            LineageSnapshotMeta snapshotMeta = LineageSnapshotMeta.fresh(generation, builtAt, selfLoopCount,
                    hasNonTrivialCycle, immutableGraph.nodes().size(), immutableGraph.edges().size(),
                    physicalEdgeCount);
            return new LineageGraphSnapshot(immutableGraph, immutableNodeMeta, snapshotMeta, physicalEdgeCount);
        } finally {
            long totalNanos = System.nanoTime() - copyStartedAt;
            if (cycleMeasured) {
                metrics.cycleCheck(cycleNanos);
            }
            metrics.snapshotCopy(Math.max(0, totalNanos - cycleNanos));
        }
    }

    public ImmutableValueGraph<Long, EdgeValue> graph() {
        return graph;
    }

    public ImmutableMap<Long, NodeMeta> nodeMeta() {
        return nodeMeta;
    }

    public LineageSnapshotMeta meta() {
        return meta;
    }

    public long generation() {
        return meta.generation();
    }

    private void validate(long physicalEdgeCount) {
        if (!graph.allowsSelfLoops()) {
            throw new IllegalArgumentException("lineage graph must allow self loops");
        }
        if (!nodeMeta.keySet().containsAll(graph.nodes())) {
            throw new IllegalArgumentException("nodeMeta must contain every graph node");
        }
        if (meta.nodeCount() != graph.nodes().size() || meta.logicalEdgeCount() != graph.edges().size()
                || meta.physicalEdgeCount() != physicalEdgeCount) {
            throw new IllegalArgumentException("snapshot metadata counts do not match graph contents");
        }
    }

    /** 供基准工具复用，避免基准侧手抄同一公式后与生产口径分叉（bench 是子包，package-private 不可见）。 */
    public static long countPhysicalEdges(ValueGraph<Long, EdgeValue> graph) {
        return graph.edges().stream()
                .mapToLong(edge -> graph.edgeValue(edge.nodeU(), edge.nodeV()).orElseThrow().jobRefs().size())
                .sum();
    }
}
