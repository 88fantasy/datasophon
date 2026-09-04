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

import com.datasophon.api.doris.DorisAdminReaderFactory;
import com.datasophon.api.doris.DorisVersionProfile;
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.dto.v2.DorisActiveTaskVO;
import com.datasophon.api.dto.v2.DorisBeTaskDetailVO;
import com.datasophon.api.enums.Status;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Reads, merges, filters, and limits one Doris active-task snapshot. */
@Service
public class DorisActiveTaskQueryService {

    /** 三张主表的 SQL 按 Doris 大版本取自 {@link DorisVersionProfile}；本表两版列名一致，无需区分。 */
    public static final String WORKLOAD_GROUPS_SQL = """
            SELECT Id, Name FROM information_schema.workload_groups;
            """;

    public static final int LIST_SQL_LIMIT_BYTES = 1_024;
    public static final int DETAIL_SQL_LIMIT_BYTES = 256 * 1_024;
    public static final int RESPONSE_LIMIT = 2_000;
    public static final int REQUEST_TIMEOUT_MS = 15_000;

    private static final String CLIENT_ADDRESS_FAILURE = "clientAddress";
    private static final String WORKLOAD_GROUP_FAILURE = "workloadGroup";
    private static final int STATEMENT_TIMEOUT_MS = 10_000;

    private final ClusterHostService hostService;
    private final RowQuery rowQuery;
    private final LongSupplier nanoTime;

    @Autowired
    public DorisActiveTaskQueryService(ClusterHostService hostService) {
        this(hostService, DorisActiveTaskQueryService::queryRows, System::nanoTime);
    }

    DorisActiveTaskQueryService(ClusterHostService hostService, RowQuery rowQuery) {
        this(hostService, rowQuery, System::nanoTime);
    }

    DorisActiveTaskQueryService(ClusterHostService hostService, RowQuery rowQuery,
                                LongSupplier nanoTime) {
        this.hostService = hostService;
        this.rowQuery = rowQuery;
        this.nanoTime = nanoTime;
    }

    /** Executes the four fixed statements and returns a response ready for the facade. */
    public DorisActiveTaskResponseVO query(Integer clusterId,
                                           DorisAdminReaderFactory.DorisAdminConnection connection,
                                           DorisActiveTaskQueryDTO filter) {
        return query(clusterId, connection, filter, null,
                nanoTime.getAsLong() + REQUEST_TIMEOUT_MS * 1_000_000L);
    }

    /** Returns one task with the larger detail-level SQL bound. */
    public DorisActiveTaskVO queryDetail(Integer clusterId,
                                         DorisAdminReaderFactory.DorisAdminConnection connection,
                                         String taskId) {
        return firstTask(query(clusterId, connection, new DorisActiveTaskQueryDTO(), taskId,
                nanoTime.getAsLong() + REQUEST_TIMEOUT_MS * 1_000_000L));
    }

    DorisActiveTaskResponseVO query(Integer clusterId,
                                    DorisAdminReaderFactory.DorisAdminConnection connection,
                                    DorisActiveTaskQueryDTO filter, long deadlineNanos) {
        return query(clusterId, connection, filter, null, deadlineNanos);
    }

    DorisActiveTaskVO queryDetail(Integer clusterId,
                                  DorisAdminReaderFactory.DorisAdminConnection connection,
                                  String taskId, long deadlineNanos) {
        return firstTask(query(clusterId, connection, new DorisActiveTaskQueryDTO(), taskId, deadlineNanos));
    }

