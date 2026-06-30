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

package com.datasophon.api.observability;

import com.datasophon.api.observability.OtelTracesQueryService.PageResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class OtelLogsQueryService {
    
    static final Set<String> ALLOWED_SEVERITIES = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    
    private final OtelDorisReaderFactory readerFactory;
    
    public OtelLogsQueryService(OtelDorisReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }
    
    public PageResult<LogRow> listLogs(Integer clusterId, long startSec, long endSec,
                                       String serviceName, String severities, String bodyKeyword,
                                       String traceId, int page, int pageSize) {
        List<String> severityList = parseSeverities(severities);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 200);
        JdbcClient client = readerFactory.create(clusterId);
        List<Map<String, Object>> rows = bindListParams(
                client.sql(buildListLogsSql(false, serviceName, severityList, bodyKeyword, traceId)),
                startSec, endSec, serviceName, severityList, bodyKeyword, traceId)
                        .param("limit", safePageSize)
                        .param("offset", (safePage - 1) * safePageSize)
                        .query()
                        .listOfRows();
        Long total = bindListParams(
                client.sql(buildListLogsSql(true, serviceName, severityList, bodyKeyword, traceId)),
                startSec, endSec, serviceName, severityList, bodyKeyword, traceId)
                        .query(Long.class)
                        .single();
        return new PageResult<>(total == null ? 0L : total, rows.stream().map(OtelLogsQueryService::toLogRow).toList());
    }
    
    static String buildListLogsSql(boolean count, String serviceName, List<String> severities,
                                   String bodyKeyword, String traceId) {
        StringBuilder sql = new StringBuilder(count
                ? "SELECT COUNT(*)\nFROM otel.otel_logs\nWHERE timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)"
                : "SELECT timestamp,\n"
                        + "       severity_text,\n"
                        + "       service_name,\n"
                        + "       body,\n"
                        + "       trace_id,\n"
                        + "       span_id,\n"
                        + "       log_attributes,\n"
                        + "       resource_attributes\n"
                        + "FROM otel.otel_logs\n"
                        + "WHERE timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        if (needsParam(serviceName)) {
            sql.append("\n  AND service_name = :serviceName");
        }
        if (severities != null && !severities.isEmpty()) {
            sql.append("\n  AND severity_text IN (:severities)");
        }
        if (needsParam(bodyKeyword)) {
            sql.append("\n  AND body MATCH_PHRASE :bodyKeyword");
        }
        if (needsParam(traceId)) {
            sql.append("\n  AND trace_id = :traceId");
        }
        if (!count) {
            sql.append("\nORDER BY timestamp DESC\nLIMIT :limit OFFSET :offset");
        }
        return sql.toString();
    }
    
    static List<String> parseSeverities(String severities) {
        if (severities == null || severities.isBlank()) {
            return List.of();
        }
        List<String> values = Arrays.stream(severities.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .toList();
        for (String value : values) {
            if (!ALLOWED_SEVERITIES.contains(value)) {
                throw new IllegalArgumentException("Unsupported log severity: " + value);
            }
        }
        return values;
    }
    
    private static JdbcClient.StatementSpec bindListParams(JdbcClient.StatementSpec spec,
                                                           long startSec, long endSec,
                                                           String serviceName, List<String> severities,
                                                           String bodyKeyword, String traceId) {
        spec = spec.param("start", startSec).param("end", endSec);
        if (needsParam(serviceName)) {
            spec = spec.param("serviceName", serviceName);
        }
        if (severities != null && !severities.isEmpty()) {
            spec = spec.param("severities", severities);
        }
        if (needsParam(bodyKeyword)) {
            spec = spec.param("bodyKeyword", bodyKeyword);
        }
        if (needsParam(traceId)) {
            spec = spec.param("traceId", traceId);
        }
        return spec;
    }
    
    private static boolean needsParam(String value) {
        return value != null && !value.isBlank();
    }
    
    private static LogRow toLogRow(Map<String, Object> row) {
        return new LogRow(
                stringValue(row.get("timestamp")),
                stringValue(row.get("severity_text")),
                stringValue(row.get("service_name")),
                stringValue(row.get("body")),
                stringValue(row.get("trace_id")),
                stringValue(row.get("span_id")),
                OtelTracesQueryService.toMap(row.get("log_attributes")),
                OtelTracesQueryService.toMap(row.get("resource_attributes")));
    }
    
    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
    
    public record LogRow(String timestamp,
                         String severityText,
                         String serviceName,
                         String body,
                         String traceId,
                         String spanId,
                         Map<String, Object> logAttributes,
                         Map<String, Object> resourceAttributes) {
    }
}
