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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.ds.DsStreamMetricRepository.StreamMetricCursor;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.OtelMetricsQueryService.DeltaSummary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DsStreamMetricAccumulatorTest {

    private static final Instant START = Instant.parse("2026-08-25T00:00:05Z");
    private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DsStreamMetricRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ds_stream_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_ddh_ds_stream_metric_job (
                    cluster_id INT NOT NULL,
                    job_id VARCHAR(64) NOT NULL,
                    job_name VARCHAR(255) NOT NULL,
                    since_time DATETIME(3) NOT NULL,
                    cursor_time DATETIME(3) NOT NULL,
                    processed_approx BIGINT NOT NULL DEFAULT 0,
                    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (cluster_id, job_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ddh_ds_stream_metric_period (
                    cluster_id INT NOT NULL,
                    job_id VARCHAR(64) NOT NULL,
                    period_start DATETIME(3) NOT NULL,
                    period_end DATETIME(3) NOT NULL,
                    delta_value BIGINT NOT NULL,
                    PRIMARY KEY (cluster_id, job_id, period_start)
                )
                """);
        repository = newRepository();
        repository.register(7, JOB_ID, "ds-7-12-stream", START);
    }

    @Test
    void repeatingTheSamePeriodDoesNotAddTwice() {
        StreamMetricCursor cursor = repository.find(7, JOB_ID).orElseThrow();
        Instant end = Instant.parse("2026-08-25T01:00:00Z");

        assertThat(repository.accumulate(cursor, end, 60)).isTrue();
        assertThat(repository.accumulate(cursor, end, 60)).isFalse();
        assertThat(repository.find(7, JOB_ID).orElseThrow().processedApprox()).isEqualTo(60);
    }

    @Test
    void newAccumulatorContinuesFromPersistedCursorAfterRestart() {
        OtelMetricsQueryService queryService = mock(OtelMetricsQueryService.class);
        when(queryService.queryDeltaSummary(anyInt(), anyString(), anyString(), any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Instant start = invocation.getArgument(3);
                    return start.equals(Instant.parse("2026-08-25T00:00:00Z"))
                            ? new DeltaSummary(60, 6)
                            : new DeltaSummary(40, 4);
                });
        DsStreamMetricAccumulator first = new DsStreamMetricAccumulator(
                repository, queryService, Clock.fixed(Instant.parse("2026-08-25T01:00:00Z"), ZoneOffset.UTC));
        first.collectScheduled();

        DsStreamMetricRepository restartedRepository = newRepository();
        DsStreamMetricAccumulator restarted = new DsStreamMetricAccumulator(
                restartedRepository, queryService,
                Clock.fixed(Instant.parse("2026-08-25T02:00:00Z"), ZoneOffset.UTC));
        restarted.collectScheduled();

        StreamMetricCursor result = restartedRepository.find(7, JOB_ID).orElseThrow();
        assertThat(result.cursor()).isEqualTo(Instant.parse("2026-08-25T02:00:00Z"));
        assertThat(result.processedApprox()).isEqualTo(100);
    }

    @Test
    void emptyObservedPeriodYieldsWithoutRecordingAHealthyZero() {
        OtelMetricsQueryService queryService = mock(OtelMetricsQueryService.class);
        when(queryService.queryDeltaSummary(anyInt(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new DeltaSummary(0, 0));
        DsStreamMetricAccumulator accumulator = new DsStreamMetricAccumulator(
                repository, queryService, Clock.fixed(Instant.parse("2026-08-25T01:00:00Z"), ZoneOffset.UTC));

        accumulator.collectScheduled();

        StreamMetricCursor result = repository.find(7, JOB_ID).orElseThrow();
        assertThat(result.cursor()).isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
        assertThat(result.processedApprox()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_ddh_ds_stream_metric_period", Integer.class)).isZero();
    }

    @Test
    void recentlyProcessedBacklogYieldsToAnUnprocessedJobAtTheLimit() {
        String unprocessedJobId = "fedcba9876543210fedcba9876543210";
        repository.register(7, unprocessedJobId, "ds-7-13-stream", START.plusSeconds(1800));
        jdbcTemplate.update("""
                UPDATE t_ddh_ds_stream_metric_job
                SET update_time = CASE WHEN job_id = ? THEN ? ELSE ? END
                """, JOB_ID, "2026-08-25 00:00:00", "2026-08-25 00:01:00");
        repository.markAttempted(repository.find(7, JOB_ID).orElseThrow());

        assertThat(repository.findPending(Instant.parse("2026-08-25T03:00:00Z"), 1))
                .extracting(StreamMetricCursor::jobId)
                .containsExactly(unprocessedJobId);
    }

    private DsStreamMetricRepository newRepository() {
        return new DsStreamMetricRepository(
                new JdbcTemplate(dataSource), new DataSourceTransactionManager(dataSource));
    }
}
