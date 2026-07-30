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

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;

/**
 * 血缘快照 single-flight 重建协调器。
 *
 * <p>所有触发源只通过 {@link #requestRebuild(Trigger)} 置脏并异步投递。实际重建始终运行在
 * 独立单线程执行器中，不占用调用方、Tomcat 或 Spring Scheduler 线程。</p>
 *
 * <p>禁止使用 {@code Graphs.transitiveClosure()}：其 O(V·E) 复杂度和超级节点结果集会导致
 * 不可控的 CPU 与堆占用；查询侧必须使用有界分层 BFS。</p>
 */
public final class LineageRebuildCoordinator implements ApplicationRunner, AutoCloseable {

    static final int DEFAULT_MAX_DRAIN_ROUNDS = 8;
    static final Duration DEFAULT_MAX_DRAIN_DURATION = Duration.ofSeconds(2);

    private final LineageGraphSnapshotHolder snapshotHolder;
    private final SnapshotLoader snapshotLoader;
    private final TransactionTemplate readTransaction;
    private final RebuildMetrics metrics;
    private final Clock clock;
    private final ExecutorService rebuildExecutor;
    private final int maxDrainRounds;
    private final long maxDrainMillis;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable lastRebuildError;

    public LineageRebuildCoordinator(LineageGraphSnapshotHolder snapshotHolder, SnapshotLoader snapshotLoader,
                                     TransactionTemplate readTransaction) {
        this(snapshotHolder, snapshotLoader, readTransaction, RebuildMetrics.NOOP);
    }

    public LineageRebuildCoordinator(LineageGraphSnapshotHolder snapshotHolder, SnapshotLoader snapshotLoader,
                                     TransactionTemplate readTransaction, RebuildMetrics metrics) {
        this(snapshotHolder, snapshotLoader, readTransaction, metrics, Clock.systemUTC(), newRebuildExecutor(),
                DEFAULT_MAX_DRAIN_ROUNDS, DEFAULT_MAX_DRAIN_DURATION);
    }

    LineageRebuildCoordinator(LineageGraphSnapshotHolder snapshotHolder, SnapshotLoader snapshotLoader,
                              TransactionTemplate readTransaction, RebuildMetrics metrics, Clock clock,
                              ExecutorService rebuildExecutor, int maxDrainRounds, Duration maxDrainDuration) {
        this.snapshotHolder = Objects.requireNonNull(snapshotHolder, "snapshotHolder");
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
        this.readTransaction = Objects.requireNonNull(readTransaction, "readTransaction");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rebuildExecutor = Objects.requireNonNull(rebuildExecutor, "rebuildExecutor");
        if (maxDrainRounds <= 0) {
            throw new IllegalArgumentException("maxDrainRounds must be positive");
        }
        Objects.requireNonNull(maxDrainDuration, "maxDrainDuration");
        if (maxDrainDuration.isNegative() || maxDrainDuration.isZero()) {
            throw new IllegalArgumentException("maxDrainDuration must be positive");
        }
        this.maxDrainRounds = maxDrainRounds;
        this.maxDrainMillis = maxDrainDuration.toMillis();
    }

    @Override
    public void run(ApplicationArguments args) {
        requestRebuild(Trigger.STARTUP);
    }

    @Scheduled(fixedDelay = 3 * 60 * 1000)
    public void requestScheduledRebuild() {
        requestRebuild(Trigger.SCHEDULED);
    }

    /**
     * 三个触发源的唯一入口；只置脏并投递，立即返回。
     */
    public void requestRebuild(Trigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        if (closed.get()) {
            return;
        }
        pending.set(true);
        submitDrainIfIdle();
    }

    public Optional<Throwable> lastRebuildError() {
        return Optional.ofNullable(lastRebuildError);
    }

    boolean publishIfNotOlder(LineageGraphSnapshot next) {
        LineageGraphSnapshotHolder.PublishResult result = snapshotHolder.publishIfNotOlder(next);
        if (!result.published()) {
            metrics.staleRebuildDiscarded(next.generation(), result.currentGeneration());
            return false;
        }
        return true;
    }

    private void submitDrainIfIdle() {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            rebuildExecutor.execute(this::drainPending);
        } catch (RejectedExecutionException e) {
            inFlight.set(false);
            lastRebuildError = e;
            metrics.rebuildFailed(e);
        }
    }

    private void drainPending() {
        int rounds = 0;
        long startedAt = clock.millis();
        long deadline = startedAt + maxDrainMillis;
        try {
            while (!closed.get() && pending.compareAndSet(true, false)) {
                try {
                    doRebuild();
                } catch (Exception e) {
                    lastRebuildError = e;
                    metrics.rebuildFailed(e);
                    break;
                }
                lastRebuildError = null;
                rounds++;
                if (rounds >= maxDrainRounds || clock.millis() > deadline) {
                    pending.set(true);
                    metrics.drainYielded(rounds, clock.millis() - startedAt);
                    break;
                }
            }
        } finally {
            inFlight.set(false);
            if (!closed.get() && pending.get()) {
                submitDrainIfIdle();
            }
        }
    }

    private void doRebuild() throws Exception {
        LineageGraphSnapshot next;
        try {
            next = Objects.requireNonNull(
                    readTransaction.execute(status -> {
                        try {
                            return snapshotLoader.load();
                        } catch (Exception e) {
                            throw new SnapshotLoadException(e);
                        }
                    }),
                    "snapshotLoader returned null");
        } catch (SnapshotLoadException e) {
            throw (Exception) e.getCause();
        }
        publishIfNotOlder(next);
    }

    @Override
    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pending.set(false);
            rebuildExecutor.shutdownNow();
        }
    }

    private static ExecutorService newRebuildExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lineage-rebuild");
            thread.setDaemon(true);
            return thread;
        });
    }

    public enum Trigger {
        STARTUP,
        SCHEDULED,
        EVENT,
        MANUAL
    }

    /**
     * 一次权威快照读取边界。
     *
     * <p>实现必须在同一个只读 REPEATABLE READ 事务、同一连接中读取 generation、节点以及
     * 全部边分页，随后构建允许自环的不可变图。具体 JDBC/MyBatis 实现属于后续写路径批次。</p>
     */
    @FunctionalInterface
    public interface SnapshotLoader {
        LineageGraphSnapshot load() throws Exception;
    }

    public interface RebuildMetrics {

        RebuildMetrics NOOP = new RebuildMetrics() {
        };

        default void staleRebuildDiscarded(long discardedGeneration, long publishedGeneration) {
        }

        default void rebuildFailed(Throwable error) {
        }

        default void drainYielded(int completedRounds, long elapsedMillis) {
        }
    }

    private static final class SnapshotLoadException extends RuntimeException {

        private SnapshotLoadException(Exception cause) {
            super(cause);
        }
    }
}