    /** 详情路径的 {@code query} 已按 taskId 过滤，结果至多一条。 */
    private static DorisActiveTaskVO firstTask(DorisActiveTaskResponseVO response) {
        List<DorisActiveTaskVO> tasks = response.getTasks();
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    private DorisActiveTaskResponseVO query(Integer clusterId,
                                            DorisAdminReaderFactory.DorisAdminConnection connection,
                                            DorisActiveTaskQueryDTO filter,
                                            String detailTaskId, long deadlineNanos) {
        DorisVersionProfile profile = connection.profile();
        if (!profile.supported()) {
            throw new CapabilityUnsupportedException();
        }
        // 详情只要一条任务：把 taskId 下推到这两张随负载增长的表，避免为一行重跑两次全量扫描。
        // processlist / workload_groups 的行数由连接数与配置决定（ddh-01 实测 6 行 / 2 行），
        // 不随查询数增长，因此不下推，也就不必碰 3.x 与 4.x 不同的 processlist 列名。
        boolean detail = detailTaskId != null;
        Object[] idArg = detail ? new Object[]{detailTaskId} : new Object[0];
        List<Map<String, Object>> metadata = requiredRows(connection,
                detail ? profile.activeQueriesByIdSql() : profile.activeQueriesSql(), deadlineNanos, idArg);
        List<Map<String, Object>> resources = requiredRows(connection,
                detail ? profile.backendActiveTasksByIdSql() : profile.backendActiveTasksSql(),
                deadlineNanos, idArg);
        List<String> partialFailures = new ArrayList<>();
        List<Map<String, Object>> processlist = optionalRows(connection, profile.processlistSql(),
                CLIENT_ADDRESS_FAILURE, partialFailures, deadlineNanos);
        List<Map<String, Object>> workloadGroups = optionalRows(connection, WORKLOAD_GROUPS_SQL,
                WORKLOAD_GROUP_FAILURE, partialFailures, deadlineNanos);
        ensureWithinDeadline(deadlineNanos);

        boolean sourceTruncated = atSourceLimit(metadata) || atSourceLimit(resources)
                || atSourceLimit(processlist) || atSourceLimit(workloadGroups);
        Map<String, Map<String, Object>> queryById = indexById(metadata);
        Map<String, List<Map<String, Object>>> resourcesById = groupById(resources);
        Map<String, String> clientsByQueryId = byQueryId(processlist, "Host");
        Map<String, String> usersByQueryId = byQueryId(processlist, "User");
        Map<String, String> workloadNames = workloadNames(workloadGroups);
        Map<String, String> hostNames = hostNames(clusterId);

        Set<String> ids = new LinkedHashSet<>(queryById.keySet());
        ids.addAll(resourcesById.keySet());
        List<TaskRecord> allTasks = ids.stream()
                .map(id -> buildTask(id, queryById.get(id), resourcesById.getOrDefault(id, List.of()),
                        clientsByQueryId, usersByQueryId, workloadNames, hostNames))
                .toList();
        List<TaskRecord> filtered = allTasks.stream()
                .filter(task -> detailTaskId == null ? matches(task, filter)
                        : detailTaskId.equals(task.task().getTaskId()))
                .sorted(TASK_ORDER)
                .toList();
        List<DorisActiveTaskVO> returnedTasks = filtered.stream()
                .limit(RESPONSE_LIMIT)
                .map(task -> detailTaskId == null ? task.task() : withDetailSql(task))
                .toList();

        DorisActiveTaskResponseVO response = new DorisActiveTaskResponseVO();
        response.setTasks(returnedTasks);
        response.setDegraded(connection.degraded());
        response.setDegradedReason(connection.degradedReason());
        response.setPartialFailures(partialFailures);
        response.setTruncated(filtered.size() > RESPONSE_LIMIT);
        response.setSourceTruncated(sourceTruncated);
        response.setTotal(filtered.size());
        response.setReturned(returnedTasks.size());
        response.setConnectedHostPort(connection.hostPort());
        response.setServerVersion(connection.serverVersion());
        response.setUnsupportedFields(profile.unsupportedFields());
        return response;
    }

    public static TruncatedText truncateSql(String sql, int maxBytes) {
        if (sql == null) {
            return new TruncatedText(null, false);
        }
        if (maxBytes <= 0) {
            return new TruncatedText("", !sql.isEmpty());
        }
        int byteLength = sql.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength <= maxBytes) {
            return new TruncatedText(sql, false);
        }
        int bytes = 0;
        int end = 0;
        while (end < sql.length()) {
            int codePoint = sql.codePointAt(end);
            int codePointBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return new TruncatedText(sql.substring(0, end), true);
    }

    private List<Map<String, Object>> requiredRows(DorisAdminReaderFactory.DorisAdminConnection connection,
                                                   String sql, long deadlineNanos, Object... args) {
        try {
            return rows(connection, sql, deadlineNanos, args);
        } catch (RuntimeException exception) {
            if (looksLikeMissingTable(exception)) {
                throw new CapabilityUnsupportedException();
            }
            throw exception;
        }
    }

    private List<Map<String, Object>> optionalRows(DorisAdminReaderFactory.DorisAdminConnection connection,
                                                   String sql, String failure, List<String> partialFailures,
                                                   long deadlineNanos) {
        try {
            return rows(connection, sql, deadlineNanos);
        } catch (RequestTimeoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            partialFailures.add(failure);
            return List.of();
        }
    }

    private List<Map<String, Object>> rows(DorisAdminReaderFactory.DorisAdminConnection connection,
                                           String sql, long deadlineNanos, Object... args) {
        ensureWithinDeadline(deadlineNanos);
        if (connection.dataSource() == null) {
            return rowQuery.apply(connection.client(), sql, args);
        }
        long remainingNanos = deadlineNanos - nanoTime.getAsLong();
        int timeoutSeconds = (int) Math.min(STATEMENT_TIMEOUT_MS / 1_000,
                remainingNanos / 1_000_000_000L);
        if (timeoutSeconds < 1) {
            throw new RequestTimeoutException();
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(connection.dataSource());
        jdbcTemplate.setQueryTimeout(timeoutSeconds);
        return args.length == 0 ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, args);
    }

    private void ensureWithinDeadline(long deadlineNanos) {
        if (deadlineNanos - nanoTime.getAsLong() < 1_000_000_000L) {
            throw new RequestTimeoutException();
        }
    }

    private TaskRecord buildTask(String id, Map<String, Object> metadata, List<Map<String, Object>> resourceRows,
                                 Map<String, String> clientsByQueryId, Map<String, String> usersByQueryId,
                                 Map<String, String> workloadNames, Map<String, String> hostNames) {
        Map<String, Object> firstResource = resourceRows.isEmpty() ? Map.of() : resourceRows.get(0);
        String queryType = text(firstResource, "QUERY_TYPE");
        boolean query = metadata != null || "SELECT".equalsIgnoreCase(queryType)
                || "QUERY".equalsIgnoreCase(queryType);
        String type = query ? "QUERY" : "LOAD";
        String fullSql = metadata == null ? null : text(metadata, "SQL");
        TruncatedText listSql = truncateSql(fullSql, LIST_SQL_LIMIT_BYTES);
        Long elapsed = metadata == null ? max(resourceRows, "TASK_TIME_MS") : number(metadata, "QUERY_TIME_MS");
        if (elapsed == null && metadata != null) {
            elapsed = max(resourceRows, "TASK_TIME_MS");
        }

        DorisActiveTaskVO task = new DorisActiveTaskVO();
        task.setTaskId(id);
        task.setType(type);
        // Doris 3.x 的 active_queries 没有 USER 列，退回 processlist 的同名列（4.x 取不到才会走到）。
        String user = metadata == null ? null : text(metadata, "USER");
        task.setUser(query ? (user == null ? usersByQueryId.get(id) : user) : null);
        task.setClientAddress(query ? clientsByQueryId.get(id) : null);
        task.setSql(query ? listSql.text() : null);
        task.setElapsedMs(elapsed);
        task.setStartTime(metadata == null ? null : text(metadata, "QUERY_START_TIME"));
        task.setCurrentMemoryBytes(sum(resourceRows, "CURRENT_USED_MEMORY_BYTES"));
        task.setPeakMemoryBytes(max(resourceRows, "BE_PEAK_MEMORY_BYTES"));
        task.setScanRows(sum(resourceRows, "SCAN_ROWS"));
        task.setScanBytes(query ? sum(resourceRows, "SCAN_BYTES") : null);
        task.setCpuTimeMs(sum(resourceRows, "TASK_CPU_TIME_MS"));
        task.setShuffleSendBytes(sum(resourceRows, "SHUFFLE_SEND_BYTES"));
        task.setShuffleSendRows(sum(resourceRows, "SHUFFLE_SEND_ROWS"));
        task.setSpillWriteBytesToLocalStorage(sum(resourceRows, "SPILL_WRITE_BYTES_TO_LOCAL_STORAGE"));
        task.setSpillReadBytesFromLocalStorage(sum(resourceRows, "SPILL_READ_BYTES_FROM_LOCAL_STORAGE"));
        Long workloadGroupId = metadata == null
                ? number(firstResource, "WORKLOAD_GROUP_ID")
                : number(metadata, "WORKLOAD_GROUP_ID");
        task.setWorkloadGroupId(workloadGroupId);
        task.setWorkloadGroupName(workloadGroupId == null ? null : workloadNames.get(String.valueOf(workloadGroupId)));
        String rawFeHost = shortenFeHost(
                metadata == null ? text(firstResource, "FE_HOST") : text(metadata, "FRONTEND_INSTANCE"));
        task.setFeHost(metadata == null && rawFeHost != null
                ? hostNames.getOrDefault(rawFeHost, rawFeHost)
                : rawFeHost);
        task.setQueryStatus(metadata == null ? null : text(metadata, "QUERY_STATUS"));
        task.setQueueStartTime(metadata == null ? null : text(metadata, "QUEUE_START_TIME"));
        task.setQueueEndTime(metadata == null ? null : text(metadata, "QUEUE_END_TIME"));
        task.setTruncated(listSql.truncated());
        task.setBeDetails(resourceRows.stream()
                .map(row -> beDetail(row, query))
                .toList());
        return new TaskRecord(task, fullSql);
    }

    private DorisActiveTaskVO withDetailSql(TaskRecord record) {
        TruncatedText detailSql = truncateSql(record.fullSql(), DETAIL_SQL_LIMIT_BYTES);
        record.task().setDetailSql(detailSql.text());
        record.task().setTruncated(detailSql.truncated());
        return record.task();
    }

    private DorisBeTaskDetailVO beDetail(Map<String, Object> row, boolean query) {
        DorisBeTaskDetailVO detail = new DorisBeTaskDetailVO();
        detail.setBeId(text(row, "BE_ID"));
        detail.setPeakMemoryBytes(number(row, "BE_PEAK_MEMORY_BYTES"));
        detail.setCurrentMemoryBytes(number(row, "CURRENT_USED_MEMORY_BYTES"));
        detail.setScanRows(number(row, "SCAN_ROWS"));
        detail.setScanBytes(query ? number(row, "SCAN_BYTES") : null);
        return detail;
    }

    private boolean matches(TaskRecord record, DorisActiveTaskQueryDTO filter) {
        if (filter == null) {
            return true;
        }
        String keyword = lower(filter.getKeyword());
        DorisActiveTaskVO task = record.task();
        if (keyword != null && !contains(keyword, task.getTaskId())
                && !contains(keyword, task.getUser()) && !contains(keyword, record.fullSql())) {
            return false;
        }
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()
                && filter.getTypes().stream().noneMatch(type -> type != null
                        && matchesType(type, task))) {
            return false;
        }
        if (!contains(lower(filter.getUser()), task.getUser())
                || !contains(lower(filter.getFeHost()), task.getFeHost())) {
            return false;
        }
        return atLeast(task.getCurrentMemoryBytes(), filter.getMinMemoryBytes())
                && atLeast(task.getElapsedMs(), filter.getMinElapsedMs());
    }

