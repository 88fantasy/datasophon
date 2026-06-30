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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OtelTracesQueryService {
    
    static final Set<String> ALLOWED_STATUS = Set.of("OK", "ERROR");
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final class MapTypeRef extends TypeReference<Map<String, Object>> {
    }
    
    private static final class ListTypeRef extends TypeReference<List<Object>> {
    }
    
    private static final MapTypeRef MAP_TYPE = new MapTypeRef();
    
    private static final ListTypeRef LIST_TYPE = new ListTypeRef();
    
    private final OtelDorisReaderFactory readerFactory;
    
    public OtelTracesQueryService(OtelDorisReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }
    
    public PageResult<TraceRow> listTraces(Integer clusterId, long startSec, long endSec,
                                           String serviceName, String status, String spanName,
                                           String traceId, int page, int pageSize) {
        validateStatus(status);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 200);
        JdbcClient client = readerFactory.create(clusterId);
        List<Map<String, Object>> rows = bindTraceListParams(
                client.sql(buildListTracesSql(false, serviceName, status, spanName, traceId)),
                startSec, endSec, serviceName, status, spanName, traceId)
                        .param("limit", safePageSize)
                        .param("offset", (safePage - 1) * safePageSize)
                        .query()
                        .listOfRows();
        Long total = bindTraceListParams(
                client.sql(buildListTracesSql(true, serviceName, status, spanName, traceId)),
                startSec, endSec, serviceName, status, spanName, traceId)
                        .query(Long.class)
                        .single();
        return new PageResult<>(total == null ? 0L : total, rows.stream().map(OtelTracesQueryService::toTraceRow).toList());
    }
    
    public List<SpanNode> getTrace(Integer clusterId, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return readerFactory.create(clusterId)
                .sql(buildTraceDetailSql())
                .param("traceId", traceId)
                .query()
                .listOfRows()
                .stream()
                .map(OtelTracesQueryService::toSpanNode)
                .toList();
    }
    
    public List<String> listServices(Integer clusterId, long startSec, long endSec) {
        return readerFactory.create(clusterId)
                .sql("SELECT DISTINCT service_name\n"
                        + "FROM otel.otel_traces\n"
                        + "WHERE timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)\n"
                        + "  AND service_name IS NOT NULL AND service_name != ''\n"
                        + "ORDER BY service_name")
                .param("start", startSec)
                .param("end", endSec)
                .query(String.class)
                .list();
    }
    
    static String buildListTracesSql(boolean count, String serviceName, String status,
                                     String spanName, String traceId) {
        StringBuilder sql = new StringBuilder(
                "WITH filtered AS (\n"
                        + "  SELECT trace_id, span_id, parent_span_id, span_name, service_name,\n"
                        + "         timestamp, duration, status_code\n"
                        + "  FROM otel.otel_traces\n"
                        + "  WHERE timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        appendTraceFilters(sql, serviceName, spanName, traceId);
        sql.append("\n), trace_agg AS (\n"
                + "  SELECT trace_id,\n"
                + "         COUNT(*) AS span_count,\n"
                + "         SUM(CASE WHEN status_code = 'STATUS_CODE_ERROR' THEN 1 ELSE 0 END) AS error_count\n"
                + "  FROM filtered\n"
                + "  GROUP BY trace_id\n"
                + "), roots AS (\n"
                + "  SELECT trace_id, span_id, span_name, service_name, timestamp, duration, status_code\n"
                + "  FROM filtered\n"
                + "  WHERE parent_span_id IS NULL OR parent_span_id = ''\n"
                + "  QUALIFY ROW_NUMBER() OVER(PARTITION BY trace_id ORDER BY timestamp ASC) = 1\n"
                + "), traces AS (\n"
                + "  SELECT r.timestamp,\n"
                + "         r.service_name,\n"
                + "         r.span_name,\n"
                + "         r.trace_id,\n"
                + "         a.span_count,\n"
                + "         r.duration,\n"
                + "         CASE WHEN r.status_code = 'STATUS_CODE_ERROR' OR a.error_count > 0\n"
                + "           THEN 'ERROR' ELSE 'OK' END AS status\n"
                + "  FROM roots r\n"
                + "  JOIN trace_agg a ON r.trace_id = a.trace_id\n"
                + ")");
        if (count) {
            sql.append("\nSELECT COUNT(*) FROM traces WHERE 1 = 1");
        } else {
            sql.append("\nSELECT timestamp, service_name, span_name, trace_id, span_count, duration, status\n"
                    + "FROM traces\n"
                    + "WHERE 1 = 1");
        }
        if (needsParam(status)) {
            sql.append("\n  AND status = :status");
        }
        if (!count) {
            sql.append("\nORDER BY timestamp DESC\nLIMIT :limit OFFSET :offset");
        }
        return sql.toString();
    }
    
    static String buildTraceDetailSql() {
        return "SELECT span_id,\n"
                + "       parent_span_id,\n"
                + "       span_name,\n"
                + "       span_kind,\n"
                + "       service_name,\n"
                + "       timestamp,\n"
                + "       end_time,\n"
                + "       duration,\n"
                + "       status_code,\n"
                + "       status_message,\n"
                + "       span_attributes,\n"
                + "       resource_attributes,\n"
                + "       events\n"
                + "FROM otel.otel_traces\n"
                + "WHERE trace_id = :traceId\n"
                + "ORDER BY timestamp";
    }
    
    static void validateStatus(String status) {
        if (needsParam(status) && !ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("Unsupported trace status: " + status);
        }
    }
    
    private static JdbcClient.StatementSpec bindTraceListParams(JdbcClient.StatementSpec spec,
                                                                long startSec, long endSec,
                                                                String serviceName, String status,
                                                                String spanName, String traceId) {
        spec = spec.param("start", startSec).param("end", endSec);
        if (needsParam(serviceName)) {
            spec = spec.param("serviceName", serviceName);
        }
        if (needsParam(status)) {
            spec = spec.param("status", status);
        }
        if (needsParam(spanName)) {
            spec = spec.param("spanName", spanName);
        }
        if (needsParam(traceId)) {
            spec = spec.param("traceId", traceId);
        }
        return spec;
    }
    
    private static void appendTraceFilters(StringBuilder sql, String serviceName, String spanName, String traceId) {
        if (needsParam(serviceName)) {
            sql.append("\n  AND service_name = :serviceName");
        }
        if (needsParam(spanName)) {
            // span_name 没有 phrase 索引语义要求，这里用 LIKE 参数化做模糊匹配。
            sql.append("\n  AND span_name LIKE CONCAT('%', :spanName, '%')");
        }
        if (needsParam(traceId)) {
            sql.append("\n  AND trace_id = :traceId");
        }
    }
    
    private static boolean needsParam(String value) {
        return value != null && !value.isBlank();
    }
    
    private static TraceRow toTraceRow(Map<String, Object> row) {
        return new TraceRow(
                stringValue(row.get("timestamp")),
                stringValue(row.get("service_name")),
                stringValue(row.get("span_name")),
                stringValue(row.get("trace_id")),
                longValue(row.get("span_count")),
                longValue(row.get("duration")),
                stringValue(row.get("status")));
    }
    
    private static SpanNode toSpanNode(Map<String, Object> row) {
        return new SpanNode(
                stringValue(row.get("span_id")),
                stringValue(row.get("parent_span_id")),
                stringValue(row.get("span_name")),
                stringValue(row.get("span_kind")),
                stringValue(row.get("service_name")),
                stringValue(row.get("timestamp")),
                stringValue(row.get("end_time")),
                longValue(row.get("duration")),
                stringValue(row.get("status_code")),
                stringValue(row.get("status_message")),
                toMap(row.get("span_attributes")),
                toMap(row.get("resource_attributes")),
                toList(row.get("events")));
    }
    
    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
    
    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
    
    static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue));
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(str, MAP_TYPE);
            } catch (Exception ignored) {
                return Map.of("_raw", str);
            }
        }
        return Map.of("_raw", value);
    }
    
    static List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(str, LIST_TYPE);
            } catch (Exception ignored) {
                return List.of(str);
            }
        }
        return List.of(value);
    }
    
    public record PageResult<T>(
    long total, List<T>data)
    {
    }
    
    public record TraceRow(String timestamp,
                           String serviceName,
                           String spanName,
                           String traceId,
                           long spanCount,
                           long duration,
                           String status) {
    }
    
    public record SpanNode(String spanId,
                           String parentSpanId,
                           String spanName,
                           String spanKind,
                           String serviceName,
                           String timestamp,
                           String endTime,
                           long duration,
                           String statusCode,
                           String statusMessage,
                           Map<String, Object> spanAttributes,
                           Map<String, Object> resourceAttributes,
                           List<Object> events) {
    }
}
