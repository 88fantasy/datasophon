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

package com.datasophon.api.ds;

import com.datasophon.api.ds.DsStreamMetricRepository.StreamMetricCursor;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.OtelMetricsQueryService.DeltaSummary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/** Periodically rebuilds an approximate processed-row total from OTLP delta samples. */
@Slf4j
@Service
public class DsStreamMetricAccumulator {

    static final String METRIC = "flink.taskmanager.job.task.operator.numRecordsOut";
    static final String SINK_OPERATOR_REGEX = ".*(Writer|Committer).*";
    private static final Duration MAX_PERIOD = Duration.ofHours(1);
    // 满窗口查不到样本时，再等这么久才判定它确实为空并推进游标：既给迟到的采集样本留余量，
    // 又保证一个再也不会变大的空窗口不会把游标永久钉死（作业停机 / 采集断流超过 MAX_PERIOD 时会发生）。
    private static final Duration EMPTY_WINDOW_GRACE = Duration.ofMinutes(10);
    private static final Duration LEDGER_RETENTION = Duration.ofDays(30);
    private static final int MAX_JOBS_PER_RUN = 64;

    private final DsStreamMetricRepository repository;
    private final OtelMetricsQueryService queryService;
    private final Clock clock;

    @Autowired
    public DsStreamMetricAccumulator(DsStreamMetricRepository repository,
                                     OtelMetricsQueryService queryService) {
        this(repository, queryService, Clock.systemUTC());
    }

    DsStreamMetricAccumulator(DsStreamMetricRepository repository,
                              OtelMetricsQueryService queryService,
                              Clock clock) {
        this.repository = repository;
        this.queryService = queryService;
        this.clock = clock;
    }

    /**
     * Registers a job for accumulation and returns its cursor.
     *
     * <p>累加算法建立在 OTLP delta 样本之上（窗口内 SUM 即该窗口的增量）。Flink 自带 Prometheus
     * reporter 导出的是累积计数器且被标成 gauge，对它做 SUM 没有意义，因此这类来源不建游标、
     * 不展示累计值——这是已知取舍，不是查询失败，所以显式记一条日志而不是静默返回空。
     *
     * @param deltaSamples 采样来源是否为 delta 语义，取自 {@code DsStreamMetricsProvider} 选中的指标
     */
    public Optional<StreamMetricCursor> registerAndRead(Integer clusterId, String jobId, String jobName,
                                                        boolean deltaSamples) {
        Optional<StreamMetricCursor> existing = repository.find(clusterId, jobId);
        if (existing.isPresent()) {
            return existing;
        }
        if (!deltaSamples) {
            log.debug("Not accumulating a running total for cluster {} job {}: "
                    + "the selected metric source exports cumulative gauges, not OTLP deltas",
                    clusterId, jobId);
            return Optional.empty();
        }
        Optional<Instant> firstSample = queryService.queryFirstSampleAt(clusterId, METRIC, jobId, "sum");
        if (firstSample.isEmpty()) {
            return repository.find(clusterId, jobId);
        }
        return Optional.of(repository.register(clusterId, jobId, jobName, firstSample.get()));
    }

    public boolean hasRegisteredJob(Integer clusterId, String jobNamePrefix) {
        return repository.findLatestByJobNamePrefix(clusterId, jobNamePrefix).isPresent();
    }

    @Scheduled(initialDelayString = "${datasophon.ds.stream-metric.initial-delay-ms:60000}", fixedDelayString = "${datasophon.ds.stream-metric.interval-ms:60000}")
    public void collectScheduled() {
        collectUntil(clock.instant());
    }

    void collectUntil(Instant now) {
        Instant completedThrough = now.truncatedTo(ChronoUnit.MINUTES);
        for (StreamMetricCursor cursor : repository.findPending(completedThrough, MAX_JOBS_PER_RUN)) {
            try {
                collectOne(cursor, completedThrough);
            } catch (RuntimeException e) {
                log.warn("Failed to accumulate DS stream metric for cluster {} job {}: {}",
                        cursor.clusterId(), cursor.jobId(), e.getMessage());
            }
        }
    }

    /** Bounds the idempotency ledger; the running total itself lives on the job row and is never purged. */
    @Scheduled(initialDelayString = "${datasophon.ds.stream-metric.purge-initial-delay-ms:300000}", fixedDelayString = "${datasophon.ds.stream-metric.purge-interval-ms:86400000}")
    public void purgeScheduled() {
        purgeBefore(clock.instant().minus(LEDGER_RETENTION));
    }

    void purgeBefore(Instant before) {
        int deleted = repository.purgePeriodsBefore(before);
        if (deleted > 0) {
            log.info("Purged {} DS stream metric ledger rows older than {}", deleted, before);
        }
    }

    private void collectOne(StreamMetricCursor cursor, Instant completedThrough) {
        Instant periodEnd = cursor.cursor().plus(MAX_PERIOD);
        if (periodEnd.isAfter(completedThrough)) {
            periodEnd = completedThrough;
        }
        if (!periodEnd.isAfter(cursor.cursor())) {
            return;
        }
        DeltaSummary summary = queryService.queryDeltaSummary(
                cursor.clusterId(), METRIC, cursor.jobId(), cursor.cursor(), periodEnd,
                java.util.Map.of("operator_name", SINK_OPERATOR_REGEX), "sum");
        if (summary.sampleCount() == 0) {
            // 窗口还能继续变大时保持游标不动，让迟到的样本仍有机会被下一轮框住；
            // 窗口已达 MAX_PERIOD 且过了宽限期仍然为空，就判定这段确实没有数据并跳过，
            // 否则游标会永久停在这个再也不会变化的空窗口上，累计值从此不再更新。
            if (completedThrough.isAfter(periodEnd.plus(EMPTY_WINDOW_GRACE))) {
                repository.skipEmptyPeriod(cursor, periodEnd);
            } else {
                repository.markAttempted(cursor);
            }
            return;
        }
        repository.accumulate(cursor, periodEnd, Math.round(summary.value()));
    }
}
