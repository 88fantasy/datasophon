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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 查询侧读取作业台账详情。
 *
 * <p>作业元数据（名称、负责人、外部地址、状态）刻意不放进内存快照：{@code owner} / {@code state}
 * 这类字段会独立于血缘结构变化，装进快照会被代际冻住，展示出陈旧状态（L3/D8）。</p>
 */
public final class LineageJobDetailReader {

    private final JdbcTemplate jdbcTemplate;

    public LineageJobDetailReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * 读取指定集群下的作业详情。
     *
     * <p>{@code cluster_id} 是查询条件而不是返回后再校验：跨集群的 jobId 直接查不到，
     * 调用方拿到的空值语义等同于「不存在」，不泄露其他集群作业的存在性。</p>
     */
    public Optional<JobDetail> read(long clusterId, long jobId) {
        List<JobDetail> rows = jdbcTemplate.query(
                """
                        SELECT id, cluster_id, job_name, engine, job_type, dw_layer, owner,
                               external_url, state, update_time
                        FROM t_ddh_data_job
                        WHERE cluster_id = ? AND id = ?
                        """,
                (resultSet, rowNumber) -> new JobDetail(
                        resultSet.getLong("id"),
                        resultSet.getLong("cluster_id"),
                        resultSet.getString("job_name"),
                        resultSet.getString("engine"),
                        resultSet.getString("job_type"),
                        resultSet.getString("dw_layer"),
                        resultSet.getString("owner"),
                        resultSet.getString("external_url"),
                        resultSet.getString("state"),
                        toInstant(resultSet.getTimestamp("update_time"))),
                clusterId, jobId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 读取该集群最近一次收到血缘事件的时间。
     *
     * <p><strong>已知口径限制</strong>：{@code t_ddh_lineage_event} 没有 {@code cluster_id}，
     * 只能经 {@code job_id} 关联作业台账过滤。因此解析失败、尚未认领到作业的事件
     * （{@code job_id IS NULL}）统计不到 —— 这类事件本来也没有产生任何血缘结构。</p>
     */
    public Optional<Instant> readLastEventReceivedAt(long clusterId) {
        List<Timestamp> rows = jdbcTemplate.query(
                """
                        SELECT MAX(e.received_at) AS last_received_at
                        FROM t_ddh_lineage_event e
                        JOIN t_ddh_data_job j ON e.job_id = j.id
                        WHERE j.cluster_id = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getTimestamp("last_received_at"),
                clusterId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0)).map(Timestamp::toInstant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record JobDetail(long id, long clusterId, String jobName, String engine, String jobType,
                            String dwLayer, String owner, String externalUrl, String state, Instant updateTime) {
    }
}
