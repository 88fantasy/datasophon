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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Reads, merges, filters, and limits one Doris active-task snapshot. */
@Service
public class DorisActiveTaskQueryService {

    public static final String ACTIVE_QUERIES_SQL = """
            SELECT QUERY_ID, QUERY_START_TIME, QUERY_TIME_MS, WORKLOAD_GROUP_ID,
                   FRONTEND_INSTANCE, QUEUE_START_TIME, QUEUE_END_TIME, QUERY_STATUS,
                   `USER`, `SQL`
            FROM information_schema.active_queries
            LIMIT 20000;
            """;

    public static final String BACKEND_ACTIVE_TASKS_SQL = """
            SELECT QUERY_ID, BE_ID, FE_HOST, WORKLOAD_GROUP_ID, QUERY_TYPE,
                   TASK_TIME_MS, TASK_CPU_TIME_MS, SCAN_ROWS, SCAN_BYTES,
                   BE_PEAK_MEMORY_BYTES, CURRENT_USED_MEMORY_BYTES,
                   SHUFFLE_SEND_BYTES, SHUFFLE_SEND_ROWS,
                   SPILL_WRITE_BYTES_TO_LOCAL_STORAGE, SPILL_READ_BYTES_FROM_LOCAL_STORAGE
            FROM information_schema.backend_active_tasks
            LIMIT 20000;
            """;

    public static final String PROCESSLIST_SQL = """
            SELECT QueryId, Host
            FROM information_schema.processlist
            WHERE Command = 'Query'
            LIMIT 20000;
            """;

    public static final String WORKLOAD_GROUPS_SQL = """
            SELECT Id, Name FROM information_schema.workload_groups;
            """;

    public static final int SOURCE_LIMIT = 20_000;
    public static final int LIST_SQL_LIMIT_BYTES = 1_024;
    public static final int DETAIL_SQL_LIMIT_BYTES = 256 * 1_024;
    public static final int RESPONSE_LIMIT = 2_000;

    private static final String CLIENT_ADDRESS_FAILURE = "clientAddress";
    private static final String WORKLOAD_GROUP_FAILURE = "workloadGroup";

    private final ClusterHostService hostService;
    private final BiFunction<JdbcClient, String, List<Map<String, Object>>> rowQuery;

    public DorisActiveTaskQueryService(ClusterHostService hostService) {
        this(hostService, DorisActiveTaskQueryService::queryRows);
    }

    DorisActiveTaskQueryService(ClusterHostService hostService,
                                BiFunction<JdbcClient, String, List<Map<String, Object>>> rowQuery) {
        this.hostService = hostService;
        this.rowQuery = rowQuery;
    }

