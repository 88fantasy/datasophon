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
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Micrometer-backed timers and result metrics for full lineage snapshot rebuilds. */
public final class MicrometerRebuildMetrics implements LineageRebuildCoordinator.RebuildMetrics {

    static final String DB_READ = "lineage.rebuild.db.read";
    static final String MAPPING = "lineage.rebuild.mapping";
    static final String GRAPH_BUILD = "lineage.rebuild.graph.build";
    static final String SNAPSHOT_COPY = "lineage.rebuild.snapshot.copy";
    static final String CYCLE_CHECK = "lineage.rebuild.cycle.check";
    static final String PUBLISH = "lineage.rebuild.publish";
    static final String STALE_DISCARDED = "lineage.rebuild.stale.discarded";
    static final String FAILED = "lineage.rebuild.failed";
    static final String LAST_ERROR = "lineage.rebuild.last.error";
    static final String DRAIN_YIELDED = "lineage.rebuild.drain.yielded";

    private final Timer dbRead;
    private final Timer mapping;
    private final Timer graphBuild;
    private final Timer snapshotCopy;
    private final Timer cycleCheck;
    private final Timer publish;
    private final Counter staleDiscarded;
    private final Counter failed;
    private final Counter drainYielded;
    private final AtomicInteger lastError = new AtomicInteger();

    public MicrometerRebuildMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        dbRead = timer(registry, DB_READ, "Lineage snapshot database read time excluding row mapping");
        mapping = timer(registry, MAPPING, "Lineage snapshot JDBC row mapping time");
        graphBuild = timer(registry, GRAPH_BUILD, "Lineage mutable graph construction time");
        snapshotCopy = timer(registry, SNAPSHOT_COPY, "Lineage immutable snapshot copy time excluding cycle check");
        cycleCheck = timer(registry, CYCLE_CHECK, "Lineage non-trivial cycle check time");
        publish = timer(registry, PUBLISH, "Lineage snapshot publication time");
        staleDiscarded = Counter.builder(STALE_DISCARDED)
                .description("Older lineage snapshots discarded at publication")
                .register(registry);
        failed = Counter.builder(FAILED).description("Failed lineage snapshot rebuilds").register(registry);
        drainYielded = Counter.builder(DRAIN_YIELDED)
                .description("Lineage rebuild drains yielded after reaching their budget")
                .register(registry);
        Gauge.builder(LAST_ERROR, lastError, AtomicInteger::get)
                .description("Whether the last lineage rebuild has an unresolved error")
                .register(registry);
    }

    @Override
    public void staleRebuildDiscarded(long discardedGeneration, long publishedGeneration) {
        staleDiscarded.increment();
    }

    @Override
    public void rebuildFailed(Throwable error) {
        failed.increment();
        lastError.set(1);
    }

    @Override
    public void drainYielded(int completedRounds, long elapsedMillis) {
        drainYielded.increment();
    }

    @Override
    public void dbRead(long elapsedNanos) {
        record(dbRead, elapsedNanos);
    }

    @Override
    public void mapping(long elapsedNanos) {
        record(mapping, elapsedNanos);
    }

    @Override
    public void graphBuild(long elapsedNanos) {
        record(graphBuild, elapsedNanos);
    }

    @Override
    public void snapshotCopy(long elapsedNanos) {
        record(snapshotCopy, elapsedNanos);
    }

    @Override
    public void cycleCheck(long elapsedNanos) {
        record(cycleCheck, elapsedNanos);
    }

    @Override
    public void publish(long elapsedNanos) {
        record(publish, elapsedNanos);
    }

    @Override
    public void rebuildSucceeded() {
        lastError.set(0);
    }

    private static Timer timer(MeterRegistry registry, String name, String description) {
        return Timer.builder(name).description(description).register(registry);
    }

    private static void record(Timer timer, long elapsedNanos) {
        if (elapsedNanos >= 0) {
            timer.record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }
}
