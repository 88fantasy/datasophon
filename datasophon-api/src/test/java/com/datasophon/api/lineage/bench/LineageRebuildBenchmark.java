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

package com.datasophon.api.lineage.bench;

import com.datasophon.api.lineage.EdgeValue;
import com.datasophon.api.lineage.JobRef;
import com.datasophon.api.lineage.LineageGraphSnapshot;
import com.datasophon.api.lineage.LineageSnapshotMeta;
import com.datasophon.api.lineage.NodeMeta;
import com.datasophon.api.lineage.bench.LineageBenchmarkDataGenerator.BenchmarkConfig;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

/**
 * T0 血缘全量重建基准脚手架。
 *
 * <p>运行前先执行 {@link LineageBenchmarkDataGenerator} 写入专用远程 MySQL。该程序统计 warmup 后
 * 多次重建的 p50/p95/p99，并独立记录 DB read、结果映射、建图、copyOf、hasCycle、publish 六段。
 * JFR 需通过 JVM 参数在进程外开启；若 classpath 含 JOL，则额外输出快照 retained heap。</p>
 */
public final class LineageRebuildBenchmark {

    private static final int PAGE_SIZE = 10_000;

    private final BenchmarkConfig config;
    private final AtomicReference<LineageGraphSnapshot> published = new AtomicReference<>();

