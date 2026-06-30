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

import java.util.List;

import org.junit.jupiter.api.Test;

class OtelLogsQueryServiceTest {
    
    @Test
    void listSql_withoutOptionalFilters_containsTimeWindowAndPagination() {
        String sql = OtelLogsQueryService.buildListLogsSql(false, null, List.of(), null, null);
        
        assertThat(sql).contains("FROM otel.otel_logs");
        assertThat(sql).contains("timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        assertThat(sql).contains("ORDER BY timestamp DESC");
        assertThat(sql).contains("LIMIT :limit OFFSET :offset");
        assertThat(sql).doesNotContain(":serviceName");
        assertThat(sql).doesNotContain(":severities");
        assertThat(sql).doesNotContain(":bodyKeyword");
        assertThat(sql).doesNotContain(":traceId");
    }
    
    @Test
    void listSql_withFilters_usesMatchPhraseInAndTraceIdParams() {
        String sql = OtelLogsQueryService.buildListLogsSql(
                false, "datasophon-api", List.of("ERROR", "WARN"), "timeout", "abc");
        
        assertThat(sql).contains("service_name = :serviceName");
        assertThat(sql).contains("severity_text IN (:severities)");
        assertThat(sql).contains("body MATCH_PHRASE :bodyKeyword");
        assertThat(sql).contains("trace_id = :traceId");
        assertThat(sql).doesNotContain("datasophon-api");
        assertThat(sql).doesNotContain("timeout");
        assertThat(sql).doesNotContain("abc");
    }
    
    @Test
    void countSql_omitsPagination() {
        String sql = OtelLogsQueryService.buildListLogsSql(true, null, List.of("ERROR"), null, null);
        
        assertThat(sql).contains("SELECT COUNT(*)");
        assertThat(sql).contains("severity_text IN (:severities)");
        assertThat(sql).doesNotContain("LIMIT :limit");
        assertThat(sql).doesNotContain("ORDER BY timestamp DESC");
    }
    
    @Test
    void parseSeverities_normalizesValuesAndRejectsNonWhitelist() {
        assertThat(OtelLogsQueryService.parseSeverities("error, warn"))
                .containsExactly("ERROR", "WARN");
        
        assertThatThrownBy(() -> OtelLogsQueryService.parseSeverities("ERROR,INFO');DROP TABLE x;--"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
