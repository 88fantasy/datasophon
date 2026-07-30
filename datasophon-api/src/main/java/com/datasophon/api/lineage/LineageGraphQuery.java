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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.ValueGraph;

/**
 * 对不可变血缘快照执行有深度和节点预算约束的分层 BFS。
 *
 * <p>节点预算只统计真实图节点；折叠 token 是查询元数据，不占预算。根节点占用第一个名额，
 * 因而常规查询最多再展开 {@value #MAX_NODES} - 1 个节点。</p>
 */
public final class LineageGraphQuery {

    public static final int MAX_NODES = 300;

    private static final String UNKNOWN_LAYER = "UNKNOWN";
    private static final List<String> STANDARD_LAYERS = List.of("CDC", "ODS", "DWD", "DWS", "ADS");
    private static final Map<String, Integer> LAYER_RANK =
            Map.of("CDC", 0, "ODS", 1, "DWD", 2, "DWS", 3, "ADS", 4);
    private static final Pattern EXPANSION_TOKEN = Pattern.compile("^n:([1-9][0-9]*):(up|down|both):g([0-9]+)$");

    public GraphData query(LineageGraphSnapshot snapshot, long rootNodeId, int depth, Direction direction) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(direction, "direction");
        if (depth < 1 || depth > 5) {
            throw new IllegalArgumentException("depth must be between 1 and 5");
        }
        requireNode(snapshot, rootNodeId);
        return traverse(snapshot, rootNodeId, depth, direction);
    }

    /**
     * 展开一个折叠节点，不重新遍历原查询树。
     *
     * <p>token 节点本身占一个节点名额；其隐藏邻居作为单节点 frontier 重新参与一轮商余分配。</p>
     */
    public GraphData expand(LineageGraphSnapshot snapshot, String token) {
        Objects.requireNonNull(snapshot, "snapshot");
        Expansion expansion = parseToken(token);
        if (expansion.generation() != snapshot.generation()) {
            throw new StaleExpansionTokenException(expansion.generation(), snapshot.generation());
        }
        requireNode(snapshot, expansion.nodeId());
        return traverse(snapshot, expansion.nodeId(), 1, expansion.direction());
    }

    public Optional<NodeMeta> table(LineageGraphSnapshot snapshot, long nodeId) {
        Objects.requireNonNull(snapshot, "snapshot");
        return Optional.ofNullable(snapshot.nodeMeta().get(nodeId));
    }

    /** 按实际图数据聚合所有层及所有层间逻辑边组合。 */
    public OverviewData overview(LineageGraphSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<String, Long> nodeCounts = new LinkedHashMap<>();
        STANDARD_LAYERS.forEach(layer -> nodeCounts.put(layer, 0L));
        snapshot.nodeMeta().values().forEach(node -> nodeCounts.merge(layerOf(node), 1L, Long::sum));

        List<LayerBlock> layers = nodeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(layerComparator()))
                .map(entry -> new LayerBlock(entry.getKey(), entry.getValue()))
                .toList();

        Map<LayerPair, Long> edgeCounts = new LinkedHashMap<>();
        for (EndpointPair<Long> edge : snapshot.graph().edges()) {
            String sourceLayer = layerOf(snapshot.nodeMeta().get(edge.source()));
            String targetLayer = layerOf(snapshot.nodeMeta().get(edge.target()));
            edgeCounts.merge(new LayerPair(sourceLayer, targetLayer), 1L, Long::sum);
        }
        List<LayerEdge> edges = edgeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(LayerPair::sourceLayer, layerComparator())
                        .thenComparing(LayerPair::targetLayer, layerComparator())))
                .map(entry -> new LayerEdge(
                        entry.getKey().sourceLayer(),
                        entry.getKey().targetLayer(),
                        entry.getValue()))
                .toList();
        return new OverviewData(layers, edges);
    }

    private GraphData traverse(LineageGraphSnapshot snapshot, long rootNodeId, int depth, Direction direction) {
        ValueGraph<Long, EdgeValue> graph = snapshot.graph();
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(rootNodeId);
        List<Long> frontier = List.of(rootNodeId);
        Map<EdgeKey, LogicalEdge> resultEdges = new LinkedHashMap<>();
        List<CollapsedNode> collapsed = new ArrayList<>();
        String rootLayer = layerOf(snapshot.nodeMeta().get(rootNodeId));

        for (int currentDepth = 0; currentDepth < depth && !frontier.isEmpty(); currentDepth++) {
            int remaining = MAX_NODES - visited.size();
            List<FrontierNode> orderedFrontier = frontier.stream()
                    .map(nodeId -> new FrontierNode(
                            nodeId,
                            layerDistance(rootLayer, layerOf(snapshot.nodeMeta().get(nodeId))),
                            neighbors(graph, nodeId, direction).size()))
                    .sorted(Comparator.comparingInt(FrontierNode::layerDistance)
                            .thenComparingInt(FrontierNode::degree)
                            .thenComparingLong(FrontierNode::nodeId))
                    .toList();

            int quotient = remaining / orderedFrontier.size();
            int remainder = remaining % orderedFrontier.size();
            int carriedBudget = 0;
            Set<Long> nextFrontier = new LinkedHashSet<>();

            for (int index = 0; index < orderedFrontier.size(); index++) {
                FrontierNode frontierNode = orderedFrontier.get(index);
                int assignedBudget = quotient + (index < remainder ? 1 : 0) + carriedBudget;
                List<Long> adjacent = neighbors(graph, frontierNode.nodeId(), direction);
                if (adjacent.size() > assignedBudget) {
                    collapsed.add(collapsed(snapshot.generation(), frontierNode.nodeId(), direction, adjacent.size()));
                    carriedBudget = assignedBudget;
                    continue;
                }

                int usedBudget = 0;
                for (long neighbor : adjacent) {
                    addVisibleEdges(graph, frontierNode.nodeId(), neighbor, direction, resultEdges);
                    if (visited.add(neighbor)) {
                        nextFrontier.add(neighbor);
                        usedBudget++;
                    }
                }
                carriedBudget = assignedBudget - usedBudget;
            }
            frontier = List.copyOf(nextFrontier);
        }

        List<NodeMeta> nodes = visited.stream()
                .sorted()
                .map(snapshot.nodeMeta()::get)
                .toList();
        List<LogicalEdge> edges = resultEdges.values().stream()
                .sorted(Comparator.comparingLong(LogicalEdge::src).thenComparingLong(LogicalEdge::dst))
                .toList();
        List<CollapsedNode> orderedCollapsed = collapsed.stream()
                .sorted(Comparator.comparingLong(CollapsedNode::nodeId)
                        .thenComparing(CollapsedNode::direction))
                .toList();
        return new GraphData(nodes, edges, orderedCollapsed, !orderedCollapsed.isEmpty());
    }

    private static void addVisibleEdges(ValueGraph<Long, EdgeValue> graph, long nodeId, long neighbor,
                                        Direction direction, Map<EdgeKey, LogicalEdge> resultEdges) {
        if (direction != Direction.UPSTREAM && graph.hasEdgeConnecting(nodeId, neighbor)) {
            addEdge(graph, nodeId, neighbor, resultEdges);
        }
        if (direction != Direction.DOWNSTREAM && graph.hasEdgeConnecting(neighbor, nodeId)) {
            addEdge(graph, neighbor, nodeId, resultEdges);
        }
    }

    private static void addEdge(ValueGraph<Long, EdgeValue> graph, long source, long target,
                                Map<EdgeKey, LogicalEdge> resultEdges) {
        EdgeKey key = new EdgeKey(source, target);
        resultEdges.computeIfAbsent(key, ignored -> {
            List<GraphJob> jobs = graph.edgeValue(source, target).orElseThrow().jobRefs().stream()
                    .map(job -> new GraphJob(job.jobId(), job.edgeId(), job.flowType()))
                    .sorted(Comparator.comparingLong(GraphJob::edgeId)
                            .thenComparingLong(GraphJob::jobId)
                            .thenComparing(GraphJob::flowType))
                    .toList();
            return new LogicalEdge(source, target, jobs);
        });
    }

    private static List<Long> neighbors(ValueGraph<Long, EdgeValue> graph, long nodeId, Direction direction) {
        Collection<Long> adjacent;
        if (direction == Direction.DOWNSTREAM) {
            adjacent = graph.successors(nodeId);
        } else if (direction == Direction.UPSTREAM) {
            adjacent = graph.predecessors(nodeId);
        } else {
            Set<Long> both = new TreeSet<>(graph.successors(nodeId));
            both.addAll(graph.predecessors(nodeId));
            adjacent = both;
        }
        return adjacent.stream().sorted().toList();
    }

    private static CollapsedNode collapsed(long generation, long nodeId, Direction direction, int hiddenCount) {
        String token = "n:" + nodeId + ":" + direction.tokenValue + ":g" + generation;
        return new CollapsedNode("collapsed", nodeId, token, hiddenCount, direction.requestValue);
    }

    private static Expansion parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidExpansionTokenException("expand token must not be blank");
        }
        Matcher matcher = EXPANSION_TOKEN.matcher(token);
        if (!matcher.matches()) {
            throw new InvalidExpansionTokenException("invalid expand token");
        }
        try {
            return new Expansion(
                    Long.parseLong(matcher.group(1)),
                    Direction.fromToken(matcher.group(2)),
                    Long.parseLong(matcher.group(3)));
        } catch (NumberFormatException e) {
            throw new InvalidExpansionTokenException("invalid expand token");
        }
    }

    private static void requireNode(LineageGraphSnapshot snapshot, long nodeId) {
        if (!snapshot.graph().nodes().contains(nodeId)) {
            throw new NodeNotFoundException(nodeId);
        }
    }

    private static String layerOf(NodeMeta node) {
        if (node == null || node.dwLayer() == null || node.dwLayer().isBlank()) {
            return UNKNOWN_LAYER;
        }
        return node.dwLayer().toUpperCase(Locale.ROOT);
    }

    private static int layerDistance(String rootLayer, String candidateLayer) {
        Integer rootRank = LAYER_RANK.get(rootLayer);
        Integer candidateRank = LAYER_RANK.get(candidateLayer);
        return rootRank == null || candidateRank == null
                ? Integer.MAX_VALUE
                : Math.abs(rootRank - candidateRank);
    }

    private static Comparator<String> layerComparator() {
        return Comparator.comparingInt((String layer) -> LAYER_RANK.getOrDefault(layer, Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());
    }

    public enum Direction {
        UPSTREAM("upstream", "up"),
        DOWNSTREAM("downstream", "down"),
        BOTH("both", "both");

        private final String requestValue;
        private final String tokenValue;

        Direction(String requestValue, String tokenValue) {
            this.requestValue = requestValue;
            this.tokenValue = tokenValue;
        }

        public String requestValue() {
            return requestValue;
        }

        public static Direction fromRequest(String value) {
            if (value == null) {
                throw new IllegalArgumentException("direction must not be null");
            }
            return switch (value) {
                case "upstream" -> UPSTREAM;
                case "downstream" -> DOWNSTREAM;
                case "both" -> BOTH;
                default -> throw new IllegalArgumentException(
                        "direction must be one of upstream, downstream or both");
            };
        }

        private static Direction fromToken(String value) {
            return switch (value) {
                case "up" -> UPSTREAM;
                case "down" -> DOWNSTREAM;
                case "both" -> BOTH;
                default -> throw new InvalidExpansionTokenException("invalid expand token");
            };
        }
    }

    public record GraphData(
            List<NodeMeta> nodes,
            List<LogicalEdge> edges,
            List<CollapsedNode> collapsed,
            boolean truncated) {

        public GraphData {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            collapsed = List.copyOf(collapsed);
        }
    }

    public record LogicalEdge(long src, long dst, List<GraphJob> jobs) {

        public LogicalEdge {
            jobs = List.copyOf(jobs);
        }
    }

    public record GraphJob(long jobId, long edgeId, String flowType) {
    }

    public record CollapsedNode(
                                String type,
                                long nodeId,
                                String token,
                                int hiddenCount,
                                String direction) {
    }

    public record OverviewData(List<LayerBlock> layers, List<LayerEdge> edges) {

        public OverviewData {
            layers = List.copyOf(layers);
            edges = List.copyOf(edges);
        }
    }

    public record LayerBlock(String layer, long nodeCount) {
    }

    public record LayerEdge(String srcLayer, String dstLayer, long count) {
    }

    public static final class NodeNotFoundException extends RuntimeException {

        private final long nodeId;

        private NodeNotFoundException(long nodeId) {
            super("lineage node " + nodeId + " was not found");
            this.nodeId = nodeId;
        }

        public long nodeId() {
            return nodeId;
        }
    }

    public static final class InvalidExpansionTokenException extends IllegalArgumentException {

        private InvalidExpansionTokenException(String message) {
            super(message);
        }
    }

    public static final class StaleExpansionTokenException extends RuntimeException {

        private StaleExpansionTokenException(long tokenGeneration, long currentGeneration) {
            super("expand token generation " + tokenGeneration
                    + " does not match current snapshot generation " + currentGeneration);
        }
    }

    private record FrontierNode(long nodeId, int layerDistance, int degree) {
    }

    private record Expansion(long nodeId, Direction direction, long generation) {
    }

    private record EdgeKey(long source, long target) {
    }

    private record LayerPair(String sourceLayer, String targetLayer) {
    }
}
