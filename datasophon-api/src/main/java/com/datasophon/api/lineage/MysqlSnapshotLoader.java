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

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

/**
 * MySQL-backed full snapshot projection.
 *
 * <p>The coordinator owns the read-only REPEATABLE READ transaction. This loader deliberately
 * contains no transaction annotation and never obtains a connection directly.</p>
 */
public final class MysqlSnapshotLoader implements LineageRebuildCoordinator.SnapshotLoader {

    private static final Logger logger = LoggerFactory.getLogger(MysqlSnapshotLoader.class);

    static final int DEFAULT_PAGE_SIZE = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final int pageSize;
    private final Clock clock;
    private final PageObserver pageObserver;
    private final LineageRebuildCoordinator.RebuildMetrics metrics;

    public MysqlSnapshotLoader(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, LineageRebuildCoordinator.RebuildMetrics.NOOP);
    }

    public MysqlSnapshotLoader(JdbcTemplate jdbcTemplate, LineageRebuildCoordinator.RebuildMetrics metrics) {
        this(jdbcTemplate, DEFAULT_PAGE_SIZE, Clock.systemUTC(), PageObserver.NOOP, metrics);
    }

    MysqlSnapshotLoader(JdbcTemplate jdbcTemplate, int pageSize) {
        this(jdbcTemplate, pageSize, Clock.systemUTC(), PageObserver.NOOP,
                LineageRebuildCoordinator.RebuildMetrics.NOOP);
    }

    MysqlSnapshotLoader(JdbcTemplate jdbcTemplate, int pageSize, Clock clock, PageObserver pageObserver) {
        this(jdbcTemplate, pageSize, clock, pageObserver, LineageRebuildCoordinator.RebuildMetrics.NOOP);
    }

    MysqlSnapshotLoader(JdbcTemplate jdbcTemplate, int pageSize, Clock clock, PageObserver pageObserver,
                        LineageRebuildCoordinator.RebuildMetrics metrics) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        this.pageSize = pageSize;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pageObserver = Objects.requireNonNull(pageObserver, "pageObserver");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public LineageGraphSnapshot load() {
        MappingTimer mappingTimer = new MappingTimer();
        long readStartedAt = System.nanoTime();
        Long generation;
        Map<Long, NodeMeta> nodes;
        Map<EdgeKey, List<JobRef>> edges;
        try {
            generation = jdbcTemplate.queryForObject(
                    "SELECT generation FROM t_ddh_lineage_generation WHERE id = 1", Long.class);
            nodes = loadNodes(mappingTimer);
            edges = loadCurrentEdges(mappingTimer);
        } finally {
            long totalReadNanos = System.nanoTime() - readStartedAt;
            metrics.mapping(mappingTimer.elapsedNanos());
            metrics.dbRead(Math.max(0, totalReadNanos - mappingTimer.elapsedNanos()));
        }

        MutableValueGraph<Long, EdgeValue> graph;
        long graphStartedAt = System.nanoTime();
        try {
            graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                    .allowsSelfLoops(true)
                    .build();
            nodes.keySet().forEach(graph::addNode);
            edges.forEach((key, jobRefs) -> graph.putEdgeValue(key.sourceNodeId(), key.targetNodeId(), new EdgeValue(jobRefs)));
        } finally {
            metrics.graphBuild(System.nanoTime() - graphStartedAt);
        }
        LineageGraphSnapshot snapshot = LineageGraphSnapshot.copyOf(
                graph, nodes, Objects.requireNonNull(generation, "generation"), clock.instant(), metrics);
        if (snapshot.meta().hasNonTrivialCycle()) {
            logger.warn(
                    "Lineage snapshot generation {} contains a non-trivial cycle; impact traversal results may be cyclic",
                    snapshot.generation());
        }
        return snapshot;
    }

    private Map<Long, NodeMeta> loadNodes(MappingTimer mappingTimer) {
        Map<Long, NodeMeta> nodes = new LinkedHashMap<>();
        long lastId = 0;
        while (true) {
            List<NodeMeta> page = jdbcTemplate.query(
                    """
                            SELECT id, connector, catalog_name, database_name, table_name, canonical_name, dw_layer
                            FROM t_ddh_lineage_node
                            WHERE id > ?
                            ORDER BY id
                            LIMIT ?
                            """,
                    mappingTimer.measure((resultSet, rowNumber) -> new NodeMeta(
                            resultSet.getLong("id"),
                            resultSet.getString("connector"),
                            resultSet.getString("catalog_name"),
                            resultSet.getString("database_name"),
                            resultSet.getString("table_name"),
                            resultSet.getString("canonical_name"),
                            resultSet.getString("dw_layer"))),
                    lastId, pageSize);
            if (page.isEmpty()) {
                return nodes;
            }
            for (NodeMeta node : page) {
                nodes.put(node.id(), node);
                lastId = node.id();
            }
            if (page.size() < pageSize) {
                return nodes;
            }
        }
    }

    private Map<EdgeKey, List<JobRef>> loadCurrentEdges(MappingTimer mappingTimer) {
        Map<EdgeKey, List<JobRef>> edges = new LinkedHashMap<>();
        long lastId = 0;
        while (true) {
            List<EdgeRow> page = jdbcTemplate.query(
                    """
                            SELECT id, job_id, definition_version, src_node_id, dst_node_id, flow_type
                            FROM t_ddh_lineage_edge
                            WHERE is_current = 1 AND id > ?
                            ORDER BY id
                            LIMIT ?
                            """,
                    mappingTimer.measure((resultSet, rowNumber) -> new EdgeRow(
                            resultSet.getLong("id"),
                            resultSet.getLong("job_id"),
                            resultSet.getInt("definition_version"),
                            resultSet.getLong("src_node_id"),
                            resultSet.getLong("dst_node_id"),
                            resultSet.getString("flow_type"))),
                    lastId, pageSize);
            if (page.isEmpty()) {
                return edges;
            }
            for (EdgeRow edge : page) {
                EdgeKey key = new EdgeKey(edge.sourceNodeId(), edge.targetNodeId());
                edges.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new JobRef(edge.jobId(), edge.id(), edge.definitionVersion(), edge.flowType()));
                lastId = edge.id();
            }
            pageObserver.afterEdgePage(lastId);
            if (page.size() < pageSize) {
                return edges;
            }
        }
    }

    record EdgeKey(long sourceNodeId, long targetNodeId) {
    }

    record EdgeRow(long id, long jobId, int definitionVersion, long sourceNodeId, long targetNodeId, String flowType) {
    }

    private static final class MappingTimer {

        private long elapsedNanos;

        private <T> RowMapper<T> measure(RowMapper<T> delegate) {
            return (resultSet, rowNumber) -> {
                long startedAt = System.nanoTime();
                try {
                    return delegate.mapRow(resultSet, rowNumber);
                } finally {
                    elapsedNanos += System.nanoTime() - startedAt;
                }
            };
        }

        private long elapsedNanos() {
            return elapsedNanos;
        }
    }

    @FunctionalInterface
    interface PageObserver {

        PageObserver NOOP = lastId -> {
        };

        void afterEdgePage(long lastId);
    }
}
