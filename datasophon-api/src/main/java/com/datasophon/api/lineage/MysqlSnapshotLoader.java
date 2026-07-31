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
import java.util.Collection;
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
    public Collection<Long> knownClusterIds() {
        // 只枚举有血缘作业的集群：血缘不关心尚未产生任何作业的集群，
        // 也刻意不耦合 t_ddh_cluster_info。
        return jdbcTemplate.queryForList("SELECT DISTINCT cluster_id FROM t_ddh_data_job", Long.class);
    }

    @Override
    public LineageGraphSnapshot load(long clusterId) {
        MappingTimer mappingTimer = new MappingTimer();
        long readStartedAt = System.nanoTime();
        long generation;
        Map<Long, NodeMeta> nodes;
        Map<EdgeKey, List<JobRef>> edges;
        try {
            // 代际行按集群惰性创建，缺失即该集群尚无结构性事件，代际为 0。
            List<Long> generations = jdbcTemplate.queryForList(
                    "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, clusterId);
            generation = generations.isEmpty() ? 0L : generations.get(0);
            nodes = loadNodes(clusterId, mappingTimer);
            edges = loadCurrentEdges(clusterId, mappingTimer);
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
                graph, nodes, generation, clock.instant(), metrics);
        if (snapshot.meta().hasNonTrivialCycle()) {
            logger.warn(
                    "Lineage snapshot generation {} contains a non-trivial cycle; impact traversal results may be cyclic",
                    snapshot.generation());
        }
        return snapshot;
    }

    private Map<Long, NodeMeta> loadNodes(long clusterId, MappingTimer mappingTimer) {
        Map<Long, NodeMeta> nodes = new LinkedHashMap<>();
        long lastId = 0;
        while (true) {
            List<NodeMeta> page = jdbcTemplate.query(
                    """
                            SELECT id, cluster_id, connector, catalog_name, database_name, table_name,
                                   canonical_name, dw_layer
                            FROM t_ddh_lineage_node
                            WHERE cluster_id = ? AND id > ?
                            ORDER BY id
                            LIMIT ?
                            """,
                    mappingTimer.measure((resultSet, rowNumber) -> new NodeMeta(
                            resultSet.getLong("id"),
                            resultSet.getLong("cluster_id"),
                            resultSet.getString("connector"),
                            resultSet.getString("catalog_name"),
                            resultSet.getString("database_name"),
                            resultSet.getString("table_name"),
                            resultSet.getString("canonical_name"),
                            resultSet.getString("dw_layer"))),
                    clusterId, lastId, pageSize);
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

    private Map<EdgeKey, List<JobRef>> loadCurrentEdges(long clusterId, MappingTimer mappingTimer) {
        Map<EdgeKey, List<JobRef>> edges = new LinkedHashMap<>();
        long lastId = 0;
        while (true) {
            // t_ddh_lineage_edge 刻意不加 cluster_id（L3 §1.3）：边的归属由作业决定，
            // 经 job_id 关联 t_ddh_data_job.cluster_id 过滤即可，避免同一事实存两处。
            List<EdgeRow> page = jdbcTemplate.query(
                    """
                            SELECT e.id, e.job_id, e.definition_version, e.src_node_id, e.dst_node_id, e.flow_type
                            FROM t_ddh_lineage_edge e
                            JOIN t_ddh_data_job j ON e.job_id = j.id
                            WHERE j.cluster_id = ? AND e.is_current = 1 AND e.id > ?
                            ORDER BY e.id
                            LIMIT ?
                            """,
                    mappingTimer.measure((resultSet, rowNumber) -> new EdgeRow(
                            resultSet.getLong("id"),
                            resultSet.getLong("job_id"),
                            resultSet.getInt("definition_version"),
                            resultSet.getLong("src_node_id"),
                            resultSet.getLong("dst_node_id"),
                            resultSet.getString("flow_type"))),
                    clusterId, lastId, pageSize);
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
