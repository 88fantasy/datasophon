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

import com.datasophon.api.lineage.LineageGraphQuery.Direction;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class LineageQueryMysqlTest extends LineageMysqlTestSupport {

    private static final long CLUSTER_ID = 7;

    @Test
    void logicalAndPhysicalEdgeCountsMatchCurrentMysqlRows() {
        List<Long> nodeIds = List.of(
                insertNode("ods", "orders_raw", "ODS"),
                insertNode("dwd", "orders", "DWD"),
                insertNode("ads", "orders_report", "ADS"));
        long firstJob = insertJob("job-one");
        long secondJob = insertJob("job-two");
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_edge
                            (job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                        VALUES (?, 1, ?, ?, 'BATCH', 1),
                               (?, 1, ?, ?, 'STREAM', 1),
                               (?, 1, ?, ?, 'BATCH', 1)
                        """,
                firstJob, nodeIds.get(0), nodeIds.get(1),
                secondJob, nodeIds.get(0), nodeIds.get(1),
                firstJob, nodeIds.get(1), nodeIds.get(2));
        jdbcTemplate.update(
                "INSERT INTO t_ddh_lineage_generation (cluster_id, generation) VALUES (?, 4)", CLUSTER_ID);

        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        readTransaction.setReadOnly(true);
        LineageGraphSnapshot snapshot =
                readTransaction.execute(status -> new MysqlSnapshotLoader(jdbcTemplate).load(CLUSTER_ID));
        long currentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_ddh_lineage_edge WHERE is_current = 1", Long.class);
        long currentLogicalEdges = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT src_node_id, dst_node_id)
                        FROM t_ddh_lineage_edge
                        WHERE is_current = 1
                        """,
                Long.class);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.meta().physicalEdgeCount()).isEqualTo(currentRows);
        assertThat(snapshot.meta().logicalEdgeCount()).isEqualTo(currentLogicalEdges);
        assertThat(snapshot.graph().edgeValue(nodeIds.get(0), nodeIds.get(1)).orElseThrow().jobRefs())
                .hasSize(2);
        LineageGraphQuery.GraphData graph =
                new LineageGraphQuery().query(snapshot, nodeIds.getFirst(), 2, Direction.DOWNSTREAM);
        assertThat(graph.edges()).hasSize(2);
        assertThat(graph.edges()).flatExtracting(LineageGraphQuery.LogicalEdge::jobs).hasSize(3);
    }

    private static long insertNode(String database, String table, String layer) {
        String canonicalName = "paimon://prod/" + database + "/" + table;
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_node
                            (cluster_id, connector, catalog_name, database_name, table_name, canonical_name,
                             dw_layer, first_seen, last_seen)
                        VALUES (?, 'paimon', 'prod', ?, ?, ?, ?, NOW(3), NOW(3))
                        """,
                CLUSTER_ID, database, table, canonicalName, layer);
        // P1：回查必须带 cluster_id，否则跨集群同名表会静默拿到别的集群的 node id。
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_ddh_lineage_node WHERE cluster_id = ? AND canonical_name = ?",
                Long.class,
                CLUSTER_ID, canonicalName);
    }

    private static long insertJob(String jobName) {
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_data_job
                            (cluster_id, engine, job_name, job_type, state)
                        VALUES (?, 'spark', ?, 'BATCH', 'RUNNING')
                        """,
                CLUSTER_ID, jobName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_ddh_data_job WHERE cluster_id = ? AND job_name = ?",
                Long.class,
                CLUSTER_ID, jobName);
    }
}
