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
        
        // ── instance/job 列修复（D3 核心）──
        
        @Test
        void allBuilders_useResourceAttributesForInstanceAndJob() {
            // 每个 builder 的 instance/job 必须来自 resource_attributes，不能用 attributes
            String noAgg = OtelMetricsQueryService.buildInstantNoAggSql(false, false, null, null);
            String agg = OtelMetricsQueryService.buildInstantAggSql("sum", false, false, null, null);
            String gauge = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            String rate = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            String summary = OtelMetricsQueryService.buildRangeSummarySql();
            
            for (String sql : List.of(noAgg, agg, gauge, rate, summary)) {
                assertThat(sql)
                        .as("SQL must reference resource_attributes for instance/job")
                        .contains("resource_attributes");
                assertThat(sql)
                        .as("SQL must NOT use attributes['instance'] (always NULL)")
                        .doesNotContain("attributes['instance']");
                assertThat(sql)
                        .as("SQL must NOT use attributes['job'] (always NULL)")
                        .doesNotContain("attributes['job']");
            }
        }
        
        // ── instant 无聚合 ──
        
        @Test
        void instantNoAgg_containsQualifyAndNamedParams() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(false, false, null, null);
            assertThat(sql).containsIgnoringCase("QUALIFY");
            assertThat(sql).containsIgnoringCase("ROW_NUMBER()");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":evalTime");
            assertThat(sql).doesNotContain(":instance");
            assertThat(sql).doesNotContain(":job");
        }
        
        @Test
        void instantNoAgg_withInstanceJobFilters_appendsResourceAttributesRegexp() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(true, true, null, null);
            assertThat(sql).contains(":instance");
            assertThat(sql).contains(":job");
            assertThat(sql).containsIgnoringCase("REGEXP");
            // 过滤列必须是 resource_attributes
            assertThat(sql).contains("resource_attributes['service']['instance']['id']");
            assertThat(sql).contains("resource_attributes['service']['name']");
        }
        
        // ── instant 聚合 ──
        
        @Test
        void instantAgg_sum_containsSumAndQualify() {
            String sql = OtelMetricsQueryService.buildInstantAggSql("sum", false, false, null, null);
            assertThat(sql).containsIgnoringCase("SUM(value)");
            assertThat(sql).containsIgnoringCase("QUALIFY");
        }
        
        @Test
        void instantAgg_max_containsMaxFunction() {
            String sql = OtelMetricsQueryService.buildInstantAggSql("max", false, false, null, null);
            assertThat(sql).containsIgnoringCase("MAX(value)");
        }
        
        // ── summary ──
        
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
        
        // ── gauge range ──
        
        @Test
        void rangeGauge_containsFloorAndAvgAndBetween() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("FLOOR(");
            assertThat(sql).containsIgnoringCase("AVG(value)");
            assertThat(sql).containsIgnoringCase("BETWEEN");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":start");
            assertThat(sql).contains(":end");
            assertThat(sql).contains(":metric");
        }
        
        @Test
        void rangeGauge_withAttrFilter_appendsAttributesCastEquals() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, Map.of("group", "fe"), null, List.of(), "otel_metrics_gauge");
            assertThat(sql).contains("attributes['group']");
            assertThat(sql).contains(":af_group");
            assertThat(sql).containsIgnoringCase("= :af_group");
        }
        
        @Test
        void rangeGauge_withAttrFilterNe_appendsNotEqualsClause() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, Map.of("device", "lo"), List.of(), "otel_metrics_sum");
            assertThat(sql).contains("attributes['device']");
            assertThat(sql).contains(":afne_device");
            assertThat(sql).contains("!= :afne_device");
        }
        
        @Test
        void rangeGauge_withGroupBy_addsExtraSelectAndGroupByColumns() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of("mode"), "otel_metrics_sum");
            assertThat(sql).contains("attributes['mode']");
            assertThat(sql).contains("AS mode");
            // GROUP BY must include the attributes expression for 'mode'
            assertThat(sql).containsIgnoringCase("attributes['mode']");
        }
        
        @Test
        void rangeGauge_withMultiGroupBy_addsAllDimensions() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of("type", "path"), "otel_metrics_gauge");
            assertThat(sql).contains("attributes['type']");
            assertThat(sql).contains("attributes['path']");
            assertThat(sql).contains("AS type");
            assertThat(sql).contains("AS path");
        }
        
        // ── rate range ──
        
        @Test
        void rangeRate_containsLagAndRateWindow() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("LAG(ts)");
            assertThat(sql).containsIgnoringCase("LAG(value)");
            assertThat(sql).contains(":rateWindow");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains("prev_val");
            assertThat(sql).contains("prev_ts");
        }
        
        @Test
        void rangeRate_withInstanceFilter_appendsRegexpToOrdered() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    true, false, null, null, List.of(), "otel_metrics_gauge");
            assertThat(sql).contains(":instance");
            assertThat(sql).doesNotContain(":job");
        }
        
        @Test
        void rangeRate_withGroupBy_addsExtraColsToPartitionBy() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of("mode"), "otel_metrics_sum");
            assertThat(sql).contains("attributes['mode']");
            assertThat(sql).contains("AS mode");
            // PARTITION BY must include mode for correct per-mode rate
            assertThat(sql).contains("PARTITION BY instance, job, mode");
        }
        
        // ── 安全性测试 ──
        
        @Test
        void noSqlBuilderConcatenatesUserInput() {
            // 过滤值通过命名参数绑定，不应出现在 SQL 模板中
            String sqlWithFilters = OtelMetricsQueryService.buildInstantNoAggSql(true, true, null, null);
            assertThat(sqlWithFilters).doesNotContain("'.+'");
            assertThat(sqlWithFilters).doesNotContain("\"localhost\"");
        }
        
        @Test
        void attrFilter_nonWhitelistKeyIsIgnored() {
            // 非白名单键不得出现在 SQL 中（SQL injection 防护）
            Map<String, String> malicious = Map.of("'; DROP TABLE otel_metrics_gauge; --", "x");
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(false, false, malicious, null);
            assertThat(sql).doesNotContain("DROP TABLE");
            assertThat(sql).doesNotContain(":af_");
        }
        
        @Test
        void attrFilter_sumTableUsesOtelMetricsSum() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of(), "otel_metrics_sum");
            assertThat(sql).contains("otel_metrics_sum");
            assertThat(sql).doesNotContain("otel_metrics_gauge");
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
        void buildVector_extraLabelColumns_includedInMetric() {
            // groupBy 带来的 'mode' 列也应出现在 metric labels 中
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h:1", "job", "doris", "mode", "idle", "ts", 1000L, "value", 0.8));
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(rows, 1.0);
            
            assertThat(result.result()).hasSize(1);
            assertThat(result.result().get(0).metric()).containsEntry("mode", "idle");
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
        
        @Test
        void buildMatrix_extraLabelColumns_splitIntoSeparateSeries() {
            // groupBy=mode: 同 instance/job 但不同 mode → 两个独立 series
            List<Map<String, Object>> rows = List.of(
                    Map.of("instance", "h:1", "job", "doris", "mode", "idle", "bucket", 1000L, "value", 0.7),
                    Map.of("instance", "h:1", "job", "doris", "mode", "user", "bucket", 1000L, "value", 0.2));
            PrometheusMatrixResult result = OtelMetricsQueryService.buildMatrix(rows, 1.0);
            
            assertThat(result.result()).hasSize(2);
            assertThat(result.result().stream()
                    .anyMatch(s -> "idle".equals(s.metric().get("mode")))).isTrue();
            assertThat(result.result().stream()
                    .anyMatch(s -> "user".equals(s.metric().get("mode")))).isTrue();
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
        void parseRateWindow_2m_returns120() {
            assertThat(OtelMetricsQueryService.parseRateWindow("2m")).isEqualTo(120L);
        }
        
        @Test
        void parseRateWindow_null_defaults60() {
            assertThat(OtelMetricsQueryService.parseRateWindow(null)).isEqualTo(60L);
        }
        
        @Test
        void appendAttrFilters_nullMaps_noExceptions() {
            // null maps must be handled gracefully (no NPE)
            StringBuilder sb = new StringBuilder("SELECT 1");
            OtelMetricsQueryService.appendAttrFilters(sb, null, null);
            assertThat(sb.toString()).isEqualTo("SELECT 1");
        }
        
        @Test
        void appendAttrFilters_whitelistKey_appendsClause() {
            StringBuilder sb = new StringBuilder();
            OtelMetricsQueryService.appendAttrFilters(sb, Map.of("group", "fe"), null);
            assertThat(sb.toString()).contains("attributes['group']");
            assertThat(sb.toString()).contains(":af_group");
        }
        
        @Test
        void appendAttrFilters_neKey_appendsNotEqualsClause() {
            StringBuilder sb = new StringBuilder();
            OtelMetricsQueryService.appendAttrFilters(sb, null, Map.of("device", "lo"));
            assertThat(sb.toString()).contains("!= :afne_device");
        }
        
        @Test
        void appendAttrFilters_nonWhitelistKey_silentlyIgnored() {
            StringBuilder sb = new StringBuilder();
            OtelMetricsQueryService.appendAttrFilters(sb, Map.of("__proto__", "x"), null);
            assertThat(sb.toString()).isEmpty();
        }
    }
}
