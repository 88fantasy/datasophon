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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class OtelTracesQueryServiceTest {
    
    @Test
    void listSql_withoutOptionalFilters_containsTimeWindowAndPagination() {
        String sql = OtelTracesQueryService.buildListTracesSql(false, null, null, null, null);
        
        assertThat(sql).contains("otel.otel_traces");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("parent_span_id IS NULL OR parent_span_id = ''");
        assertThat(sql).contains("LIMIT :limit OFFSET :offset");
        assertThat(sql).doesNotContain(":serviceName");
        assertThat(sql).doesNotContain(":status");
        assertThat(sql).doesNotContain(":spanName");
        assertThat(sql).doesNotContain(":traceId");
    }
    
    @Test
    void listSql_withFilters_appendsNamedParamsOnly() {
        String sql = OtelTracesQueryService.buildListTracesSql(
                false, "datasophon-api", "ERROR", "cluster/list", "abc");
        
        assertThat(sql).contains("service_name = :serviceName");
        assertThat(sql).contains("span_name LIKE CONCAT('%', :spanName, '%')");
        assertThat(sql).contains("trace_id = :traceId");
        assertThat(sql).contains("status = :status");
        assertThat(sql).doesNotContain("datasophon-api");
        assertThat(sql).doesNotContain("cluster/list");
        assertThat(sql).doesNotContain("abc");
    }
    
    @Test
    void countSql_omitsPagination() {
        String sql = OtelTracesQueryService.buildListTracesSql(true, null, "OK", null, null);
        
        assertThat(sql).contains("SELECT COUNT(*) FROM traces");
        assertThat(sql).contains("status = :status");
        assertThat(sql).doesNotContain("LIMIT :limit");
        assertThat(sql).doesNotContain("OFFSET :offset");
    }
    
    @Test
    void traceDetailSql_filtersByTraceIdAndOrdersByTimestamp() {
        String sql = OtelTracesQueryService.buildTraceDetailSql();
        
        assertThat(sql).contains("span_attributes");
        assertThat(sql).contains("resource_attributes");
        assertThat(sql).contains("events");
        assertThat(sql).contains("WHERE trace_id = :traceId");
        assertThat(sql).contains("ORDER BY timestamp");
    }
    
    @Test
    void validateStatus_rejectsNonWhitelistStatus() {
        assertThatThrownBy(() -> OtelTracesQueryService.validateStatus("OK' OR 1=1 --"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void toMap_parsesJsonObjectAndFallsBackToRawValue() {
        assertThat(OtelTracesQueryService.toMap("{\"http.method\":\"GET\"}"))
                .containsEntry("http.method", "GET");
        assertThat(OtelTracesQueryService.toMap("not-json"))
                .containsEntry("_raw", "not-json");
        assertThat(OtelTracesQueryService.toMap(Map.of("key", "value")))
                .containsEntry("key", "value");
    }
}