    private boolean matchesType(String type, DorisActiveTaskVO task) {
        if ("QUEUED".equalsIgnoreCase(type)) {
            return "QUERY".equalsIgnoreCase(task.getType())
                    && "QUEUED".equalsIgnoreCase(task.getQueryStatus());
        }
        return type.equalsIgnoreCase(task.getType());
    }

    private static final Comparator<TaskRecord> TASK_ORDER = Comparator
            .comparing((TaskRecord record) -> record.task().getCurrentMemoryBytes(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(record -> record.task().getElapsedMs(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(record -> valueOrEmpty(record.task().getTaskId()));

    /**
     * 按 QueryId 索引 processlist 的某一列。
     *
     * <p>{@code QueryId} 在 Sleep/EOF 连接上保留的是上一条查询的 ID，因此必须叠加
     * {@code Command} 过滤（SQL 里已过滤一次，这里对手工构造的行再兜一次）。
     */
    private Map<String, String> byQueryId(List<Map<String, Object>> rows, String column) {
        Map<String, String> values = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String command = text(row, "Command");
            if (command != null && !"QUERY".equalsIgnoreCase(command)) {
                continue;
            }
            String id = text(row, "QueryId");
            if (id != null) {
                values.putIfAbsent(id, text(row, column));
            }
        }
        return values;
    }

    private Map<String, String> workloadNames(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new String[]{text(row, "Id"), text(row, "Name")})
                .filter(entry -> entry[0] != null && entry[1] != null)
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1], (first, ignored) -> first));
    }

    private Map<String, String> hostNames(Integer clusterId) {
        if (hostService == null) {
            return Map.of();
        }
        try {
            return hostService.getHostListByClusterId(clusterId).stream()
                    .filter(host -> host.getIp() != null && host.getHostname() != null)
                    .collect(Collectors.toMap(ClusterHostDO::getIp, ClusterHostDO::getHostname,
                            (first, ignored) -> first));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, Map<String, Object>> indexById(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = text(row, "QUERY_ID");
            if (id != null) {
                result.putIfAbsent(id, row);
            }
        }
        return result;
    }

    private static Map<String, List<Map<String, Object>>> groupById(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String id = text(row, "QUERY_ID");
            if (id != null) {
                result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(row);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> queryRows(JdbcClient client, String sql, Object... args) {
        return args.length == 0
                ? client.sql(sql).query().listOfRows()
                : client.sql(sql).params(args).query().listOfRows();
    }

    /** 取数接缝：生产走 JDBC，测试注入假数据。带 args 以便详情路径参数化下推 taskId。 */
    @FunctionalInterface
    interface RowQuery {
        List<Map<String, Object>> apply(JdbcClient client, String sql, Object... args);
    }

    private static boolean atSourceLimit(Collection<?> rows) {
        return rows.size() == DorisVersionProfile.SOURCE_LIMIT;
    }

    private static boolean atLeast(Long value, Long minimum) {
        return minimum == null || value != null && value >= minimum;
    }

    private static boolean contains(String needle, String value) {
        return needle == null || value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String lower(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : normalizeBlank(String.valueOf(value));
    }

    private static Long number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long sum(List<Map<String, Object>> rows, String key) {
        long total = 0;
        boolean present = false;
        for (Map<String, Object> row : rows) {
            Long value = number(row, key);
            if (value != null) {
                present = true;
                total += value;
            }
        }
        return present ? total : null;
    }

    private static Long max(List<Map<String, Object>> rows, String key) {
        Long maximum = null;
        for (Map<String, Object> row : rows) {
            Long value = number(row, key);
            if (value != null && (maximum == null || value > maximum)) {
                maximum = value;
            }
        }
        return maximum;
    }

    private static Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null || row.containsKey(key)) {
            return value;
        }
        String upperKey = key.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().toUpperCase(Locale.ROOT).equals(upperKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * K8s 上的 Doris FE 报的是 Pod FQDN
     * {@code <pod>.<service>.<namespace>.svc.cluster.local}（实测 96 字符，会撑爆列宽），
     * 只保留 {@code <pod>.<namespace>}。不含 {@code .svc.} 的值（IP、主机名）原样返回。
     */
    private static String shortenFeHost(String host) {
        if (host == null || !host.contains(".svc.")) {
            return host;
        }
        String[] parts = host.split("\\.");
        return parts.length < 3 ? host : parts[0] + "." + parts[2];
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean looksLikeMissingTable(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("doesn't exist") || lower.contains("does not exist")
                        || lower.contains("unknown table") || lower.contains("table not found")) {
                    return true;
                }
            }
        }
        return false;
    }

    public record TruncatedText(String text, boolean truncated) {
    }

    private record TaskRecord(DorisActiveTaskVO task, String fullSql) {
    }

    public static final class CapabilityUnsupportedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CapabilityUnsupportedException() {
            super(Status.DORIS_CAPABILITY_UNSUPPORTED.getMsg());
        }
    }

    static final class RequestTimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RequestTimeoutException() {
            super("Doris active-task request timed out");
        }
    }
}
