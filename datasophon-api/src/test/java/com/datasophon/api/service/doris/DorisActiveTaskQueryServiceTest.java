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

package com.datasophon.api.service.doris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.datasophon.api.doris.DorisAdminReaderFactory;
import com.datasophon.api.doris.DorisVersionProfile;
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.dto.v2.DorisActiveTaskVO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DorisActiveTaskQueryServiceTest {

    @Test
    void fullOuterJoinKeepsQueryLoadAndResourceOnlySelect() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row("QUERY_ID", "q-meta", "USER", "alice"));
        rows.get(DorisVersionProfile.V4.backendActiveTasksSql()).addAll(List.of(
                row("QUERY_ID", "q-meta", "QUERY_TYPE", "SELECT"),
                row("QUERY_ID", "load-1", "QUERY_TYPE", "LOAD"),
                row("QUERY_ID", "select-only", "QUERY_TYPE", "SELECT")));

        DorisActiveTaskResponseVO response = service(rows).query(7, connection(false), null);

        assertThat(response.getTasks()).extracting(DorisActiveTaskVO::getTaskId)
                .containsExactlyInAnyOrder("q-meta", "load-1", "select-only");
        assertThat(response.getTasks()).filteredOn(task -> task.getTaskId().equals("load-1"))
                .singleElement().extracting(DorisActiveTaskVO::getType).isEqualTo("LOAD");
        assertThat(response.getTasks()).filteredOn(task -> task.getTaskId().equals("select-only"))
                .singleElement().extracting(DorisActiveTaskVO::getType).isEqualTo("QUERY");
    }

    @Test
    void sumsCurrentMemoryButUsesMaximumPeakMemory() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.backendActiveTasksSql()).addAll(List.of(
                row("QUERY_ID", "q1", "QUERY_TYPE", "SELECT", "CURRENT_USED_MEMORY_BYTES", 10L,
                        "BE_PEAK_MEMORY_BYTES", 100L),
                row("QUERY_ID", "q1", "QUERY_TYPE", "SELECT", "CURRENT_USED_MEMORY_BYTES", 20L,
                        "BE_PEAK_MEMORY_BYTES", 80L)));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getCurrentMemoryBytes()).isEqualTo(30L);
        assertThat(task.getPeakMemoryBytes()).isEqualTo(100L).isNotEqualTo(180L);
    }

    @Test
    void loadScanBytesAndStartTimeAreNotApplicable() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.backendActiveTasksSql()).add(
                row("QUERY_ID", "load-1", "QUERY_TYPE", "LOAD", "TASK_TIME_MS", 4_000L,
                        "SCAN_BYTES", 0L));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getScanBytes()).isNull();
        assertThat(task.getStartTime()).isNull();
        assertThat(task.getElapsedMs()).isEqualTo(4_000L);
    }

    @Test
    void blankQueryStatusAndQueueTimesBecomeMissing() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row(
                "QUERY_ID", "q1", "QUERY_STATUS", "", "QUEUE_START_TIME", "", "QUEUE_END_TIME", " "));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getQueryStatus()).isNull();
        assertThat(task.getQueueStartTime()).isNull();
        assertThat(task.getQueueEndTime()).isNull();
    }

    @Test
    void queuedTypeFilterMatchesQueuedQueriesOnly() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).addAll(List.of(
                row("QUERY_ID", "queued", "QUERY_STATUS", "Queued"),
                row("QUERY_ID", "running", "QUERY_STATUS", "RUNNING")));
        DorisActiveTaskQueryDTO filter = new DorisActiveTaskQueryDTO();
        filter.setTypes(List.of("QUEUED"));

        DorisActiveTaskResponseVO response = service(rows).query(7, connection(false), filter);

        assertThat(response.getTasks()).extracting(DorisActiveTaskVO::getTaskId)
                .containsExactly("queued");
    }

    @Test
    void processlistOnlyUsesQueryRows() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row("QUERY_ID", "q1"));
        rows.get(DorisVersionProfile.V4.processlistSql()).addAll(List.of(
                row("QueryId", "q1", "Command", "Sleep", "Host", "stale-host"),
                row("QueryId", "q1", "Command", "Query", "Host", "real-host")));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getClientAddress()).isEqualTo("real-host");
    }

    @Test
    void truncatesUtf8SqlAtListAndDetailBoundaries() {
        String sql = "中".repeat(600);
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row("QUERY_ID", "q1", "SQL", sql));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);
        DorisActiveTaskQueryService.TruncatedText detail = DorisActiveTaskQueryService.truncateSql(
                sql, DorisActiveTaskQueryService.DETAIL_SQL_LIMIT_BYTES);

        assertThat(task.getSql().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(DorisActiveTaskQueryService.LIST_SQL_LIMIT_BYTES);
        assertThat(task.getSql().charAt(task.getSql().length() - 1)).isEqualTo('中');
        assertThat(task.getDetailSql()).isNull();

        DorisActiveTaskVO detailTask = service(rows).queryDetail(7, connection(false), "q1");
        assertThat(detailTask.getDetailSql()).isEqualTo(sql);
        assertThat(detailTask.getDetailSql().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(DorisActiveTaskQueryService.DETAIL_SQL_LIMIT_BYTES);
        assertThat(detailTask.getTruncated()).isFalse();
        assertThat(detail.truncated()).isFalse();
    }

    @Test
    void filtersBeforeTheTwoThousandRowLimit() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        for (int index = 0; index < DorisActiveTaskQueryService.RESPONSE_LIMIT + 1; index++) {
            rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(
                    row("QUERY_ID", "q-" + index, "USER", index == 2_000 ? "target" : "other"));
        }
        DorisActiveTaskQueryDTO filter = new DorisActiveTaskQueryDTO();
        filter.setUser("TARGET");

        DorisActiveTaskResponseVO response = service(rows).query(7, connection(false), filter);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getReturned()).isEqualTo(1);
        assertThat(response.isTruncated()).isFalse();
        assertThat(response.getTasks()).extracting(DorisActiveTaskVO::getTaskId).containsExactly("q-2000");
    }

    @Test
    void sortsByMemoryThenElapsedThenId() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.backendActiveTasksSql()).addAll(List.of(
                row("QUERY_ID", "b", "QUERY_TYPE", "LOAD", "CURRENT_USED_MEMORY_BYTES", 10L,
                        "TASK_TIME_MS", 2L),
                row("QUERY_ID", "a", "QUERY_TYPE", "LOAD", "CURRENT_USED_MEMORY_BYTES", 10L,
                        "TASK_TIME_MS", 2L),
                row("QUERY_ID", "c", "QUERY_TYPE", "LOAD", "CURRENT_USED_MEMORY_BYTES", 20L,
                        "TASK_TIME_MS", 1L)));

        DorisActiveTaskResponseVO response = service(rows).query(7, connection(false), null);

        assertThat(response.getTasks()).extracting(DorisActiveTaskVO::getTaskId)
                .containsExactly("c", "a", "b");
    }

    @Test
    void marksAnySourceAtExactlyTwentyThousandRows() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        for (int index = 0; index < DorisVersionProfile.SOURCE_LIMIT; index++) {
            rows.get(DorisVersionProfile.V4.backendActiveTasksSql()).add(
                    row("QUERY_ID", "load-" + index, "QUERY_TYPE", "LOAD"));
        }

        assertThat(service(rows).query(7, connection(false), null).isSourceTruncated()).isTrue();
    }

    @Test
    void enforcesOverallRequestDeadlineBetweenSourceQueries() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        AtomicInteger calls = new AtomicInteger();
        long[] now = {0L};
        DorisActiveTaskQueryService service = new DorisActiveTaskQueryService(null, (client, sql, args) -> {
            calls.incrementAndGet();
            now[0] = DorisActiveTaskQueryService.REQUEST_TIMEOUT_MS * 1_000_000L;
            return rows.getOrDefault(sql, List.of());
        }, () -> now[0]);

        assertThatThrownBy(() -> service.query(7, connection(false), null))
                .isInstanceOf(DorisActiveTaskQueryService.RequestTimeoutException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void reportsOptionalSourceFailureWithoutDroppingMainRows() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row("QUERY_ID", "q1"));
        DorisActiveTaskQueryService.RowQuery source = (client, sql, args) -> {
            if (DorisVersionProfile.V4.processlistSql().equals(sql)) {
                throw new IllegalStateException("processlist unavailable");
            }
            return rows.get(sql);
        };

        DorisActiveTaskResponseVO response = new DorisActiveTaskQueryService(null, source)
                .query(7, connection(false), null);

        assertThat(response.getTasks()).hasSize(1);
        assertThat(response.getPartialFailures()).containsExactly("clientAddress");
    }

    @Test
    void rejectsMissingRequiredTableAsCapabilityError() {
        DorisActiveTaskQueryService.RowQuery source = (client, sql, args) -> {
            throw new IllegalStateException("Table information_schema.active_queries doesn't exist");
        };

        assertThatThrownBy(() -> new DorisActiveTaskQueryService(null, source)
                .query(7, connection(false), null))
                .isInstanceOf(DorisActiveTaskQueryService.CapabilityUnsupportedException.class);
    }

    @Test
    void shortensKubernetesPodFqdnToPodAndNamespace() {
        // 实测 3.0.8 Cloud Mode 报的就是这个 96 字符的 Pod FQDN。
        String fqdn = "doris-disaggregated-cluster-fe-0"
                + ".doris-disaggregated-cluster-fe-internal.doris.svc.cluster.local";
        Map<String, List<Map<String, Object>>> rows = baseRows(DorisVersionProfile.V3);
        rows.get(DorisVersionProfile.V3.activeQueriesSql()).addAll(List.of(
                row("QUERY_ID", "on-k8s", "FRONTEND_INSTANCE", fqdn),
                row("QUERY_ID", "on-metal", "FRONTEND_INSTANCE", "192.168.10.131")));

        List<DorisActiveTaskVO> tasks = service(rows)
                .query(7, connection(DorisVersionProfile.V3), null).getTasks();

        assertThat(tasks).extracting(DorisActiveTaskVO::getFeHost)
                .containsExactlyInAnyOrder("doris-disaggregated-cluster-fe-0.doris", "192.168.10.131");
    }

    @Test
    void rejectsTwoPointXBeforeIssuingAnyStatement() {
        AtomicInteger calls = new AtomicInteger();
        DorisActiveTaskQueryService.RowQuery source = (client, sql, args) -> {
            calls.incrementAndGet();
            return List.of();
        };

        assertThatThrownBy(() -> new DorisActiveTaskQueryService(null, source)
                .query(7, connection(DorisVersionProfile.V2), null))
                .isInstanceOf(DorisActiveTaskQueryService.CapabilityUnsupportedException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void threePointXTakesUserFromProcesslistBecauseActiveQueriesHasNoUserColumn() {
        Map<String, List<Map<String, Object>>> rows = baseRows(DorisVersionProfile.V3);
        rows.get(DorisVersionProfile.V3.activeQueriesSql()).add(row("QUERY_ID", "q1"));
        rows.get(DorisVersionProfile.V3.processlistSql())
                .add(row("QueryId", "q1", "Command", "Query", "Host", "10.0.0.7:5555", "User", "alice"));

        DorisActiveTaskVO task = service(rows).query(7, connection(DorisVersionProfile.V3), null)
                .getTasks().get(0);

        assertThat(task.getUser()).isEqualTo("alice");
        assertThat(task.getClientAddress()).isEqualTo("10.0.0.7:5555");
    }

    @Test
    void reportsVersionAndTheFieldsThatVersionCannotProvide() {
        DorisActiveTaskResponseVO onThree = service(baseRows(DorisVersionProfile.V3))
                .query(7, connection(DorisVersionProfile.V3), null);
        DorisActiveTaskResponseVO onFour = service(baseRows(DorisVersionProfile.V4))
                .query(7, connection(DorisVersionProfile.V4), null);

        assertThat(onThree.getUnsupportedFields()).containsExactlyInAnyOrder("spillBytes", "loadWorkloadGroup");
        assertThat(onThree.getServerVersion()).contains("doris-3.");
        assertThat(onFour.getUnsupportedFields()).isEmpty();
    }

    @Test
    void detailPushesTaskIdIntoSqlAsBoundParameter() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisVersionProfile.V4.activeQueriesSql()).add(row("QUERY_ID", "q1", "SQL", "select 1"));
        Map<String, Object[]> issued = new HashMap<>();
        DorisActiveTaskQueryService.RowQuery capturing = (client, sql, args) -> {
            issued.put(sql, args);
            return rows.getOrDefault(sql, List.of());
        };

        new DorisActiveTaskQueryService(null, capturing).queryDetail(7, connection(false), "q1");

        // 两张会随负载涨到 SOURCE_LIMIT 的表必须走带 WHERE 的变体，且 taskId 以绑定参数下发，
        // 不拼进 SQL 文本（3.0.8 与 4.1.3 均实测 WHERE QUERY_ID 生效）。
        assertThat(issued).containsKeys(DorisVersionProfile.V4.activeQueriesByIdSql(),
                DorisVersionProfile.V4.backendActiveTasksByIdSql());
        assertThat(issued).doesNotContainKeys(DorisVersionProfile.V4.activeQueriesSql(),
                DorisVersionProfile.V4.backendActiveTasksSql());
        assertThat(issued.get(DorisVersionProfile.V4.activeQueriesByIdSql())).containsExactly("q1");
        assertThat(issued.get(DorisVersionProfile.V4.backendActiveTasksByIdSql())).containsExactly("q1");
        assertThat(DorisVersionProfile.V4.activeQueriesByIdSql()).doesNotContain("q1");
        // 侧查表行数由连接数/配置决定（实测 3.x 1 行、4.x 6 行），不下推，故仍是无参全量。
        assertThat(issued.get(DorisVersionProfile.V4.processlistSql())).isEmpty();
    }

    private static DorisActiveTaskQueryService service(Map<String, List<Map<String, Object>>> rows) {
        return new DorisActiveTaskQueryService(null, (client, sql, args) -> rows.getOrDefault(sql, List.of()));
    }

    private static DorisAdminReaderFactory.DorisAdminConnection connection(boolean degraded) {
        return new DorisAdminReaderFactory.DorisAdminConnection(
                mock(JdbcClient.class), "ddh-01", 9030, "root", degraded, degraded ? "fallback" : null);
    }

    private static DorisAdminReaderFactory.DorisAdminConnection connection(DorisVersionProfile profile) {
        return new DorisAdminReaderFactory.DorisAdminConnection(
                mock(JdbcClient.class), "doris-fe", 9030, "otel_reader", false, null, null,
                profile, "Doris version doris-" + profile.name().charAt(1) + ".0.8");
    }

    private static Map<String, List<Map<String, Object>>> baseRows() {
        return baseRows(DorisVersionProfile.V4);
    }

    private static Map<String, List<Map<String, Object>>> baseRows(DorisVersionProfile profile) {
        Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        List<Map<String, Object>> activeQueries = new ArrayList<>();
        List<Map<String, Object>> backendTasks = new ArrayList<>();
        rows.put(profile.activeQueriesSql(), activeQueries);
        rows.put(profile.backendActiveTasksSql(), backendTasks);
        // 详情路径走带 QUERY_ID 占位符的变体，指向同一份 fixture：假数据源不模拟 SQL 过滤，
        // 收敛到单条仍由服务自身的 detailTaskId 判断完成。
        rows.put(profile.activeQueriesByIdSql(), activeQueries);
        rows.put(profile.backendActiveTasksByIdSql(), backendTasks);
        rows.put(profile.processlistSql(), new ArrayList<>());
        rows.put(DorisActiveTaskQueryService.WORKLOAD_GROUPS_SQL, new ArrayList<>());
        return rows;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
