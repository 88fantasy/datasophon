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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OtelMetricsQueryServiceTest {

    @Test
    void instantAgg_deduplicatesLatestSamplePerFilesystemSeriesBeforeSumming() {
        String sql = OtelMetricsQueryService.buildInstantAggSql("sum", true, true, null, null, "otel_metrics_gauge");
        assertThat(sql).contains("attributes['path']");
        assertThat(sql).contains("attributes['device']");
        assertThat(sql).contains("attributes['fstype']");
        assertThat(sql).contains("attributes['mountpoint']");
        assertThat(sql).contains("PARTITION BY " + OtelMetricsQueryService.INST_EXPR);
        assertThat(sql).contains("CAST(attributes['path'] AS STRING)");
        assertThat(sql).contains("CAST(attributes['mountpoint'] AS STRING)");
    }

    @Test
    void attrFilters_useRegexpWhenFilterValueContainsRegexpMetaCharacters() {
        String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                false, false, Map.of("fstype", "ext.*|xfs"), Map.of("mountpoint", ".*pod.*"),
                List.of(), "otel_metrics_gauge");
        assertThat(sql).contains("REGEXP :af_fstype");
        assertThat(sql).contains("NOT REGEXP :afne_mountpoint");
    }

    @Test
    void instantAgg_count_containsCountFunction() {
        String sql = OtelMetricsQueryService.buildInstantAggSql(
                "count", false, false, null, null, "otel_metrics_gauge");
        assertThat(sql).containsIgnoringCase("COUNT(value)");
    }

    @Test
    void rangeHistogramFieldRate_countAndSum_useHistogramTableAndSeriesKeyPartition() {
        String countSql = OtelMetricsQueryService.buildRangeFieldRateSql(
                "count", false, false, Map.of("vol_name", "prod-fs"), null,
                List.of("method"), "otel_metrics_histogram");
        String sumSql = OtelMetricsQueryService.buildRangeFieldRateSql(
                "sum", false, false, Map.of("vol_name", "prod-fs"), null,
                List.of("method"), "otel_metrics_histogram");

        for (String sql : List.of(countSql, sumSql)) {
            assertThat(sql).contains("FROM otel.otel_metrics_histogram");
            assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
            assertThat(sql).contains("PARTITION BY instance, job, method, series_key");
            assertThat(sql).contains("reset_count >= prev_reset_count");
            assertThat(sql).contains("SUM(rate) AS value");
            assertThat(sql).contains("attributes['vol_name']");
            assertThat(sql).contains("attributes['method']");
        }
        assertThat(countSql).contains("count AS value");
        assertThat(sumSql).contains("sum AS value");
    }

    @Test
    void allowedAttrFilterKeys_includeJuicefsDimensions() {
        assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                .contains("vol_name", "mp", "method");
        String sql = OtelMetricsQueryService.buildInstantAggSql(
                "sum", false, false, Map.of("vol_name", "prod-fs"), null, "otel_metrics_gauge");
        assertThat(sql).contains("attributes['vol_name']");
        assertThat(sql).contains("attributes['mp']");
        assertThat(sql).contains("attributes['method']");
    }

    @Test
    void allowedAttrFilterKeys_includeZooKeeperJvmDimensions() {
        assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                .contains("pool", "gc");
        String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                false, false, null, null, List.of("pool"), "otel_metrics_gauge");
        assertThat(sql).contains("attributes['pool']");
    }

    @Test
    void allowedAttrFilterKeys_includeCollectorPipelineDimensions() {
        assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                .contains("exporter", "receiver", "processor", "transport");
        String sql = OtelMetricsQueryService.buildRangeRateSql(
                false, false, Map.of("exporter", "awss3/metrics"), null,
                List.of("receiver", "transport"), "otel_metrics_sum");
        assertThat(sql).contains("attributes['exporter']");
        assertThat(sql).contains("attributes['receiver']");
        assertThat(sql).contains("attributes['transport']");
    }

    @Test
    void allowedAttrFilterKeys_includeDolphinSchedulerDimensions() {
        assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                .contains("area", "result", "status", "level", "cause");
        String sql = OtelMetricsQueryService.buildRangeRateSql(
                false, false, Map.of("result", "success"), null,
                Map.of("status", "5.."), null,
                List.of("level", "cause"), "otel_metrics_sum");
        assertThat(sql).contains("attributes['result']");
        assertThat(sql).contains("attributes['status']");
        assertThat(sql).contains("attributes['level']");
        assertThat(sql).contains("attributes['cause']");
    }

    @Test
    void rangeSummaryFieldRate_count_useSummaryTableAndSeriesKeyPartition() {
        String sql = OtelMetricsQueryService.buildRangeFieldRateSql(
                "count", false, false, Map.of("gc", "G1 Young Generation"), null,
                List.of("gc"), "otel_metrics_summary");

        assertThat(sql).contains("FROM otel.otel_metrics_summary");
        assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
        assertThat(sql).contains("PARTITION BY instance, job, gc, series_key");
        assertThat(sql).contains("reset_count >= prev_reset_count");
        assertThat(sql).contains("SUM(rate) AS value");
        assertThat(sql).contains("attributes['gc']");
        assertThat(sql).contains("count AS value");
    }

    // ─── SQL 生成测试 ────────────────────────────────────────────────────────────

    @Nested
    class SqlBuilding {

        // ── instance/job 列契约 ──

        @Test
        void allBuilders_useFlattenedServiceColumnsForInstanceAndJob() {
            // Doris OTel schema 将 instance/job 写入扁平 service 列，不能从 attributes 取。
            String noAgg = OtelMetricsQueryService.buildInstantNoAggSql(false, false, null, null, "otel_metrics_gauge");
            String agg = OtelMetricsQueryService.buildInstantAggSql(
                    "sum", false, false, null, null, "otel_metrics_gauge");
            String gauge = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            String rate = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of(), "otel_metrics_gauge");
            String summary = OtelMetricsQueryService.buildRangeSummarySql();
            String histogram = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());

            for (String sql : List.of(noAgg, agg, gauge, rate, summary, histogram)) {
                assertThat(sql)
                        .as("SQL must reference flattened service columns for instance/job")
                        .contains("service_instance_id")
                        .contains("service_name");
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
        void instantNoAgg_usesWindowSubqueryAndNamedParams() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(false, false, null, null, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("ROW_NUMBER()");
            assertThat(sql).contains("WHERE rn = 1");
            assertThat(sql).doesNotContainIgnoringCase("QUALIFY");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":evalTime");
            assertThat(sql).doesNotContain(":instance");
            assertThat(sql).doesNotContain(":job");
        }

        @Test
        void instantNoAgg_withInstanceJobFilters_appendsServiceColumnRegexp() {
            String sql = OtelMetricsQueryService.buildInstantNoAggSql(true, true, null, null, "otel_metrics_gauge");
            assertThat(sql).contains(":instance");
            assertThat(sql).contains(":job");
            assertThat(sql).containsIgnoringCase("REGEXP");
            assertThat(sql).contains("service_instance_id REGEXP :instance");
            assertThat(sql).contains("service_name REGEXP :job");
        }

        // ── instant 聚合 ──

        @Test
        void instantAgg_sum_containsSumAndUsesWindowSubquery() {
            String sql = OtelMetricsQueryService.buildInstantAggSql(
                    "sum", false, false, null, null, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("SUM(value)");
            assertThat(sql).containsIgnoringCase("ROW_NUMBER()");
            assertThat(sql).contains("WHERE rn = 1");
            assertThat(sql).doesNotContainIgnoringCase("QUALIFY");
        }

        @Test
        void instantAgg_max_containsMaxFunction() {
            String sql = OtelMetricsQueryService.buildInstantAggSql(
                    "max", false, false, null, null, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("MAX(value)");
        }

        @Test
        void instantAgg_count_containsCountFunction() {
            String sql = OtelMetricsQueryService.buildInstantAggSql(
                    "count", false, false, null, null, "otel_metrics_gauge");
            assertThat(sql).containsIgnoringCase("COUNT(value)");
        }

        // ── summary ──

        @Test
        void rangeSummary_containsLateralViewAndQuantile() {
            String sql = OtelMetricsQueryService.buildRangeSummarySql();
            assertThat(sql).containsIgnoringCase("LATERAL VIEW EXPLODE");
            assertThat(sql).contains("quantile_values");
            assertThat(sql).contains("STRUCT_ELEMENT(qv, 'quantile')");
            assertThat(sql).contains("STRUCT_ELEMENT(qv, 'value')");
            assertThat(sql).doesNotContain("qv.quantile");
            assertThat(sql).doesNotContain("qv.value");
            assertThat(sql).contains(":quantile");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":start");
            assertThat(sql).contains(":end");
            assertThat(sql).containsIgnoringCase("BETWEEN FROM_UNIXTIME");
            assertThat(sql).contains("resource_attributes");
        }

        // ── histogram range 分位数 ──

        @Test
        void rangeHistogram_containsPosexplodeElementAtAndQuantile() {
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());
            assertThat(sql).containsIgnoringCase("LATERAL VIEW POSEXPLODE");
            assertThat(sql).contains("bucket_counts");
            assertThat(sql).contains("explicit_bounds");
            // prev_bucket_counts 对齐用 JOIN(非 element_at):Doris 对 LAG() 结果 + 非常量下标的
            // element_at 会报 "must be constant"(真实 Doris 实测确认)
            assertThat(sql).containsIgnoringCase("LEFT JOIN prev_exploded");
            assertThat(sql).contains("c.pos = p.pos");
            assertThat(sql).containsIgnoringCase("element_at(c.explicit_bounds, c.pos + 1)");
            assertThat(sql).contains(":metric");
            assertThat(sql).contains(":start");
            assertThat(sql).contains(":end");
            assertThat(sql).contains(":step");
            assertThat(sql).contains(":rateWindow");
            assertThat(sql).contains(":quantile");
            assertThat(sql).contains("otel_metrics_histogram");
            assertThat(sql).contains("resource_attributes");
        }

        @Test
        void rangeHistogram_usesAdjacentSampleDeltaLikeRateQuery() {
            // 与 buildRangeRateSql 相同的相邻采样对差分套路；ARRAY 列不直接 LAG，避免 3.0.8
            // 尝试把 ARRAY 转成 VARCHAR 而失败，改用 prev_ts 回 join ordered 取上一行 bucket_counts。
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());
            assertThat(sql).containsIgnoringCase("LAG(ts)");
            assertThat(sql).doesNotContainIgnoringCase("LAG(bucket_counts)");
            assertThat(sql).containsIgnoringCase("prev_rows AS");
            assertThat(sql).contains("JOIN ordered p");
            assertThat(sql).contains("d.prev_ts = p.ts");
            assertThat(sql).contains("prev_ts IS NOT NULL AND ts > prev_ts");
        }

        @Test
        void rangeHistogram_partitionsBySeriesKeyToAvoidCrossSeriesPairing() {
            // Codex 审查发现：同一 instance/job 下可能有多条实际 series(如 APISIX 的
            // route/service/node 维度未被 groupBy 覆盖时)，若不按完整 series 身份分区，
            // LAG/JOIN 会把不同 series 的采样点错配，产出跨 series 的伪 delta。
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());
            assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
            assertThat(sql).contains("PARTITION BY instance, job, series_key");
            assertThat(sql).contains("AND c.series_key = p.series_key");
        }

        @Test
        void rangeHistogram_guardsAgainstCounterResetUsingHistCount() {
            // 与 buildRangeRateSql 的 value >= prev_val 守卫一致：用 count 列的 LAG 判断是否
            // 发生计数器重置(如进程重启)，reset 时整对采样丢弃，而不是逐桶 clamp 到 0
            // (clamp 会静默丢失 reset 后的真实新增量，使分位数系统性偏低)。
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());
            assertThat(sql).contains("count AS hist_count");
            assertThat(sql).containsIgnoringCase("LAG(hist_count)");
            assertThat(sql).contains("AS prev_hist_count");
            assertThat(sql).contains("hist_count >= prev_hist_count");
        }

        @Test
        void rangeHistogram_interpolatesWithinBucketAndDegradesOnOverflowBucket() {
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of());
            // 溢出桶（upper_bound IS NULL）退化为返回 lower_bound
            assertThat(sql).contains("WHEN upper_bound IS NULL THEN 0");
            // 普通桶线性插值：(quantile*total - lower_cum) / bucket_delta * (upper_bound - lower_bound)
            assertThat(sql).contains(":quantile * total_count - COALESCE(lower_cum, 0)) / bucket_delta");
            assertThat(sql).containsIgnoringCase("ROW_NUMBER()");
            assertThat(sql).contains("WHERE rn = 1");
            assertThat(sql).doesNotContainIgnoringCase("QUALIFY");
        }

        @Test
        void rangeHistogram_withInstanceJobFilter_appendsResourceAttributesRegexp() {
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    true, true, null, null, List.of());
            assertThat(sql).contains(":instance");
            assertThat(sql).contains(":job");
            assertThat(sql).containsIgnoringCase("REGEXP");
        }

        @Test
        void rangeHistogram_withGroupBy_addsExtraDimension() {
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, null, null, List.of("type"));
            assertThat(sql).contains("attributes['type']");
            assertThat(sql).contains("AS type");
            assertThat(sql).contains("PARTITION BY instance, job, type");
            // JOIN 对齐 curr/prev 时也要按 groupBy 维度对齐,否则不同 type 的桶会误配对
            assertThat(sql).contains("AND c.type = p.type");
            // series_key 对齐仍须叠加在 groupBy 维度之上(groupBy 粒度可能比实际 series 更粗)
            assertThat(sql).contains("AND c.series_key = p.series_key");
            // exploded CTE 的 SELECT 列表必须用 c.type 限定(curr_exploded c LEFT JOIN prev_exploded p
            // 两边都有同名 groupBy 列,不限定会被 Doris 判 "type is ambiguous";
            // 真实沙箱数据复现过此 bug,见 JuiceFS J09 groupBy=['mp'] 场景)
            assertThat(sql).contains("c.type AS type");
        }

        @Test
        void rangeHistogram_withAttrFilter_appendsAttributesCastEquals() {
            String sql = OtelMetricsQueryService.buildRangeHistogramSql(
                    false, false, Map.of("service", "order-service"), null, List.of());
            assertThat(sql).contains("attributes['service']");
            assertThat(sql).contains(":af_service");
        }

        @Test
        void rangeHistogramFieldRate_countAndSum_useHistogramTableAndSeriesKeyPartition() {
            String countSql = OtelMetricsQueryService.buildRangeFieldRateSql(
                    "count", false, false, Map.of("vol_name", "prod-fs"), null,
                    List.of("method"), "otel_metrics_histogram");
            String sumSql = OtelMetricsQueryService.buildRangeFieldRateSql(
                    "sum", false, false, Map.of("vol_name", "prod-fs"), null,
                    List.of("method"), "otel_metrics_histogram");

            for (String sql : List.of(countSql, sumSql)) {
                assertThat(sql).contains("FROM otel.otel_metrics_histogram");
                assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
                assertThat(sql).contains("PARTITION BY instance, job, method, series_key");
                assertThat(sql).contains("reset_count >= prev_reset_count");
                assertThat(sql).contains("SUM(rate) AS value");
                assertThat(sql).contains("attributes['vol_name']");
                assertThat(sql).contains("attributes['method']");
            }
            assertThat(countSql).contains("count AS value");
            assertThat(sumSql).contains("sum AS value");
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
        void rangeGauge_withRegexpAttrFilters_appendsRegexpClauses() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, Map.of("fstype", "ext.*|xfs"), Map.of("mountpoint", ".*pod.*"),
                    List.of(), "otel_metrics_gauge");
            assertThat(sql).contains("attributes['fstype']");
            assertThat(sql).contains("REGEXP :af_fstype");
            assertThat(sql).contains("attributes['mountpoint']");
            assertThat(sql).contains("NOT REGEXP :afne_mountpoint");
        }

        @Test
        void explicitRegexFilters_appendRegexpClausesAndBindValuesAsParams() {
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null,
                    Map.of("status", "5.."), Map.of("status", "2.."),
                    List.of(), "otel_metrics_sum");
            assertThat(sql).contains("REGEXP :afr_status");
            assertThat(sql).contains("NOT REGEXP :afnr_status");
            assertThat(sql).doesNotContain("5..");
            assertThat(sql).doesNotContain("2..");
        }

        @Test
        void explicitRegexFilters_ignoreNonWhitelistKey() {
            String sql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null,
                    Map.of("status", "5..", "'; DROP TABLE otel_metrics_gauge; --", ".*"),
                    Map.of("bad_key", ".*"),
                    List.of(), "otel_metrics_gauge");
            assertThat(sql).contains("REGEXP :afr_status");
            assertThat(sql).doesNotContain("DROP TABLE");
            assertThat(sql).doesNotContain(":afr_';");
            assertThat(sql).doesNotContain(":afnr_bad_key");
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

        @Test
        void rangeRate_withRustfsGroupByKeys_addsExtraColsToPartitionBy() {
            // op/drive/server/status_class 是 RustFS 看板需要的属性维度（见 Phase 3 白名单扩容）
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of("op"), "otel_metrics_sum");
            assertThat(sql).contains("attributes['op']");
            assertThat(sql).contains("AS op");
            assertThat(sql).contains("PARTITION BY instance, job, op");
        }

        @Test
        void rangeRate_partitionsBySeriesKeyToAvoidCrossSeriesPairing() {
            // Codex 复审发现：RustFS 的 rustfs_s3_operations_total 带 bucket+op 标签，但
            // groupBy=['op'] 时 bucket 是残余维度；若不按完整 series 身份分区，LAG 会把不同
            // bucket 的采样点错配，产出跨 series 的伪 rate。修法与 buildRangeHistogramSql 一致。
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of("op"), "otel_metrics_sum");
            assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
            assertThat(sql).contains("PARTITION BY instance, job, op, series_key");
        }

        @Test
        void rangeRate_partitionsBySeriesKeyEvenWithoutGroupBy() {
            // 无 groupBy 时（如 RustFS R08/R10）若指标本身仍带隐藏属性维度，同样需要
            // series_key 防止跨 series 串线，而不仅在有 groupBy 时才生效。
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of(), "otel_metrics_sum");
            assertThat(sql).contains("CAST(attributes AS STRING) AS series_key");
            assertThat(sql).contains("PARTITION BY instance, job, series_key");
        }

        @Test
        void rangeRate_aggregatesAcrossSeriesWithSumMatchingPrometheusSemantics() {
            // per_series 先按 series_key 粒度 AVG（同一 series 内多样本平滑，语义与旧行为一致），
            // 最外层再跨落入同一 groupBy 粒度的多条真实 series SUM，等价于
            // Prometheus sum(rate(metric[window])) by (groupByKeys)；
            // 单 series 场景下 SUM 退化为原值，向后兼容。
            String sql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of("op"), "otel_metrics_sum");
            assertThat(sql).contains("per_series AS (");
            assertThat(sql).contains("AVG(rate) AS rate");
            assertThat(sql).contains("SELECT instance, job, op, bucket, SUM(rate) AS value");
            assertThat(sql).contains("FROM per_series");
        }

        @Test
        void allowedAttrFilterKeys_deliberatelyExcludesBucket() {
            // bucket（S3 桶名）故意不进白名单：toValidGroupBy() 若放行会导致 buildExtraSelect()
            // 生成 "CAST(attributes['bucket'] AS STRING) AS bucket"，与本类范围查询已有的
            // 时间分桶列 "FLOOR(...) AS bucket" 别名冲突。见 ALLOWED_ATTR_FILTER_KEYS 类头注释。
            assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS).doesNotContain("bucket");
        }

        @Test
        void allowedAttrFilterKeys_includeJuicefsDimensions() {
            assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                    .contains("vol_name", "mp", "method");
            String sql = OtelMetricsQueryService.buildInstantAggSql(
                    "sum", false, false, Map.of("vol_name", "prod-fs"), null, "otel_metrics_gauge");
            assertThat(sql).contains("attributes['vol_name']");
            assertThat(sql).contains("attributes['mp']");
            assertThat(sql).contains("attributes['method']");
        }

        @Test
        void allowedAttrFilterKeys_includeZooKeeperJvmDimensions() {
            assertThat(OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS)
                    .contains("pool", "gc");
            String memorySql = OtelMetricsQueryService.buildRangeGaugeSql(
                    false, false, null, null, List.of("pool"), "otel_metrics_gauge");
            String gcSql = OtelMetricsQueryService.buildRangeRateSql(
                    false, false, null, null, List.of("gc"), "otel_metrics_sum");
            assertThat(memorySql).contains("attributes['pool']");
            assertThat(gcSql).contains("attributes['gc']");
        }

        // ── 安全性测试 ──

        @Test
        void noSqlBuilderConcatenatesUserInput() {
            // 过滤值通过命名参数绑定，不应出现在 SQL 模板中
            String sqlWithFilters =
                    OtelMetricsQueryService.buildInstantNoAggSql(true, true, null, null, "otel_metrics_gauge");
            assertThat(sqlWithFilters).doesNotContain("'.+'");
            assertThat(sqlWithFilters).doesNotContain("\"localhost\"");
        }

        @Test
        void attrFilter_nonWhitelistKeyIsIgnored() {
            // 非白名单键不得出现在 SQL 中（SQL injection 防护）
            Map<String, String> malicious = Map.of("'; DROP TABLE otel_metrics_gauge; --", "x");
            String sql =
                    OtelMetricsQueryService.buildInstantNoAggSql(false, false, malicious, null, "otel_metrics_gauge");
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

        @Test
        void instant_sumTableUsesOtelMetricsSum() {
            // hostmetrics 的 system.memory.usage 等 non-monotonic sum 指标落 otel_metrics_sum 表
            String noAgg = OtelMetricsQueryService.buildInstantNoAggSql(false, false, null, null, "otel_metrics_sum");
            String agg = OtelMetricsQueryService.buildInstantAggSql(
                    "sum", false, false, null, null, "otel_metrics_sum");
            assertThat(noAgg).contains("FROM otel.otel_metrics_sum");
            assertThat(noAgg).doesNotContain("otel_metrics_gauge");
            assertThat(agg).contains("FROM otel.otel_metrics_sum");
            assertThat(agg).doesNotContain("otel_metrics_gauge");
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
        void buildVector_nullValue_skipsRowInsteadOfThrowing() {
            // 无 agg 聚合查询在评估窗口内无匹配行时仍返回一行、value 为 SQL NULL（如 SUM(空集)）；
            // 复现真实报错：NullPointerException at buildVector 对 row.get("value") 直接拆箱。
            Map<String, Object> row = new HashMap<>();
            row.put("ts", 1000L);
            row.put("value", null);
            PrometheusVectorResult result = OtelMetricsQueryService.buildVector(List.of(row), 1.0);

            assertThat(result.result()).isEmpty();
        }

        @Test
        void buildMatrix_nullValue_skipsRowInsteadOfThrowing() {
            Map<String, Object> row = new HashMap<>();
            row.put("bucket", 1000L);
            row.put("value", null);
            PrometheusMatrixResult result = OtelMetricsQueryService.buildMatrix(List.of(row), 1.0);

            assertThat(result.result()).isEmpty();
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
        void appendAttrFilters_rustfsWhitelistKeys_appendClause() {
            StringBuilder sb = new StringBuilder();
            OtelMetricsQueryService.appendAttrFilters(
                    sb, Map.of("op", "s3:PutObject", "drive", "/data", "server", "10.10.30.132",
                            "status_class", "2xx"),
                    null);
            String s = sb.toString();
            assertThat(s).contains("attributes['op']").contains(":af_op");
            assertThat(s).contains("attributes['drive']").contains(":af_drive");
            assertThat(s).contains("attributes['server']").contains(":af_server");
            assertThat(s).contains("attributes['status_class']").contains(":af_status_class");
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
