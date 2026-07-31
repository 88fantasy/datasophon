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
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
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
public final class LineageRebuildCoordinator implements ApplicationRunner, Ordered, AutoCloseable {

    static final int DEFAULT_MAX_DRAIN_ROUNDS = 8;
    static final Duration DEFAULT_MAX_DRAIN_DURATION = Duration.ofSeconds(2);
    static final int STARTUP_ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    private final LineageGraphSnapshotHolder snapshotHolder;
    private final SnapshotLoader snapshotLoader;
    private final TransactionTemplate readTransaction;
    private final RebuildMetrics metrics;
    private final Clock clock;
    private final ExecutorService rebuildExecutor;
    private final int maxDrainRounds;
    private final long maxDrainMillis;
    /** 待重建的集群集合；替代分片前的单个 pending 标记（L3/D5）。 */
    private final Set<Long> dirtyClusters = ConcurrentHashMap.newKeySet();
    /**
     * 全局在途标记，**刻意不按集群分**：重建执行器是单线程，且租约是全局单实例（L3/D6），
     * 按集群拆分 inFlight 只会让 drain 的收敛与 yield 逻辑复杂化而不带来并行度。
     */
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ConcurrentMap<Long, Throwable> lastRebuildErrors = new ConcurrentHashMap<>();

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
        requestRebuildForAllClusters(Trigger.STARTUP);
    }

    @Override
    public int getOrder() {
        return STARTUP_ORDER;
    }

    @Scheduled(fixedDelay = 3 * 60 * 1000)
    public void requestScheduledRebuild() {
        requestRebuildForAllClusters(Trigger.SCHEDULED);
    }

    /**
     * 四个触发源的唯一入口；只把集群置脏并投递，立即返回。
     */
    public void requestRebuild(long clusterId, Trigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        if (closed.get()) {
            return;
        }
        dirtyClusters.add(clusterId);
        submitDrainIfIdle();
    }

    /**
     * STARTUP / SCHEDULED 场景：没有具体的触发集群，需要枚举全部已知集群逐一置脏。
     *
     * <p>枚举失败不能让启动或定时任务挂掉，只记录为错误指标。</p>
     */
    public void requestRebuildForAllClusters(Trigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        if (closed.get()) {
            return;
        }
        Collection<Long> clusterIds;
        try {
            clusterIds = snapshotLoader.knownClusterIds();
        } catch (Exception e) {
            metrics.rebuildFailed(e);
            return;
        }
        if (clusterIds.isEmpty()) {
            return;
        }
        dirtyClusters.addAll(clusterIds);
        submitDrainIfIdle();
    }

    public Optional<Throwable> lastRebuildError(long clusterId) {
        return Optional.ofNullable(lastRebuildErrors.get(clusterId));
    }

    /** 返回指定集群当前已发布快照代际；尚未发布时为 {@code -1}。 */
    public long currentGeneration(long clusterId) {
        return snapshotHolder.currentGeneration(clusterId);
    }

    boolean publishIfNotOlder(long clusterId, LineageGraphSnapshot next) {
        long startedAt = System.nanoTime();
        LineageGraphSnapshotHolder.PublishResult result;
        try {
            result = snapshotHolder.publishIfNotOlder(clusterId, next);
        } finally {
            metrics.publish(System.nanoTime() - startedAt);
        }
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
            // 执行器拒绝是全局性失败，没有单一集群归属：当前全部待重建集群的重建都落空了，
            // 逐个记录，查询侧才能在对应集群上看到 lastRebuildError。
            dirtyClusters.forEach(clusterId -> lastRebuildErrors.put(clusterId, e));
            metrics.rebuildFailed(e);
        }
    }

    private void drainPending() {
        int rounds = 0;
        long startedAt = clock.millis();
        long deadline = startedAt + maxDrainMillis;
        try {
            Long clusterId;
            while (!closed.get() && (clusterId = pollDirtyCluster()) != null) {
                try {
                    doRebuild(clusterId);
                    lastRebuildErrors.remove(clusterId);
                    metrics.rebuildSucceeded();
                } catch (Exception e) {
                    // 单个集群重建失败不阻断其余集群：记录该集群的错误后继续 drain。
                    lastRebuildErrors.put(clusterId, e);
                    metrics.rebuildFailed(e);
                }
                // 成功与失败都必须计入收敛预算，否则大量集群持续失败时 drain 会长跑不 yield，
                // 独占重建线程。
                rounds++;
                if (rounds >= maxDrainRounds || clock.millis() > deadline) {
                    metrics.drainYielded(rounds, clock.millis() - startedAt);
                    break;
                }
            }
        } finally {
            inFlight.set(false);
            if (!closed.get() && !dirtyClusters.isEmpty()) {
                submitDrainIfIdle();
            }
        }
    }

    /** 取出并清除一个待重建集群；无待重建集群时返回 {@code null}。 */
    private Long pollDirtyCluster() {
        Iterator<Long> iterator = dirtyClusters.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Long clusterId = iterator.next();
        dirtyClusters.remove(clusterId);
        return clusterId;
    }

    private void doRebuild(long clusterId) throws Exception {
        LineageGraphSnapshot next;
        try {
            next = Objects.requireNonNull(
                    readTransaction.execute(status -> {
                        try {
                            return snapshotLoader.load(clusterId);
                        } catch (Exception e) {
                            throw new SnapshotLoadException(e);
                        }
                    }),
                    "snapshotLoader returned null");
        } catch (SnapshotLoadException e) {
            throw (Exception) e.getCause();
        }
        publishIfNotOlder(clusterId, next);
    }

    @Override
    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            dirtyClusters.clear();
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
     * 全部边分页，随后构建允许自环的不可变图。</p>
     *
     * <p>快照按集群分片后（L3/D5），本接口同时承担「有哪些集群需要重建」的枚举职责 ——
     * 它已经是权威读取边界，再引入独立的 ClusterIdSource 只会让装配多一个 Bean。
     * 因此它不再是 {@code @FunctionalInterface}。</p>
     */
    public interface SnapshotLoader {

        LineageGraphSnapshot load(long clusterId) throws Exception;

        /** 枚举当前有血缘作业的集群；STARTUP / SCHEDULED 触发时据此逐一置脏。 */
        Collection<Long> knownClusterIds() throws Exception;
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

        default void dbRead(long elapsedNanos) {
        }

        default void mapping(long elapsedNanos) {
        }

        default void graphBuild(long elapsedNanos) {
        }

        default void snapshotCopy(long elapsedNanos) {
        }

        default void cycleCheck(long elapsedNanos) {
        }

        default void publish(long elapsedNanos) {
        }

        default void rebuildSucceeded() {
        }
    }

    private static final class SnapshotLoadException extends RuntimeException {

        private SnapshotLoadException(Exception cause) {
            super(cause);
        }
    }
}
