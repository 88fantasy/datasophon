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
    private static final Duration MAX_PERIOD = Duration.ofHours(1);
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

    public Optional<StreamMetricCursor> registerAndRead(Integer clusterId, String jobId, String jobName) {
        Optional<StreamMetricCursor> existing = repository.find(clusterId, jobId);
        if (existing.isPresent()) {
            return existing;
        }
        Optional<Instant> firstSample = queryService.queryFirstSampleAt(clusterId, METRIC, jobId, "sum");
        if (firstSample.isEmpty()) {
            return repository.find(clusterId, jobId);
        }
        return Optional.of(repository.register(clusterId, jobId, jobName, firstSample.get()));
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

    private void collectOne(StreamMetricCursor cursor, Instant completedThrough) {
        Instant periodEnd = cursor.cursor().plus(MAX_PERIOD);
        if (periodEnd.isAfter(completedThrough)) {
            periodEnd = completedThrough;
        }
        if (!periodEnd.isAfter(cursor.cursor())) {
            return;
        }
        DeltaSummary summary = queryService.queryDeltaSummary(
                cursor.clusterId(), METRIC, cursor.jobId(), cursor.cursor(), periodEnd, "sum");
        if (summary.sampleCount() == 0) {
            repository.markAttempted(cursor);
            return;
        }
        repository.accumulate(cursor, periodEnd, Math.round(summary.value()));
    }
}
