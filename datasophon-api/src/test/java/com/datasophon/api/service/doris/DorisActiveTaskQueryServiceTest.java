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
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.dto.v2.DorisActiveTaskVO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DorisActiveTaskQueryServiceTest {

    @Test
    void fullOuterJoinKeepsQueryLoadAndResourceOnlySelect() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(row("QUERY_ID", "q-meta", "USER", "alice"));
        rows.get(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL).addAll(List.of(
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
        rows.get(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL).addAll(List.of(
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
        rows.get(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL).add(
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
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(row(
                "QUERY_ID", "q1", "QUERY_STATUS", "", "QUEUE_START_TIME", "", "QUEUE_END_TIME", " "));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getQueryStatus()).isNull();
        assertThat(task.getQueueStartTime()).isNull();
        assertThat(task.getQueueEndTime()).isNull();
    }

    @Test
    void queuedTypeFilterMatchesQueuedQueriesOnly() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).addAll(List.of(
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
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(row("QUERY_ID", "q1"));
        rows.get(DorisActiveTaskQueryService.PROCESSLIST_SQL).addAll(List.of(
                row("QueryId", "q1", "Command", "Sleep", "Host", "stale-host"),
                row("QueryId", "q1", "Command", "Query", "Host", "real-host")));

        DorisActiveTaskVO task = service(rows).query(7, connection(false), null).getTasks().get(0);

        assertThat(task.getClientAddress()).isEqualTo("real-host");
    }

    @Test
    void truncatesUtf8SqlAtListAndDetailBoundaries() {
        String sql = "中".repeat(600);
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(row("QUERY_ID", "q1", "SQL", sql));

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
            rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(
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
        rows.get(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL).addAll(List.of(
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
        for (int index = 0; index < DorisActiveTaskQueryService.SOURCE_LIMIT; index++) {
            rows.get(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL).add(
                    row("QUERY_ID", "load-" + index, "QUERY_TYPE", "LOAD"));
        }

        assertThat(service(rows).query(7, connection(false), null).isSourceTruncated()).isTrue();
    }

    @Test
    void reportsOptionalSourceFailureWithoutDroppingMainRows() {
        Map<String, List<Map<String, Object>>> rows = baseRows();
        rows.get(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL).add(row("QUERY_ID", "q1"));
        BiFunction<JdbcClient, String, List<Map<String, Object>>> source = (client, sql) -> {
            if (DorisActiveTaskQueryService.PROCESSLIST_SQL.equals(sql)) {
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
        BiFunction<JdbcClient, String, List<Map<String, Object>>> source = (client, sql) -> {
            throw new IllegalStateException("Table information_schema.active_queries doesn't exist");
        };

        assertThatThrownBy(() -> new DorisActiveTaskQueryService(null, source)
                .query(7, connection(false), null))
                .isInstanceOf(DorisActiveTaskQueryService.CapabilityUnsupportedException.class);
    }

    private static DorisActiveTaskQueryService service(Map<String, List<Map<String, Object>>> rows) {
        return new DorisActiveTaskQueryService(null, (client, sql) -> rows.getOrDefault(sql, List.of()));
    }

    private static DorisAdminReaderFactory.DorisAdminConnection connection(boolean degraded) {
        return new DorisAdminReaderFactory.DorisAdminConnection(
                mock(JdbcClient.class), "ddh-01", 9030, "root", degraded, degraded ? "fallback" : null);
    }

    private static Map<String, List<Map<String, Object>>> baseRows() {
        Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        rows.put(DorisActiveTaskQueryService.ACTIVE_QUERIES_SQL, new ArrayList<>());
        rows.put(DorisActiveTaskQueryService.BACKEND_ACTIVE_TASKS_SQL, new ArrayList<>());
        rows.put(DorisActiveTaskQueryService.PROCESSLIST_SQL, new ArrayList<>());
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
