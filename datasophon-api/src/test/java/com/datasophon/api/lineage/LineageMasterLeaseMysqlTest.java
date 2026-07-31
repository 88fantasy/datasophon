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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasophon.api.controller.v2.LineageV2Controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class LineageMasterLeaseMysqlTest extends LineageMysqlTestSupport {

    private static final long CLUSTER_ID = 7;
    private static final Instant BASE_TIME = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void secondMasterIsRejectedAndTakesOverAfterOwnerCloses() {
        Duration manualHeartbeat = Duration.ofHours(1);
        AtomicBoolean ingestCalled = new AtomicBoolean();
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

        try (
                LineageMasterLease first =
                        new LineageMasterLease(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD, true, manualHeartbeat);
                LineageMasterLease second =
                        new LineageMasterLease(MYSQL_URL, MYSQL_USERNAME, MYSQL_PASSWORD, true, manualHeartbeat);
                LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(
                        holder,
                        new LineageRebuildCoordinator.SnapshotLoader() {

                            @Override
                            public LineageGraphSnapshot load(long clusterId) {
                                throw new UnsupportedOperationException("not used by this lease test");
                            }

                            @Override
                            public Collection<Long> knownClusterIds() {
                                return List.of();
                            }
                        },
                        new TransactionTemplate(transactionManager))) {
            first.start();
            second.start();

            assertThat(first.isOwner()).isTrue();
            assertThat(second.isOwner()).isFalse();

            String ingestToken = "test-lineage-token";
            LineageV2Controller controller = new LineageV2Controller(
                    (clusterId, payload) -> {
                        ingestCalled.set(true);
                        return LineageIngestService.IngestResult.of(LineageIngestService.Status.CHANGED);
                    },
                    new LineageLeaseGuard(second),
                    holder,
                    coordinator,
                    new LineageGenerationReader(jdbcTemplate),
                    new LineageGraphQuery(),
                    new LineageJobDetailReader(jdbcTemplate),
                    600,
                    1800,
                    ingestToken);

            assertThatThrownBy(() -> controller.ingest("Bearer " + ingestToken, CLUSTER_ID, event(
                    "blocked-second-master", "COMPLETE", BASE_TIME, "orders")))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.getReason()).isEqualTo(LineageLeaseGuard.UNAVAILABLE_MESSAGE);
                    });
            assertThat(ingestCalled).isFalse();

            first.close();
            second.heartbeat();

            assertThat(second.isOwner()).isTrue();
        }
    }
}
