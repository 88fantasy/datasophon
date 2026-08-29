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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stores the resumable cursor and the per-period idempotency ledger for stream totals. */
@Repository
public class DsStreamMetricRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DsStreamMetricRepository(JdbcTemplate jdbcTemplate,
                                    PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public StreamMetricCursor register(Integer clusterId, String jobId, String jobName, Instant since) {
        Instant cursor = since.truncatedTo(ChronoUnit.MINUTES);
        jdbcTemplate.update("""
                INSERT IGNORE INTO t_ddh_ds_stream_metric_job
                    (cluster_id, job_id, job_name, since_time, cursor_time, processed_approx)
                VALUES (?, ?, ?, ?, ?, 0)
                """, clusterId, jobId, jobName, Timestamp.from(since), Timestamp.from(cursor));
        return find(clusterId, jobId).orElseThrow();
    }

    public Optional<StreamMetricCursor> find(Integer clusterId, String jobId) {
        return jdbcTemplate.query("""
                SELECT cluster_id, job_id, job_name, since_time, cursor_time, processed_approx
                FROM t_ddh_ds_stream_metric_job
                WHERE cluster_id = ? AND job_id = ?
                """, (rs, rowNum) -> mapCursor(rs.getInt("cluster_id"), rs.getString("job_id"),
                rs.getString("job_name"), rs.getTimestamp("since_time"),
                rs.getTimestamp("cursor_time"), rs.getLong("processed_approx")),
                clusterId, jobId).stream().findFirst();
    }

    public Optional<StreamMetricCursor> findLatestByJobNamePrefix(Integer clusterId, String jobNamePrefix) {
        return jdbcTemplate.query("""
                SELECT cluster_id, job_id, job_name, since_time, cursor_time, processed_approx
                FROM t_ddh_ds_stream_metric_job
                WHERE cluster_id = ? AND job_name LIKE ?
                ORDER BY since_time DESC, job_id DESC
                LIMIT 1
                """, (rs, rowNum) -> mapCursor(rs.getInt("cluster_id"), rs.getString("job_id"),
                rs.getString("job_name"), rs.getTimestamp("since_time"),
                rs.getTimestamp("cursor_time"), rs.getLong("processed_approx")),
                clusterId, jobNamePrefix + "%").stream().findFirst();
    }

    public List<StreamMetricCursor> findPending(Instant before, int limit) {
        return jdbcTemplate.query("""
                SELECT cluster_id, job_id, job_name, since_time, cursor_time, processed_approx
                FROM t_ddh_ds_stream_metric_job
                WHERE cursor_time < ?
                ORDER BY update_time, cursor_time
                LIMIT ?
                """, (rs, rowNum) -> mapCursor(rs.getInt("cluster_id"), rs.getString("job_id"),
                rs.getString("job_name"), rs.getTimestamp("since_time"),
                rs.getTimestamp("cursor_time"), rs.getLong("processed_approx")),
                Timestamp.from(before), limit);
    }

    public boolean accumulate(StreamMetricCursor cursor, Instant periodEnd, long delta) {
        Boolean applied = transactionTemplate.execute(status -> {
            int inserted = jdbcTemplate.update("""
                    INSERT IGNORE INTO t_ddh_ds_stream_metric_period
                        (cluster_id, job_id, period_start, period_end, delta_value)
                    VALUES (?, ?, ?, ?, ?)
                    """, cursor.clusterId(), cursor.jobId(), Timestamp.from(cursor.cursor()),
                    Timestamp.from(periodEnd), delta);
            if (inserted == 0) {
                return false;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE t_ddh_ds_stream_metric_job
                    SET cursor_time = ?, processed_approx = processed_approx + ?
                    WHERE cluster_id = ? AND job_id = ? AND cursor_time = ?
                    """, Timestamp.from(periodEnd), delta, cursor.clusterId(), cursor.jobId(),
                    Timestamp.from(cursor.cursor()));
            if (updated != 1) {
                throw new IllegalStateException("DS stream metric cursor changed concurrently");
            }
            return true;
        });
        return Boolean.TRUE.equals(applied);
    }

    /**
     * Advances the cursor past a window that stayed empty, without touching the running total.
     *
     * <p>没有增量可记，所以不写幂等台账；游标自身的 CAS 已经能防止并发重复推进。
     */
    public void skipEmptyPeriod(StreamMetricCursor cursor, Instant periodEnd) {
        jdbcTemplate.update("""
                UPDATE t_ddh_ds_stream_metric_job
                SET cursor_time = ?
                WHERE cluster_id = ? AND job_id = ? AND cursor_time = ?
                """, Timestamp.from(periodEnd), cursor.clusterId(), cursor.jobId(),
                Timestamp.from(cursor.cursor()));
    }

    /** Drops idempotency-ledger rows whose window closed before {@code before}. */
    public int purgePeriodsBefore(Instant before) {
        return jdbcTemplate.update("""
                DELETE FROM t_ddh_ds_stream_metric_period
                WHERE period_end < ?
                """, Timestamp.from(before));
    }

    public void markAttempted(StreamMetricCursor cursor) {
        jdbcTemplate.update("""
                UPDATE t_ddh_ds_stream_metric_job
                SET update_time = CURRENT_TIMESTAMP(3)
                WHERE cluster_id = ? AND job_id = ? AND cursor_time = ?
                """, cursor.clusterId(), cursor.jobId(), Timestamp.from(cursor.cursor()));
    }

    private static StreamMetricCursor mapCursor(int clusterId, String jobId, String jobName,
                                                Timestamp since, Timestamp cursor, long processedApprox) {
        return new StreamMetricCursor(clusterId, jobId, jobName, since.toInstant(), cursor.toInstant(),
                processedApprox);
    }

    public record StreamMetricCursor(Integer clusterId, String jobId, String jobName,
                                     Instant since, Instant cursor, long processedApprox) {
    }
}