    /** Executes the four fixed statements and returns a response ready for the facade. */
    public DorisActiveTaskResponseVO query(Integer clusterId,
                                           DorisAdminReaderFactory.DorisAdminConnection connection,
                                           DorisActiveTaskQueryDTO filter) {
        List<Map<String, Object>> metadata = requiredRows(connection.client(), ACTIVE_QUERIES_SQL);
        List<Map<String, Object>> resources = requiredRows(connection.client(), BACKEND_ACTIVE_TASKS_SQL);
        List<String> partialFailures = new ArrayList<>();
        List<Map<String, Object>> processlist = optionalRows(connection.client(), PROCESSLIST_SQL,
                CLIENT_ADDRESS_FAILURE, partialFailures);
        List<Map<String, Object>> workloadGroups = optionalRows(connection.client(), WORKLOAD_GROUPS_SQL,
                WORKLOAD_GROUP_FAILURE, partialFailures);

        boolean sourceTruncated = atSourceLimit(metadata) || atSourceLimit(resources)
                || atSourceLimit(processlist) || atSourceLimit(workloadGroups);
        Map<String, Map<String, Object>> queryById = indexById(metadata);
        Map<String, List<Map<String, Object>>> resourcesById = groupById(resources);
        Map<String, String> clientsByQueryId = clientsByQueryId(processlist);
        Map<String, String> workloadNames = workloadNames(workloadGroups);
        Map<String, String> hostNames = hostNames(clusterId);

        Set<String> ids = new LinkedHashSet<>(queryById.keySet());
        ids.addAll(resourcesById.keySet());
        List<TaskRecord> allTasks = ids.stream()
                .map(id -> buildTask(id, queryById.get(id), resourcesById.getOrDefault(id, List.of()),
                        clientsByQueryId, workloadNames, hostNames))
                .toList();
        List<TaskRecord> filtered = allTasks.stream()
                .filter(task -> matches(task, filter))
                .sorted(TASK_ORDER)
                .toList();
        List<DorisActiveTaskVO> returnedTasks = filtered.stream()
                .limit(RESPONSE_LIMIT)
                .map(TaskRecord::task)
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

    private List<Map<String, Object>> requiredRows(JdbcClient client, String sql) {
        try {
            return rowQuery.apply(client, sql);
        } catch (RuntimeException exception) {
            if (looksLikeMissingTable(exception)) {
                throw new CapabilityUnsupportedException();
            }
            throw exception;
        }
    }

    private List<Map<String, Object>> optionalRows(JdbcClient client, String sql, String failure,
                                                   List<String> partialFailures) {
        try {
            return rowQuery.apply(client, sql);
        } catch (RuntimeException exception) {
            partialFailures.add(failure);
            return List.of();
        }
    }

    private TaskRecord buildTask(String id, Map<String, Object> metadata, List<Map<String, Object>> resourceRows,
                                 Map<String, String> clientsByQueryId, Map<String, String> workloadNames,
                                 Map<String, String> hostNames) {
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
        task.setUser(query && metadata != null ? text(metadata, "USER") : null);
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
        String rawFeHost = metadata == null ? text(firstResource, "FE_HOST") : text(metadata, "FRONTEND_INSTANCE");
        task.setFeHost(metadata == null ? hostNames.getOrDefault(rawFeHost, rawFeHost) : rawFeHost);
        task.setQueryStatus(metadata == null ? null : text(metadata, "QUERY_STATUS"));
        task.setQueueStartTime(metadata == null ? null : text(metadata, "QUEUE_START_TIME"));
        task.setQueueEndTime(metadata == null ? null : text(metadata, "QUEUE_END_TIME"));
        task.setTruncated(listSql.truncated());
        task.setBeDetails(resourceRows.stream()
                .map(row -> beDetail(row, query))
                .toList());
        return new TaskRecord(task, fullSql);
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
                && type.equalsIgnoreCase(task.getType()))) {
            return false;
        }
        if (!contains(lower(filter.getUser()), task.getUser())
                || !contains(lower(filter.getFeHost()), task.getFeHost())) {
            return false;
        }
        return atLeast(task.getCurrentMemoryBytes(), filter.getMinMemoryBytes())
                && atLeast(task.getElapsedMs(), filter.getMinElapsedMs());
    }

    private static final Comparator<TaskRecord> TASK_ORDER = Comparator
            .comparing((TaskRecord record) -> record.task().getCurrentMemoryBytes(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(record -> record.task().getElapsedMs(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(record -> valueOrEmpty(record.task().getTaskId()));

    private Map<String, String> clientsByQueryId(List<Map<String, Object>> rows) {
        Map<String, String> clients = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String command = text(row, "COMMAND");
            if (command != null && !"QUERY".equalsIgnoreCase(command)) {
                continue;
            }
            String id = text(row, "QUERYID");
            if (id != null) {
                clients.putIfAbsent(id, text(row, "HOST"));
            }
        }
        return clients;
    }

    private Map<String, String> workloadNames(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new String[] {text(row, "ID"), text(row, "NAME")})
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

    private static List<Map<String, Object>> queryRows(JdbcClient client, String sql) {
        return client.sql(sql).query().listOfRows();
    }

    private static boolean atSourceLimit(Collection<?> rows) {
        return rows.size() == SOURCE_LIMIT;
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
}