    private LineageRebuildBenchmark(BenchmarkConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromEnvironment();
        int warmup = integerEnvironment("LINEAGE_BENCH_WARMUP", 5);
        int iterations = integerEnvironment("LINEAGE_BENCH_ITERATIONS", 30);
        if (warmup < 0 || iterations < 3) {
            throw new IllegalArgumentException("warmup must be >= 0 and iterations must be >= 3");
        }

        LineageRebuildBenchmark benchmark = new LineageRebuildBenchmark(config);
        benchmark.printRuntime();
        benchmark.explainAnalyze();
        benchmark.printLockDiagnostics();
        for (int i = 0; i < warmup; i++) {
            benchmark.rebuildOnce();
        }
        List<Measurement> measurements = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            measurements.add(benchmark.rebuildOnce());
        }
        printPercentiles(measurements);
        benchmark.printRetainedHeapIfJolPresent();
        benchmark.printJfrGuidance();
    }

    private Measurement rebuildOnce() throws SQLException {
        long totalStart = System.nanoTime();

        long phaseStart = System.nanoTime();
        RawSnapshot raw = readRawSnapshot();
        long dbReadNanos = System.nanoTime() - phaseStart;

        phaseStart = System.nanoTime();
        MappedSnapshot mapped = mapRows(raw);
        long mappingNanos = System.nanoTime() - phaseStart;

        phaseStart = System.nanoTime();
        MutableValueGraph<Long, EdgeValue> mutableGraph = buildGraph(mapped);
        long buildGraphNanos = System.nanoTime() - phaseStart;

        phaseStart = System.nanoTime();
        ImmutableValueGraph<Long, EdgeValue> immutableGraph = ImmutableValueGraph.copyOf(mutableGraph);
        ImmutableMap<Long, NodeMeta> immutableNodeMeta = ImmutableMap.copyOf(mapped.nodeMeta());
        long copyOfNanos = System.nanoTime() - phaseStart;

        phaseStart = System.nanoTime();
        boolean hasCycle = Graphs.hasCycle(immutableGraph.asGraph());
        long hasCycleNanos = System.nanoTime() - phaseStart;

        long physicalEdges = LineageGraphSnapshot.countPhysicalEdges(immutableGraph);
        LineageSnapshotMeta meta = LineageSnapshotMeta.fresh(raw.generation(), Instant.now(), hasCycle,
                immutableGraph.nodes().size(), immutableGraph.edges().size(), physicalEdges);
        LineageGraphSnapshot snapshot = new LineageGraphSnapshot(immutableGraph, immutableNodeMeta, meta);

        phaseStart = System.nanoTime();
        published.set(snapshot);
        long publishNanos = System.nanoTime() - phaseStart;

        return new Measurement(System.nanoTime() - totalStart, dbReadNanos, mappingNanos, buildGraphNanos,
                copyOfNanos, hasCycleNanos, publishNanos);
    }

    private RawSnapshot readRawSnapshot() throws SQLException {
        try (
                Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(),
                        config.password())) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                long generation = readGeneration(connection);
                List<NodeMeta> nodes = readNodes(connection);
                List<RawEdge> edges = readEdges(connection);
                connection.commit();
                return new RawSnapshot(generation, nodes, edges);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static long readGeneration(Connection connection) throws SQLException {
        try (
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement
                        .executeQuery("SELECT generation FROM t_ddh_lineage_generation WHERE id = 1")) {
            if (!resultSet.next()) {
                throw new SQLException("t_ddh_lineage_generation row id=1 is missing");
            }
            return resultSet.getLong(1);
        }
    }

    private static List<NodeMeta> readNodes(Connection connection) throws SQLException {
        String sql = """
                SELECT id, connector, catalog_name, database_name, table_name, canonical_name, dw_layer
                FROM t_ddh_lineage_node
                WHERE catalog_name = ? AND id > ?
                ORDER BY id
                LIMIT ?
                """;
        List<NodeMeta> rows = new ArrayList<>(LineageBenchmarkDataGenerator.NODE_COUNT);
        long lastId = 0;
        while (true) {
            int pageRows = 0;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, LineageBenchmarkDataGenerator.BENCHMARK_CATALOG);
                statement.setLong(2, lastId);
                statement.setInt(3, PAGE_SIZE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        lastId = resultSet.getLong(1);
                        rows.add(new NodeMeta(lastId, resultSet.getString(2), resultSet.getString(3),
                                resultSet.getString(4), resultSet.getString(5), resultSet.getString(6),
                                resultSet.getString(7)));
                        pageRows++;
                    }
                }
            }
            if (pageRows < PAGE_SIZE) {
                return rows;
            }
        }
    }

    private List<RawEdge> readEdges(Connection connection) throws SQLException {
        String sql = """
                SELECT e.id, e.job_id, e.definition_version, e.src_node_id, e.dst_node_id, e.flow_type
                FROM t_ddh_lineage_edge e
                JOIN t_ddh_data_job j ON j.id = e.job_id
                WHERE e.is_current = 1 AND j.cluster_id = ? AND j.job_name LIKE ? AND e.id > ?
                ORDER BY e.id
                LIMIT ?
                """;
        List<RawEdge> rows = new ArrayList<>(LineageBenchmarkDataGenerator.EDGE_COUNT);
        long lastId = 0;
        while (true) {
            int pageRows = 0;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, config.clusterId());
                statement.setString(2, LineageBenchmarkDataGenerator.JOB_PREFIX + "%");
                statement.setLong(3, lastId);
                statement.setInt(4, PAGE_SIZE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        lastId = resultSet.getLong(1);
                        rows.add(new RawEdge(lastId, resultSet.getLong(2), resultSet.getInt(3),
                                resultSet.getLong(4), resultSet.getLong(5), resultSet.getString(6)));
                        pageRows++;
                    }
                }
            }
            if (pageRows < PAGE_SIZE) {
                return rows;
            }
        }
    }

    private static MappedSnapshot mapRows(RawSnapshot raw) {
        Map<Long, NodeMeta> nodes = new HashMap<>(raw.nodes().size() * 2);
        for (NodeMeta node : raw.nodes()) {
            nodes.put(node.id(), node);
        }
        List<MappedEdge> edges = raw.edges().stream()
                .map(row -> new MappedEdge(row.srcNodeId(), row.dstNodeId(),
                        new JobRef(row.jobId(), row.edgeId(), row.definitionVersion(), row.flowType())))
                .toList();
        return new MappedSnapshot(nodes, edges);
    }

    private static MutableValueGraph<Long, EdgeValue> buildGraph(MappedSnapshot mapped) {
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .expectedNodeCount(mapped.nodeMeta().size())
                .build();
        mapped.nodeMeta().keySet().forEach(graph::addNode);

        Map<EdgeKey, List<JobRef>> logicalEdges = new LinkedHashMap<>();
        for (MappedEdge edge : mapped.edges()) {
            logicalEdges.computeIfAbsent(new EdgeKey(edge.srcNodeId(), edge.dstNodeId()), ignored -> new ArrayList<>())
                    .add(edge.jobRef());
        }
        logicalEdges.forEach(
                (key, jobs) -> graph.putEdgeValue(key.srcNodeId(), key.dstNodeId(), new EdgeValue(jobs)));
        return graph;
    }

    private void explainAnalyze() {
        String sql = """
                EXPLAIN ANALYZE
                SELECT e.id, e.job_id, e.definition_version, e.src_node_id, e.dst_node_id, e.flow_type
                FROM t_ddh_lineage_edge e
                JOIN t_ddh_data_job j ON j.id = e.job_id
                WHERE e.is_current = 1 AND j.cluster_id = %d AND j.job_name LIKE '%s%%'
                ORDER BY e.id
                """.formatted(config.clusterId(), LineageBenchmarkDataGenerator.JOB_PREFIX);
        System.out.println("EXPLAIN ANALYZE for the real rebuild SELECT:");
        queryAndPrint(sql);
    }

    private void printLockDiagnostics() {
        System.out.println("InnoDB lock/deadlock/history diagnostics (best effort; permissions may restrict them):");
        queryAndPrint("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_%'");
        queryAndPrint("""
                SELECT NAME, COUNT
                FROM information_schema.INNODB_METRICS
                WHERE NAME IN ('lock_deadlocks', 'trx_rseg_history_len')
                """);
    }

    private void queryAndPrint(String sql) {
        try (
                Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(),
                        config.password());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        line.append(" | ");
                    }
                    line.append(resultSet.getString(i));
                }
                System.out.println(line);
            }
        } catch (SQLException e) {
            System.out.println("  unavailable: " + e.getMessage());
        }
    }

    private static void printPercentiles(List<Measurement> measurements) {
        System.out.printf(Locale.ROOT, "%-14s %12s %12s %12s%n", "phase", "p50(ms)", "p95(ms)", "p99(ms)");
        printPhase("total", measurements, Measurement::totalNanos);
        printPhase("db-read", measurements, Measurement::dbReadNanos);
        printPhase("mapping", measurements, Measurement::mappingNanos);
        printPhase("build-graph", measurements, Measurement::buildGraphNanos);
        printPhase("copyOf", measurements, Measurement::copyOfNanos);
        printPhase("hasCycle", measurements, Measurement::hasCycleNanos);
        printPhase("publish", measurements, Measurement::publishNanos);
    }

    private static void printPhase(String name, List<Measurement> values, ToLongFunction<Measurement> extractor) {
        List<Long> sorted = values.stream().map(extractor::applyAsLong).sorted(Comparator.naturalOrder()).toList();
        System.out.printf(Locale.ROOT, "%-14s %12.3f %12.3f %12.3f%n", name,
                nanosToMillis(percentile(sorted, 0.50)),
                nanosToMillis(percentile(sorted, 0.95)),
                nanosToMillis(percentile(sorted, 0.99)));
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private void printRuntime() {
        Runtime runtime = Runtime.getRuntime();
        System.out.printf(Locale.ROOT, "JDK=%s maxHeap=%,d MiB remoteMySQL=%s%n",
                System.getProperty("java.version"), runtime.maxMemory() / 1024 / 1024, config.redactedJdbcUrl());
    }

    private void printRetainedHeapIfJolPresent() {
        LineageGraphSnapshot snapshot = published.get();
        if (snapshot == null) {
            return;
        }
        try {
            Class<?> graphLayout = Class.forName("org.openjdk.jol.info.GraphLayout");
            Method parseInstance = graphLayout.getMethod("parseInstance", Object[].class);
            Object layout = parseInstance.invoke(null, (Object) new Object[]{snapshot});
            long totalSize = (long) graphLayout.getMethod("totalSize").invoke(layout);
            System.out.printf(Locale.ROOT, "JOL retained heap: %,d bytes%n", totalSize);
        } catch (ClassNotFoundException e) {
            System.out.println("JOL not on classpath; add it only to the benchmark launch classpath to measure retained heap.");
        } catch (ReflectiveOperationException e) {
            System.out.println("JOL retained-heap measurement failed: " + e.getMessage());
        }
    }

    private void printJfrGuidance() {
        System.out.println("""
                JFR is external to this harness. Re-run with:
                  -XX:StartFlightRecording=filename=lineage-rebuild.jfr,settings=profile,dumponexit=true
                Inspect Object Allocation and Object Promotion events; a three-minute published snapshot is
                expected to survive young GC and may be promoted to old gen.
                Also record lock_wait p95/p99, deadlocks, history_list_length, replication lag and the
                longest read-only transaction in the final report.
                """);
    }

    private static int integerEnvironment(String name, int defaultValue) {
        return Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(defaultValue)));
    }

    private record RawEdge(long edgeId, long jobId, int definitionVersion, long srcNodeId, long dstNodeId,
                           String flowType) {
    }

    private record RawSnapshot(long generation, List<NodeMeta> nodes, List<RawEdge> edges) {
    }

    private record MappedEdge(long srcNodeId, long dstNodeId, JobRef jobRef) {
    }

    private record MappedSnapshot(Map<Long, NodeMeta> nodeMeta, List<MappedEdge> edges) {
    }

    private record EdgeKey(long srcNodeId, long dstNodeId) {
    }

    private record Measurement(long totalNanos, long dbReadNanos, long mappingNanos, long buildGraphNanos,
                               long copyOfNanos, long hasCycleNanos, long publishNanos) {
    }
}
