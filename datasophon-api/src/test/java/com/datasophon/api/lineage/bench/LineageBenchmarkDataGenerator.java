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

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * T0 远程 MySQL 基准造数工具。
 *
 * <p>生成 5000 作业、15000 节点和 20000 条 current 物理边，数据包含并行边、自环和超级节点。
 * 工具不会删除已有数据；已存在一套完整基准作业时会复用，数量异常则拒绝继续。请仅在专用基准库执行。</p>
 */
public final class LineageBenchmarkDataGenerator {

    public static final int JOB_COUNT = 5_000;
    public static final int NODE_COUNT = 15_000;
    public static final int EDGE_COUNT = 20_000;
    static final String BENCHMARK_CATALOG = "lineage-l1-bench";
    static final String JOB_PREFIX = "lineage-l1-bench-job-";

    private static final int BATCH_SIZE = 1_000;

    private LineageBenchmarkDataGenerator() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromEnvironment();
        System.out.printf(Locale.ROOT, "Seeding remote MySQL %s with %,d jobs, %,d nodes and %,d edges%n",
                config.redactedJdbcUrl(), JOB_COUNT, NODE_COUNT, EDGE_COUNT);
        try (
                Connection connection = DriverManager.getConnection(config.jdbcUrl(), config.username(),
                        config.password())) {
            connection.setAutoCommit(false);
            seed(connection, config.clusterId());
            connection.commit();
            printCounts(connection, config.clusterId());
        }
    }

    static void seed(Connection connection, int clusterId) throws SQLException {
        List<Long> jobIds = insertJobs(connection, clusterId);
        if (jobIds.size() != JOB_COUNT) {
            throw new IllegalStateException("expected " + JOB_COUNT + " benchmark jobs but found " + jobIds.size());
        }
        insertDefinitions(connection, jobIds);
        insertNodes(connection, clusterId);
        List<Long> nodeIds = readNodeIds(connection);
        if (nodeIds.size() != NODE_COUNT) {
            throw new IllegalStateException(
                    "expected " + NODE_COUNT + " benchmark nodes but found " + nodeIds.size());
        }
        insertEdges(connection, jobIds, nodeIds);
    }

    /** 返回本次可用的作业 id 列表，避免调用方为拿同一份数据再查一次 5000 行排序扫描。 */
    private static List<Long> insertJobs(Connection connection, int clusterId) throws SQLException {
        List<Long> existingJobIds = readJobIds(connection, clusterId);
        if (existingJobIds.size() == JOB_COUNT) {
            return existingJobIds;
        }
        if (!existingJobIds.isEmpty()) {
            throw new IllegalStateException(
                    "expected either 0 or " + JOB_COUNT + " existing benchmark jobs but found " + existingJobIds.size());
        }
        String sql = """
                INSERT INTO t_ddh_data_job
                    (cluster_id, job_name, engine, otel_service_name, job_type, dw_layer, owner, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < JOB_COUNT; i++) {
                String engine = i % 5 == 0 ? "FLINK" : "SPARK";
                statement.setInt(1, clusterId);
                statement.setString(2, JOB_PREFIX + String.format(Locale.ROOT, "%05d", i));
                statement.setString(3, engine);
                statement.setString(4, "lineage-benchmark-" + i);
                statement.setString(5, engine.equals("FLINK") ? "FLINK_SQL" : "SPARK_SQL");
                statement.setString(6, layer(i));
                statement.setString(7, "lineage-benchmark");
                statement.setString(8, "ACTIVE");
                addBatch(statement, i);
            }
            statement.executeBatch();
        }
        return readJobIds(connection, clusterId);
    }

    private static void insertDefinitions(Connection connection, List<Long> jobIds) throws SQLException {
        String sql = """
                INSERT IGNORE INTO t_ddh_data_job_definition
                    (job_id, version, definition_text, content_hash)
                VALUES (?, 1, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < jobIds.size(); i++) {
                statement.setLong(1, jobIds.get(i));
                statement.setString(2, "INSERT OVERWRITE benchmark_sink SELECT * FROM benchmark_source_" + i);
                statement.setString(3, String.format(Locale.ROOT, "%064x", i + 1L));
                addBatch(statement, i);
            }
            statement.executeBatch();
        }
    }

    private static void insertNodes(Connection connection, int clusterId) throws SQLException {
        String sql = """
                INSERT IGNORE INTO t_ddh_lineage_node
                    (cluster_id, connector, catalog_name, database_name, table_name, canonical_name,
                     dw_layer, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < NODE_COUNT; i++) {
                String layer = layer(i);
                String database = layer.toLowerCase(Locale.ROOT);
                String table = database + "_benchmark_orders_enriched_" + String.format(Locale.ROOT, "%05d", i);
                String connector = i % 7 == 0 ? "doris" : "paimon";
                String canonicalName = connector + "://" + BENCHMARK_CATALOG + "/" + database + "/" + table;
                statement.setInt(1, clusterId);
                statement.setString(2, connector);
                statement.setString(3, BENCHMARK_CATALOG);
                statement.setString(4, database);
                statement.setString(5, table);
                statement.setString(6, canonicalName);
                statement.setString(7, layer);
                statement.setObject(8, Instant.parse("2026-07-29T00:00:00Z"));
                statement.setObject(9, Instant.parse("2026-07-29T00:00:00Z"));
                addBatch(statement, i);
            }
            statement.executeBatch();
        }
    }

    private static void insertEdges(Connection connection, List<Long> jobIds, List<Long> nodeIds) throws SQLException {
        List<EdgeSeed> edges = generateEdges(jobIds, nodeIds);
        String sql = """
                INSERT IGNORE INTO t_ddh_lineage_edge
                    (job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                VALUES (?, 1, ?, ?, ?, 1)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < edges.size(); i++) {
                EdgeSeed edge = edges.get(i);
                statement.setLong(1, edge.jobId());
                statement.setLong(2, edge.srcNodeId());
                statement.setLong(3, edge.dstNodeId());
                statement.setString(4, edge.flowType());
                addBatch(statement, i);
            }
            statement.executeBatch();
        }
    }

    static List<EdgeSeed> generateEdges(List<Long> jobIds, List<Long> nodeIds) {
        Map<EdgeIdentity, EdgeSeed> edges = new LinkedHashMap<>(EDGE_COUNT);

        for (int i = 0; i < NODE_COUNT - 1; i++) {
            addEdge(edges, edge(jobIds, nodeIds, i, i, i + 1));
        }
        for (int i = 0; i < 100; i++) {
            int node = i * 101 % NODE_COUNT;
            addEdge(edges, edge(jobIds, nodeIds, NODE_COUNT + i, node, node));
        }
        for (int i = 1; i <= 1_000; i++) {
            addEdge(edges, edge(jobIds, nodeIds, NODE_COUNT + 100 + i, i, 0));
        }
        for (int i = 0; i < 1_000; i++) {
            int src = i * 13 % (NODE_COUNT - 1);
            EdgeSeed original = edge(jobIds, nodeIds, i, src, src + 1);
            long parallelJob = jobIds.get((i + 1) % jobIds.size());
            addEdge(edges, new EdgeSeed(parallelJob, original.srcNodeId(), original.dstNodeId(),
                    i % 3 == 0 ? "STREAM" : "BATCH"));
        }

        int candidate = 0;
        while (edges.size() < EDGE_COUNT) {
            int src = candidate * 37 % NODE_COUNT;
            int dst = (src + 97 + candidate % 211) % NODE_COUNT;
            addEdge(edges, edge(jobIds, nodeIds, NODE_COUNT + 2_100 + candidate, src, dst));
            candidate++;
        }
        return List.copyOf(edges.values());
    }

    private static EdgeSeed edge(List<Long> jobIds, List<Long> nodeIds, int ordinal, int srcIndex, int dstIndex) {
        return new EdgeSeed(jobIds.get(Math.floorMod(ordinal, jobIds.size())), nodeIds.get(srcIndex),
                nodeIds.get(dstIndex), ordinal % 5 == 0 ? "STREAM" : "BATCH");
    }

    private static void addEdge(Map<EdgeIdentity, EdgeSeed> edges, EdgeSeed edge) {
        edges.putIfAbsent(new EdgeIdentity(edge.jobId(), edge.srcNodeId(), edge.dstNodeId()), edge);
    }

    private static List<Long> readJobIds(Connection connection, int clusterId) throws SQLException {
        String sql = """
                SELECT id FROM t_ddh_data_job
                WHERE cluster_id = ? AND job_name LIKE ?
                ORDER BY job_name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clusterId);
            statement.setString(2, JOB_PREFIX + "%");
            return readIds(statement);
        }
    }

    private static List<Long> readNodeIds(Connection connection) throws SQLException {
        String sql = """
                SELECT id FROM t_ddh_lineage_node
                WHERE catalog_name = ?
                ORDER BY canonical_name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BENCHMARK_CATALOG);
            return readIds(statement);
        }
    }

    private static List<Long> readIds(PreparedStatement statement) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
        }
        return ids;
    }

    private static void printCounts(Connection connection, int clusterId) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM t_ddh_data_job WHERE cluster_id = ? AND job_name LIKE ?) AS jobs,
                    (SELECT COUNT(*) FROM t_ddh_lineage_node WHERE catalog_name = ?) AS nodes,
                    (SELECT COUNT(*) FROM t_ddh_lineage_edge e
                       JOIN t_ddh_data_job j ON j.id = e.job_id
                      WHERE j.cluster_id = ? AND j.job_name LIKE ? AND e.is_current = 1) AS edges
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clusterId);
            statement.setString(2, JOB_PREFIX + "%");
            statement.setString(3, BENCHMARK_CATALOG);
            statement.setInt(4, clusterId);
            statement.setString(5, JOB_PREFIX + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                System.out.printf(Locale.ROOT, "Seeded rows: jobs=%,d nodes=%,d currentEdges=%,d%n",
                        resultSet.getLong("jobs"), resultSet.getLong("nodes"), resultSet.getLong("edges"));
            }
        }
    }

    private static void addBatch(PreparedStatement statement, int index) throws SQLException {
        statement.addBatch();
        if ((index + 1) % BATCH_SIZE == 0) {
            statement.executeBatch();
        }
    }

    private static String layer(int index) {
        return switch (index % 4) {
            case 0 -> "ODS";
            case 1 -> "DWD";
            case 2 -> "DWS";
            default -> "ADS";
        };
    }

    record EdgeSeed(long jobId, long srcNodeId, long dstNodeId, String flowType) {
    }

    private record EdgeIdentity(long jobId, long srcNodeId, long dstNodeId) {
    }

    public record BenchmarkConfig(String jdbcUrl, String username, String password, int clusterId) {

        static BenchmarkConfig fromEnvironment() {
            String jdbcUrl = requiredEnvironment("LINEAGE_BENCH_JDBC_URL");
            rejectLocalMysql(jdbcUrl);
            String username = requiredEnvironment("LINEAGE_BENCH_DB_USER");
            String password = requiredEnvironment("LINEAGE_BENCH_DB_PASSWORD");
            int clusterId = Integer.parseInt(System.getenv().getOrDefault("LINEAGE_BENCH_CLUSTER_ID", "-225"));
            return new BenchmarkConfig(jdbcUrl, username, password, clusterId);
        }

        String redactedJdbcUrl() {
            int query = jdbcUrl.indexOf('?');
            return query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
        }

        private static void rejectLocalMysql(String jdbcUrl) {
            String uriValue = jdbcUrl.substring("jdbc:".length());
            try {
                URI uri = new URI(uriValue);
                String host = uri.getHost();
                if (host == null || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                        || host.equals("::1")) {
                    throw new IllegalArgumentException("T0 requires a remote MySQL host, not " + host);
                }
            } catch (URISyntaxException | IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("invalid LINEAGE_BENCH_JDBC_URL", e);
            }
        }

        private static String requiredEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing environment variable " + name);
            }
            return value;
        }
    }
}
