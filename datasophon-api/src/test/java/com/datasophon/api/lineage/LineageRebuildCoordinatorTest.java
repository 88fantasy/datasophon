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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

class LineageRebuildCoordinatorTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void coordinatorConcurrencyIsAlwaysOneAndConcurrentRequestsAreCoalesced() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch firstLoadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLoad = new CountDownLatch(1);
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

        try (LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(holder, () -> {
            int currentActive = active.incrementAndGet();
            maxActive.accumulateAndGet(currentActive, Math::max);
            int generation = loads.incrementAndGet();
            firstLoadStarted.countDown();
            if (generation == 1) {
                assertThat(releaseFirstLoad.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            }
            active.decrementAndGet();
            return snapshot(generation);
        })) {
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.MANUAL);
            assertThat(firstLoadStarted.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();

            for (int i = 0; i < 100; i++) {
                coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.EVENT);
            }
            releaseFirstLoad.countDown();

            await(() -> loads.get() >= 2 && active.get() == 0);
            assertThat(maxActive).hasValue(1);
            assertThat(loads.get()).isLessThan(100);
            assertThat(holder.getForQuery()).get().extracting(LineageGraphSnapshot::generation).isEqualTo(2L);
        }
    }

    @Test
    void publishIfNewerDiscardsOlderGenerationAndIncrementsMetric() {
        AtomicInteger discarded = new AtomicInteger();
        AtomicReference<Long> discardedGeneration = new AtomicReference<>();
        LineageRebuildCoordinator.RebuildMetrics metrics = new LineageRebuildCoordinator.RebuildMetrics() {
            @Override
            public void staleRebuildDiscarded(long nextGeneration, long publishedGeneration) {
                discarded.incrementAndGet();
                discardedGeneration.set(nextGeneration);
            }
        };
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

        try (
                LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(holder, () -> snapshot(0),
                        metrics)) {
            assertThat(coordinator.publishIfNewer(snapshot(11))).isTrue();
            assertThat(coordinator.publishIfNewer(snapshot(10))).isFalse();

            assertThat(discarded).hasValue(1);
            assertThat(discardedGeneration).hasValue(10L);
            assertThat(holder.getForQuery()).get().extracting(LineageGraphSnapshot::generation).isEqualTo(11L);
        }
    }

    @Test
    void continuousPendingYieldsAtDrainBudgetAndIsResubmitted() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger yielded = new AtomicInteger();
        AtomicReference<LineageRebuildCoordinator> coordinatorRef = new AtomicReference<>();
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LineageRebuildCoordinator.RebuildMetrics metrics = new LineageRebuildCoordinator.RebuildMetrics() {
            @Override
            public void drainYielded(int completedRounds, long elapsedMillis) {
                yielded.incrementAndGet();
            }
        };

        try (LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(holder, () -> {
            int generation = loads.incrementAndGet();
            if (generation < 6) {
                coordinatorRef.get().requestRebuild(LineageRebuildCoordinator.Trigger.EVENT);
            }
            return snapshot(generation);
        }, metrics, FIXED_CLOCK, executor, 2, Duration.ofSeconds(1))) {
            coordinatorRef.set(coordinator);
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.STARTUP);

            await(() -> holder.getForQuery().map(LineageGraphSnapshot::generation).orElse(-1L) >= 6);
            assertThat(yielded.get()).isGreaterThanOrEqualTo(2);
            assertThat(loads.get()).isGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void continuousPendingAlsoYieldsAtWallClockBudget() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger yielded = new AtomicInteger();
        AtomicLong currentMillis = new AtomicLong();
        AtomicReference<LineageRebuildCoordinator> coordinatorRef = new AtomicReference<>();
        Clock advancingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(currentMillis.get());
            }

            @Override
            public long millis() {
                return currentMillis.get();
            }
        };
        LineageRebuildCoordinator.RebuildMetrics metrics = new LineageRebuildCoordinator.RebuildMetrics() {
            @Override
            public void drainYielded(int completedRounds, long elapsedMillis) {
                yielded.incrementAndGet();
            }
        };

        try (
                LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(
                        new LineageGraphSnapshotHolder(), () -> {
                            int generation = loads.incrementAndGet();
                            if (generation < 3) {
                                coordinatorRef.get().requestRebuild(LineageRebuildCoordinator.Trigger.EVENT);
                            }
                            currentMillis.addAndGet(101);
                            return snapshot(generation);
                        }, metrics, advancingClock, Executors.newSingleThreadExecutor(), 100, Duration.ofMillis(100))) {
            coordinatorRef.set(coordinator);
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.STARTUP);

            await(() -> loads.get() >= 3);
            assertThat(yielded.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void failedRebuildRecordsErrorAndPendingRetryRecovers() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch firstLoadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLoad = new CountDownLatch(1);
        CountDownLatch retryStarted = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();
        LineageRebuildCoordinator.RebuildMetrics metrics = new LineageRebuildCoordinator.RebuildMetrics() {
            @Override
            public void rebuildFailed(Throwable error) {
                failures.incrementAndGet();
            }
        };

        try (LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(holder, () -> {
            if (loads.incrementAndGet() == 1) {
                firstLoadStarted.countDown();
                assertThat(releaseFirstLoad.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
                throw new IllegalStateException("injected rebuild failure");
            }
            retryStarted.countDown();
            assertThat(releaseRetry.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            return snapshot(2);
        }, metrics)) {
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.EVENT);
            assertThat(firstLoadStarted.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.SCHEDULED);
            releaseFirstLoad.countDown();
            assertThat(retryStarted.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(coordinator.lastRebuildError()).get()
                    .extracting(Throwable::getMessage)
                    .isEqualTo("injected rebuild failure");

            releaseRetry.countDown();
            await(() -> holder.getForQuery().isPresent());
            assertThat(failures).hasValue(1);
            assertThat(coordinator.lastRebuildError()).isEmpty();
            assertThat(holder.getForQuery()).get().extracting(LineageGraphSnapshot::generation).isEqualTo(2L);
        }
    }

    @Test
    void repeatableReadLoaderDoesNotMixVersionsWhenCurrentEdgesFlipBetweenPages() throws Exception {
        List<EdgeRow> versionOne = List.of(new EdgeRow(1, 2, 1, 101), new EdgeRow(2, 3, 1, 102));
        List<EdgeRow> versionTwo = List.of(new EdgeRow(1, 3, 2, 201), new EdgeRow(3, 2, 2, 202));
        AtomicReference<List<EdgeRow>> currentRows = new AtomicReference<>(versionOne);
        CountDownLatch firstPageRead = new CountDownLatch(1);
        CountDownLatch allowSecondPage = new CountDownLatch(1);
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();

        LineageRebuildCoordinator.SnapshotLoader repeatableReadLoader = () -> {
            List<EdgeRow> readView = List.copyOf(currentRows.get());
            List<EdgeRow> rows = new ArrayList<>();
            rows.add(readView.get(0));
            firstPageRead.countDown();
            assertThat(allowSecondPage.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            rows.add(readView.get(1));
            return snapshotFromRows(1, rows);
        };

        try (LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(holder, repeatableReadLoader)) {
            coordinator.requestRebuild(LineageRebuildCoordinator.Trigger.MANUAL);
            assertThat(firstPageRead.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            currentRows.set(versionTwo);
            allowSecondPage.countDown();

            await(() -> holder.getForQuery().isPresent());
            LineageGraphSnapshot snapshot = holder.getForQuery().orElseThrow();
            Set<Integer> versions = snapshot.graph().edges().stream()
                    .map(edge -> snapshot.graph().edgeValue(edge.nodeU(), edge.nodeV()).orElseThrow())
                    .flatMap(value -> value.jobRefs().stream())
                    .map(JobRef::definitionVersion)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(versions).containsExactly(1);
            assertThat(snapshot.meta().physicalEdgeCount()).isEqualTo(2);
        }
    }

    @Test
    void holderReturnsGenerationAndGraphFromTheSamePublishedReference() throws Exception {
        LineageGraphSnapshotHolder holder = new LineageGraphSnapshotHolder();
        CopyOnWriteArrayList<String> mismatches = new CopyOnWriteArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread reader = new Thread(() -> {
            while (running.get()) {
                holder.getForQuery().ifPresent(snapshot -> {
                    long expectedNodeId = snapshot.generation() + 1;
                    if (!snapshot.graph().nodes().contains(expectedNodeId)) {
                        mismatches.add(snapshot.generation() + ":" + snapshot.graph().nodes());
                    }
                });
            }
        });
        reader.start();

        for (int generation = 0; generation < 1000; generation++) {
            holder.publishIfNewer(snapshot(generation));
        }
        running.set(false);
        reader.join(TEST_TIMEOUT.toMillis());

        assertThat(reader.isAlive()).isFalse();
        assertThat(mismatches).isEmpty();
    }

    private static LineageGraphSnapshot snapshot(long generation) {
        long nodeId = generation + 1;
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        graph.addNode(nodeId);
        return LineageGraphSnapshot.copyOf(graph, Map.of(nodeId, node(nodeId)), generation, FIXED_CLOCK.instant());
    }

    private static LineageGraphSnapshot snapshotFromRows(long generation, List<EdgeRow> rows) {
        MutableValueGraph<Long, EdgeValue> graph = ValueGraphBuilder.<Long, EdgeValue>directed()
                .allowsSelfLoops(true)
                .build();
        Map<Long, NodeMeta> nodes = Map.of(
                1L, node(1),
                2L, node(2),
                3L, node(3));
        nodes.keySet().forEach(graph::addNode);
        for (EdgeRow row : rows) {
            graph.putEdgeValue(row.src(), row.dst(),
                    new EdgeValue(List.of(new JobRef(1, row.edgeId(), row.definitionVersion(), "BATCH"))));
        }
        return LineageGraphSnapshot.copyOf(graph, nodes, generation, FIXED_CLOCK.instant());
    }

    private static NodeMeta node(long id) {
        return new NodeMeta(id, "paimon", "prod", "dwd", "table_" + id, "paimon://prod/dwd/table_" + id, "DWD");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TEST_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private record EdgeRow(long src, long dst, int definitionVersion, long edgeId) {
    }
}
