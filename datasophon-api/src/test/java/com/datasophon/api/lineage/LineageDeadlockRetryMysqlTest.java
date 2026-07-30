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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LineageDeadlockRetryMysqlTest extends LineageMysqlTestSupport {

    @Test
    void inverseNodeUpdatesDeadlockAndWholeTransactionRetryRecovers() throws Exception {
        long firstNodeId = insertNode("shared-a");
        long secondNodeId = insertNode("shared-b");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LineageIngestService service = ingestService(new MicrometerIngestMetrics(registry));
        CountDownLatch firstLocksAcquired = new CountDownLatch(2);
        AtomicInteger leftAttempts = new AtomicInteger();
        AtomicInteger rightAttempts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> left = executor.submit(() -> updateInOppositeOrder(service, firstNodeId, secondNodeId, leftAttempts, firstLocksAcquired));
            Future<Boolean> right = executor.submit(() -> updateInOppositeOrder(service, secondNodeId, firstNodeId, rightAttempts, firstLocksAcquired));

            assertThat(left.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(right.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(registry.get("lineage.ingest.deadlock").counter().count()).isEqualTo(1);
        assertThat(leftAttempts.get() + rightAttempts.get()).isGreaterThanOrEqualTo(3);
        registry.close();
    }

    private static boolean updateInOppositeOrder(
                                                 LineageIngestService service,
                                                 long firstNodeId,
                                                 long secondNodeId,
                                                 AtomicInteger attempts,
                                                 CountDownLatch firstLocksAcquired) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return service.executeWithDeadlockRetry(() -> transaction.execute(status -> {
            int attempt = attempts.incrementAndGet();
            updateNode(firstNodeId);
            if (attempt == 1) {
                firstLocksAcquired.countDown();
                try {
                    if (!firstLocksAcquired.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out constructing the MySQL deadlock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            updateNode(secondNodeId);
            return true;
        }));
    }

    private static void updateNode(long nodeId) {
        jdbcTemplate.update(
                "UPDATE t_ddh_lineage_node SET last_seen = GREATEST(last_seen, NOW(3)) WHERE id = ?",
                nodeId);
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
}
