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

import com.datasophon.api.observability.PrometheusMatrixResult.MatrixSeries;
import com.datasophon.api.observability.PrometheusVectorResult.VectorSample;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OtelMetricsQueryServiceTest {
    
    // ─── SQL 生成测试 ────────────────────────────────────────────────────────────
    
    @Nested
    class SqlBuilding {
        
        @Test
        void instantNoAgg_containsQualifyAndNamedParams() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(false, false);
            assertThat(sql).containsIgnoringCase("QUALIFY");
            assertThat(sql).containsIgnoringCase("ROW_NUMBER()");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":evalTime");
            assertThat(sql).doesNotContain(":instance");
            assertThat(sql).doesNotContain(":job");
        }
        
        @Test
        void instantNoAgg_withFilters_appendsRegexpClauses() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(true, true);
            assertThat(sql).contains(":instance");
            assertThat(sql).contains(":job");
            assertThat(sql).containsIgnoringCase("REGEXP");
        }
        
        @Test
        void instantAgg_sum_containsSumAndQualify() {
            String sql = OtelMetricsQueryService.buildInstantAggSql("sum", false, false);
            assertThat(sql).containsIgnoringCase("SUM(value)");
            assertThat(sql).containsIgnoringCase("QUALIFY");
        }
        
        @Test
        void instantAgg_max_containsMaxFunction() {
            String sql = OtelMetricsQueryService.buildInstantAggSql("max", false, false);
            assertThat(sql).containsIgnoringCase("MAX(value)");
        }
        
        @Test
        void rangeSummary_containsLateralViewAndQuantile() {
            String sql = OtelMetricsQueryService.buildRangeSummarySql();
            assertThat(sql).containsIgnoringCase("LATERAL VIEW EXPLODE");
            assertThat(sql).contains("quantile_values");
            assertThat(sql).contains(":quantile");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":start");
            assertThat(sql).contains(":end");
            assertThat(sql).containsIgnoringCase("BETWEEN FROM_UNIXTIME");
            assertThat(sql).contains("resource_attributes");
        }
        
        @Test
        void rangeGauge_containsFloorAndAvgAndBetween() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(false, false, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("FLOOR(");
            assertThat(sql).containsIgnoringCase("AVG(value)");
            assertThat(sql).containsIgnoringCase("BETWEEN");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":start");
            assertThat(sql).contains(":end");
            assertThat(sql).contains(":metric");
        }
        
        @Test
        void rangeRate_containsLagAndRateWindow() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(false, false, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("LAG(ts)");
            assertThat(sql).containsIgnoringCase("LAG(value)");
            assertThat(sql).contains(":rateWindow");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":metric");
            // rate formula: (value - prev_val) / (ts - prev_ts)
            assertThat(sql).contains("prev_val");
            assertThat(sql).contains("prev_ts");
        }
        
        @Test
        void rangeRate_withFilters_appendsRegexpClauses() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(true, false, "otel_metrics_gauge");
            assertThat(sql).contains(":instance");
            assertThat(sql).doesNotContain(":job");
        }
        
        @Test
        void noSqlBuilderConcatenatesUserInput() {
            // Verify no format-string concatenation of filter values (params are named, not inlined).
            // The SQL templates contain only literal :paramName placeholders, never string values.
            String sqlWithFilters = OtelMetricsQueryService.buildInstantNoAggSql(true, true);
            assertThat(sqlWithFilters).doesNotContain("'.+'");
            assertThat(sqlWithFilters).doesNotContain("\"localhost\"");
        }
    }
    
    // ─── 行映射测试 ──────────────────────────────────────────────────────────────
    
    @Nested
    class RowMapping {
        
        @Test
        void buildVector_emptyRows_returnsEmptyVector() {
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(List.of(), 1.0);
            assertThat(result.resultType()).isEqualTo("vector");
            assertThat(result.result()).isEmpty();
        }
        
        @Test
        void buildVector_singleRow_convertsCorrectly() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "host:8081", "job", "nexus", "ts", 1234567890L, "value", 42.5));
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(rows, 1.0);
            
            assertThat(result.result()).hasSize(1);
            VectorSample sample = result.result().get(0);
            assertThat(sample.metric()).containsEntry("instance", "host:8081");
            assertThat(sample.metric()).containsEntry("job", "nexus");
            assertThat(sample.value()[0]).isEqualTo(1234567890L);
            assertThat(sample.value()[1]).isEqualTo("42.5");
        }
        
        @Test
        void buildVector_appliesScale() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h:1", "job", "j", "ts", 1000L, "value", 0.5));
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(rows, 100.0);
            
            assertThat(result.result().get(0).value()[1]).isEqualTo("50.0");
        }
        
        @Test
        void buildVector_multipleRows_onePerSeries() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h1:1", "job", "j", "ts", 1000L, "value", 10.0),
                    Map.of("instance", "h2:2", "job", "j", "ts", 1000L, "value", 20.0));
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(rows, 1.0);
            assertThat(result.result()).hasSize(2);
        }
        
        @Test
        void buildMatrix_emptyRows_returnsEmptyMatrix() {
            PrometheusMatrixResult result = OtelMetricsQueryService.buildMatrix(List.of(), 1.0);
            assertThat(result.resultType()).isEqualTo("matrix");
            assertThat(result.result()).isEmpty();
        }
        
        @Test
        void buildMatrix_groupsByInstanceAndJob() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h:1", "job", "j", "bucket", 1000L, "value", 10.0),
                    Map.of("instance", "h:1", "job", "j", "bucket", 1015L, "value", 11.0),
                    Map.of("instance", "h:2", "job", "j", "bucket", 1000L, "value", 20.0));
            PrometheusMatrixResult result = OtelMetricsQueryService.buildMatrix(rows, 1.0);
            
            assertThat(result.result()).hasSize(2);
            MatrixSeries series1 = result.result().get(0);
            assertThat(series1.metric()).containsEntry("instance", "h:1");
            assertThat(series1.values()).hasSize(2);
            assertThat(series1.values().get(0)[0]).isEqualTo(1000L);
            assertThat(series1.values().get(0)[1]).isEqualTo("10.0");
        }
        
        @Test
        void buildMatrix_appliesScale() {
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h:1", "job", "j", "bucket", 1000L, "value", 1.5));
            PrometheusMatrixResult result = OtelMetricsQueryService.buildMatrix(rows, 100.0);
            assertThat(result.result().get(0).values().get(0)[1]).isEqualTo("150.0");
        }
    }
    
    // ─── 工具函数测试 ─────────────────────────────────────────────────────────────
    
    @Nested
    class Helpers {
        
        @Test
        void needsFilter_null_false() {
            assertThat(OtelMetricsQueryService.needsFilter(null)).isFalse();
        }
        
        @Test
        void needsFilter_matchAll_false() {
            assertThat(OtelMetricsQueryService.needsFilter(".+")).isFalse();
        }
        
        @Test
        void needsFilter_specificValue_true() {
            assertThat(OtelMetricsQueryService.needsFilter("localhost:8081")).isTrue();
        }
        
        @Test
        void parseRateWindow_1m_returns60() {
            assertThat(OtelMetricsQueryService.parseRateWindow("1m")).isEqualTo(60L);
        }
        
        @Test
        void parseRateWindow_5m_returns300() {
            assertThat(OtelMetricsQueryService.parseRateWindow("5m")).isEqualTo(300L);
        }
        
        @Test
        void parseRateWindow_null_defaults60() {
            assertThat(OtelMetricsQueryService.parseRateWindow(null)).isEqualTo(60L);
        }
    }
}
