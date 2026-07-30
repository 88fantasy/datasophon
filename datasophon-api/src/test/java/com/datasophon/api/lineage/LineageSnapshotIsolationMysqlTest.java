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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class LineageSnapshotIsolationMysqlTest extends LineageMysqlTestSupport {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(Isolation.class)
    void snapshotConsistencyDependsOnCoordinatorEnforcedIsolation(Isolation isolation) throws Exception {
        Fixture fixture = insertVersionOne();
        CountDownLatch firstPageRead = new CountDownLatch(1);
        CountDownLatch continuePaging = new CountDownLatch(1);
        AtomicBoolean firstPage = new AtomicBoolean(true);
        MysqlSnapshotLoader loader = new MysqlSnapshotLoader(jdbcTemplate, 1, FIXED_CLOCK, lastId -> {
            if (firstPage.compareAndSet(true, false)) {
                firstPageRead.countDown();
                try {
                    if (!continuePaging.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to continue edge paging");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        });
        TransactionTemplate readTransaction = new TransactionTemplate(transactionManager);
        readTransaction.setIsolationLevel(isolation.springIsolation());
        readTransaction.setReadOnly(true);
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

        try (
                LineageRebuildCoordinator coordinator =
                        new LineageRebuildCoordinator(holder, loader, readTransaction)) {
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.MANUAL);
            assertThat(firstPageRead.await(5, TimeUnit.SECONDS)).isTrue();
            flipToVersionTwo(fixture);
            continuePaging.countDown();

            await(() -> holder.getForQuery().isPresent());
            LineageGraphSnapshot snapshot = holder.getForQuery().orElseThrow();
            Set<Integer> versions = snapshot.graph().edges().stream()
                    .map(edge -> snapshot.graph().edgeValue(edge.nodeU(), edge.nodeV()).orElseThrow())
                    .flatMap(value -> value.jobRefs().stream())
                    .map(JobRef::definitionVersion)
                    .collect(Collectors.toSet());
            if (isolation == Isolation.REPEATABLE_READ) {
                assertThat(versions).containsExactly(1);
                assertThat(snapshot.meta().physicalEdgeCount()).isEqualTo(2);
            } else {
                assertThat(versions)
                        .as("READ COMMITTED must expose the deliberately constructed torn pagination")
                        .containsExactlyInAnyOrder(1, 2);
            }
        } finally {
            continuePaging.countDown();
        }
    }

    private static Fixture insertVersionOne() {
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_data_job
                            (cluster_id, engine, job_name, job_type, state, current_structural_hash, current_watermark)
                        VALUES (7, 'spark', 'snapshot-job', 'UNKNOWN', 'UNKNOWN', 'hash-v1', 1)
                        """);
        long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_ddh_data_job WHERE job_name = 'snapshot-job'", Long.class);
        long first = insertNode("first");
        long second = insertNode("second");
        long third = insertNode("third");
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_edge
                            (job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                        VALUES (?, 1, ?, ?, 'BATCH', 1), (?, 1, ?, ?, 'BATCH', 1)
                        """,
                jobId, first, second, jobId, second, third);
        jdbcTemplate.update("UPDATE t_ddh_lineage_generation SET generation = 1 WHERE id = 1");
        return new Fixture(jobId, first, second, third);
    }

    private static long insertNode(String table) {
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_node
                            (connector, catalog_name, database_name, table_name, canonical_name, first_seen, last_seen)
                        VALUES ('paimon', 'prod', 'dwd', ?, ?, NOW(3), NOW(3))
                        """,
                table, "paimon://prod/dwd/" + table);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_ddh_lineage_node WHERE canonical_name = ?",
                Long.class, "paimon://prod/dwd/" + table);
    }

    private static void flipToVersionTwo(Fixture fixture) {
        jdbcTemplate.update(
                "UPDATE t_ddh_lineage_edge SET is_current = 0 WHERE job_id = ?",
                fixture.jobId());
        jdbcTemplate.update(
                """
                        INSERT INTO t_ddh_lineage_edge
                            (job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                        VALUES (?, 2, ?, ?, 'BATCH', 1), (?, 2, ?, ?, 'BATCH', 1)
                        """,
                fixture.jobId(), fixture.firstNodeId(), fixture.thirdNodeId(),
                fixture.jobId(), fixture.thirdNodeId(), fixture.secondNodeId());
        jdbcTemplate.update("UPDATE t_ddh_lineage_generation SET generation = 2 WHERE id = 1");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    enum Isolation {
        REPEATABLE_READ(TransactionDefinition.ISOLATION_REPEATABLE_READ),
        READ_COMMITTED(TransactionDefinition.ISOLATION_READ_COMMITTED);

        private final int springIsolation;

        Isolation(int springIsolation) {
            this.springIsolation = springIsolation;
        }

        int springIsolation() {
            return springIsolation;
        }
    }

    private record Fixture(long jobId, long firstNodeId, long secondNodeId, long thirdNodeId) {
    }
}
