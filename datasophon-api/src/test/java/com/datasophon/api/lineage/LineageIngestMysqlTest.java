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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LineageIngestMysqlTest extends LineageMysqlTestSupport {

    private static final long CLUSTER_ID = 7;
    private static final Instant BASE_TIME = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void realEventPersistsAndDuplicateDeliveryIsIdempotent() {
        LineageIngestService service = ingestService();
        var payload = event("same-run", "COMPLETE", BASE_TIME, "orders");

        assertThat(service.ingest(CLUSTER_ID, payload).status())
                .isEqualTo(LineageIngestService.Status.CHANGED);
        for (int i = 1; i < 100; i++) {
            assertThat(service.ingest(CLUSTER_ID, payload).status())
                    .isEqualTo(LineageIngestService.Status.DUPLICATE);
        }

        assertThat(count("t_ddh_lineage_event")).isEqualTo(1);
        assertThat(count("t_ddh_data_job")).isEqualTo(1);
        assertThat(count("t_ddh_data_job_definition")).isEqualTo(1);
        assertThat(count("t_ddh_lineage_edge")).isEqualTo(1);
        assertThat(generation()).isEqualTo(1);
    }

    @Test
    void unchangedStructureAcrossOneHundredRunsDoesNotCreateDefinitions() {
        LineageIngestService service = ingestService();
        service.ingest(CLUSTER_ID, event("initial", "COMPLETE", BASE_TIME, "orders"));

        for (int i = 1; i <= 100; i++) {
            LineageIngestService.IngestResult result = service.ingest(
                    CLUSTER_ID,
                    event("run-" + i, "COMPLETE", BASE_TIME.plusSeconds(i), "orders"));
            assertThat(result.status()).isEqualTo(LineageIngestService.Status.UNCHANGED);
        }

        assertThat(count("t_ddh_data_job_definition")).isEqualTo(1);
        assertThat(count("t_ddh_lineage_edge")).isEqualTo(1);
        assertThat(generation()).isEqualTo(1);
    }

    @Test
    void lateAndOverlappingRunsCannotRollBackCurrentStructure() {
        LineageIngestService service = ingestService();
        service.ingest(CLUSTER_ID, event("start-1", "START", BASE_TIME.plusSeconds(10), "orders-v1"));
        service.ingest(CLUSTER_ID, event("start-2", "START", BASE_TIME.plusSeconds(20), "orders-v2"));
        service.ingest(CLUSTER_ID, event("complete-2", "COMPLETE", BASE_TIME.plusSeconds(20), "orders-v2"));
        service.ingest(CLUSTER_ID, event("complete-1", "COMPLETE", BASE_TIME.plusSeconds(10), "orders-v1"));

        assertThat(currentOutput()).isEqualTo("paimon://prod/dwd/orders-v2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_ddh_lineage_parse_log WHERE status = 'LATE_EVENT'",
                Integer.class)).isEqualTo(2);
        assertThat(count("t_ddh_data_job_definition")).isEqualTo(2);
    }

    @Test
    void aToBToAProducesVersionThreeWithRepeatedContentHash() {
        LineageIngestService service = ingestService();
        service.ingest(CLUSTER_ID, event("run-a1", "COMPLETE", BASE_TIME.plusSeconds(10), "orders-a"));
        service.ingest(CLUSTER_ID, event("run-b", "COMPLETE", BASE_TIME.plusSeconds(20), "orders-b"));
        service.ingest(CLUSTER_ID, event("run-a2", "COMPLETE", BASE_TIME.plusSeconds(30), "orders-a"));

        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT content_hash FROM t_ddh_data_job_definition ORDER BY version", String.class);
        assertThat(hashes).hasSize(3);
        assertThat(hashes.get(0)).isEqualTo(hashes.get(2)).isNotEqualTo(hashes.get(1));
        assertThat(currentOutput()).isEqualTo("paimon://prod/dwd/orders-a");
    }

    @Test
    void emptyUnresolvedAndFailedEventsAreSideLoggedWithoutStructureWrites() {
        LineageIngestService service = ingestService();

        var empty = event("empty", "START", BASE_TIME, List.of(), List.of(), null);
        assertThat(service.ingest(CLUSTER_ID, empty).status())
                .isEqualTo(LineageIngestService.Status.SKIPPED_EMPTY);

        var unresolved = event("unresolved", "COMPLETE", BASE_TIME.plusSeconds(1), "orders");
        ((com.fasterxml.jackson.databind.node.ObjectNode) unresolved.withArray("inputs").get(0))
                .put("namespace", "unconfirmed.namespace");
        assertThat(service.ingest(CLUSTER_ID, unresolved).status())
                .isEqualTo(LineageIngestService.Status.SKIPPED_UNRESOLVED);

        assertThat(service.ingest(CLUSTER_ID, event("failed", "FAIL", BASE_TIME.plusSeconds(2), "orders")).status())
                .isEqualTo(LineageIngestService.Status.IGNORED_EVENT);

        assertThat(count("t_ddh_lineage_event")).isEqualTo(3);
        assertThat(count("t_ddh_data_job")).isZero();
        assertThat(count("t_ddh_lineage_edge")).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM t_ddh_lineage_parse_log ORDER BY id", String.class))
                .containsExactly("SKIPPED_EMPTY", "UNRESOLVED_DATASET", "IGNORED_EVENT");
    }

    /**
     * L3/B2 验收：同一 {@code canonical_name} 在不同集群下必须各自建行、互不串号（P1 踩坑点——
     * 回查 node id 若不带 cluster_id 条件，会在跨集群同名表时静默拿到别的集群的 node id）。
     */
    @Test
    void sameCanonicalNameAcrossTwoClustersProducesTwoIndependentNodesAndGenerations() {
        long otherClusterId = CLUSTER_ID + 1;
        LineageIngestService service = ingestService();

        service.ingest(CLUSTER_ID, event("cluster-a-run", "COMPLETE", BASE_TIME, "orders"));
        service.ingest(otherClusterId, event("cluster-b-run", "COMPLETE", BASE_TIME, "orders"));

        List<Long> nodeIds = jdbcTemplate.queryForList(
                "SELECT id FROM t_ddh_lineage_node WHERE canonical_name = ? ORDER BY cluster_id",
                Long.class, "paimon://prod/dwd/orders");
        assertThat(nodeIds).hasSize(2);
        assertThat(nodeIds.get(0)).isNotEqualTo(nodeIds.get(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, CLUSTER_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, otherClusterId))
                .isEqualTo(1);

        // 各自再摄入一次结构不变的事件：generation 只应在自己集群内自增，不能互相影响。
        service.ingest(CLUSTER_ID, event("cluster-a-run-2", "COMPLETE", BASE_TIME.plusSeconds(10), "orders"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, CLUSTER_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, otherClusterId))
                .isEqualTo(1);
    }

    @Test
    void twentyConcurrentFirstEventsCreateOneJobAndOneCurrentVersion() throws Exception {
        Instant earlierSeenAt = Instant.EPOCH;
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_node
                            (cluster_id, connector, catalog_name, database_name, table_name, canonical_name,
                             dw_layer, first_seen, last_seen)
                        VALUES
                            (?, 'paimon', 'prod', 'ods', 'orders_raw', 'paimon://prod/ods/orders_raw',
                             'ODS', ?, ?),
                            (?, 'paimon', 'prod', 'dwd', 'orders', 'paimon://prod/dwd/orders',
                             'DWD', ?, ?)
                        """,
                CLUSTER_ID, Timestamp.from(earlierSeenAt), Timestamp.from(earlierSeenAt),
                CLUSTER_ID, Timestamp.from(earlierSeenAt), Timestamp.from(earlierSeenAt));

        LineageIngestService service = ingestService();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Callable<LineageIngestService.IngestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int index = i;
            tasks.add(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return service.ingest(CLUSTER_ID,
                        event("concurrent-" + index, "COMPLETE", BASE_TIME.plusSeconds(index + 1), "orders"));
            });
        }

        try {
            List<Future<LineageIngestService.IngestResult>> futures =
                    tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<LineageIngestService.IngestResult> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM t_ddh_data_job
                        WHERE cluster_id = ? AND engine = 'spark' AND job_name = 'daily-orders'
                        """,
                Integer.class, CLUSTER_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT definition_version) FROM t_ddh_lineage_edge WHERE is_current = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_ddh_lineage_node WHERE last_seen > ?",
                Integer.class, Timestamp.from(earlierSeenAt))).isEqualTo(2);
        Integer currentVersion = jdbcTemplate.queryForObject(
                "SELECT MIN(definition_version) FROM t_ddh_lineage_edge WHERE is_current = 1", Integer.class);
        Integer latestVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM t_ddh_data_job_definition", Integer.class);
        assertThat(currentVersion).isEqualTo(latestVersion);
    }

    private static int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static long generation() {
        return jdbcTemplate.queryForObject(
                "SELECT generation FROM t_ddh_lineage_generation WHERE cluster_id = ?", Long.class, CLUSTER_ID);
    }

    private static String currentOutput() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT n.canonical_name
                        FROM t_ddh_lineage_edge e
                        JOIN t_ddh_lineage_node n ON n.id = e.dst_node_id
                        WHERE e.is_current = 1
                        """,
                String.class);
    }
}
