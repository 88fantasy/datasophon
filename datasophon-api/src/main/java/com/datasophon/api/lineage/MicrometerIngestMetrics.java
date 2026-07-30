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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Micrometer-backed counters and row-lock timing for the authoritative lineage write path. */
public final class MicrometerIngestMetrics implements IngestMetrics {

    static final String EVENT = "lineage.ingest.event";
    static final String STRUCTURE_CHANGE = "lineage.ingest.structure.change";
    static final String EDGE_ROWS_WRITTEN = "lineage.ingest.edge.rows.written";
    static final String LAST_SEEN_ROWS_UPDATED = "lineage.ingest.last.seen.rows.updated";
    static final String DEADLOCK = "lineage.ingest.deadlock";
    static final String LOCK_WAIT = "lineage.ingest.lock.wait";

    private final Counter event;
    private final Counter structureChange;
    private final Counter edgeRowsWritten;
    private final Counter lastSeenRowsUpdated;
    private final Counter deadlock;
    private final Timer lockWait;

    public MicrometerIngestMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        event = Counter.builder(EVENT).description("Accepted lineage events").register(registry);
        structureChange = Counter.builder(STRUCTURE_CHANGE)
                .description("Lineage events that changed job structure")
                .register(registry);
        edgeRowsWritten = Counter.builder(EDGE_ROWS_WRITTEN)
                .description("Physical lineage edge rows written")
                .register(registry);
        lastSeenRowsUpdated = Counter.builder(LAST_SEEN_ROWS_UPDATED)
                .description("Lineage node last-seen rows updated")
                .register(registry);
        deadlock = Counter.builder(DEADLOCK).description("Lineage ingest deadlock retries").register(registry);
        lockWait = Timer.builder(LOCK_WAIT)
                .description("Wait time for the t_ddh_data_job SELECT FOR UPDATE row lock")
                .publishPercentiles(0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public void eventTotal() {
        event.increment();
    }

    @Override
    public void structureChangeTotal() {
        structureChange.increment();
    }

    @Override
    public void edgeRowsWritten(long count) {
        increment(edgeRowsWritten, count);
    }

    @Override
    public void lastSeenRowsUpdated(long count) {
        increment(lastSeenRowsUpdated, count);
    }

    @Override
    public void deadlockRetry(int attempt, long backoffMillis) {
        deadlock.increment();
    }

    @Override
    public void lockWait(long waitNanos) {
        if (waitNanos >= 0) {
            lockWait.record(waitNanos, TimeUnit.NANOSECONDS);
        }
    }

    private static void increment(Counter counter, long count) {
        if (count > 0) {
            counter.increment(count);
        }
    }
}
