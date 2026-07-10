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

import com.datasophon.api.observability.PrometheusMatrixResult.MatrixSeries;
import com.datasophon.api.observability.PrometheusVectorResult.VectorSample;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 从 Doris OTel 指标表查询监控数据，返回 Prometheus wire format，
 * 供前端复用现有渲染层（{@code promql.ts} 的 PrometheusVector/Matrix）。
 *
 * <p>使用 SELECT-only {@code otel_reader} 账号（F1 凭据隔离，不用 root/writer 账号）。
 * SQL 全部走参数化查询（JdbcClient 命名参数），禁止拼接用户输入。
 *
 * <h3>关键 schema 约定（D2 实测确认）</h3>
 * <ul>
 *   <li>Prometheus 抓取指标的 {@code instance} / {@code job} 分别落位于
 *       exporter 写入的扁平列 {@code service_instance_id} / {@code service_name}。
 *   <li>Doris 业务维度（{@code group} / {@code type} / {@code mode} /
 *       {@code path} / {@code device}）落在 {@code attributes} MAP。
 *   <li>FE summary 指标（{@code doris_fe_query_latency_ms} 等）写入
 *       {@code otel_metrics_summary}；BE counter（cpu/network/compaction）写入
 *       {@code otel_metrics_sum}；其余写入 {@code otel_metrics_gauge}。
 * </ul>
 */
@Component
public class OtelMetricsQueryService {

    private static final String MATCH_ALL = ".+";

    /** Prometheus 抓取指标的 instance 字段（Doris OTel schema 的扁平列）。 */
    static final String INST_EXPR = "service_instance_id";

    /** Prometheus 抓取指标的 job 字段（Doris OTel schema 的扁平列）。 */
    static final String JOB_EXPR = "service_name";

    /**
     * attributes MAP 的属性过滤键白名单。
     * 键名会被直接拼入 SQL（如 {@code attributes['group']}），必须严格白名单化；
     * 对应值通过命名参数绑定（af_key / afne_key），不存在注入风险。
     *
     * <p>RustFS 指标的 {@code bucket} 属性（S3 桶名）故意不在此白名单：
     * {@link #buildExtraSelect} 把属性列直接按键名起别名，会与本类范围查询里已存在的
     * {@code FLOOR(...) AS bucket}（时间分桶）别名冲突。
     */
    static final Set<String> ALLOWED_ATTR_FILTER_KEYS =
            Set.of("group", "type", "mode", "path", "device", "fstype", "mountpoint", "state",
                    "code", "service", "route", "node", "consumer", "name",
                    "op", "drive", "server", "status_class", "vol_name", "mp", "method", "pool", "gc",
                    "exporter", "receiver", "processor", "transport",
                    "area", "result", "status", "level", "cause");

    private static final List<String> INSTANT_SERIES_ATTR_KEYS =
            List.of("group", "type", "mode", "path", "device", "fstype", "mountpoint", "state",
                    "code", "service", "route", "node", "consumer", "name",
                    "op", "drive", "server", "status_class", "vol_name", "mp", "method", "pool", "gc",
                    "exporter", "receiver", "processor", "transport",
                    "area", "result", "status", "level", "cause");

    private final ClusterServiceRoleInstanceService roleService;
    private final OtelDorisReaderFactory readerFactory;

    public OtelMetricsQueryService(ClusterServiceRoleInstanceService roleService,
                                   OtelDorisReaderFactory readerFactory) {
        this.roleService = roleService;
        this.readerFactory = readerFactory;
    }

    // ─── 公开查询接口 ──────────────────────────────────────────────────────────────

    /**
     * Instant 查询，返回 Prometheus vector 格式。查询 {@code otel_metrics_gauge} 表；
     * 若指标是 non-monotonic sum（如 hostmetrics 的 {@code system.memory.usage}），
     * 用 {@link #queryInstant(Integer, String, String, double, String, String, Map, Map, long, String)}
     * 并传 {@code table="sum"}。
     *
     * @param agg       聚合函数（"sum"/"max"；null 表示不聚合，每 series 各一样本）
     * @param scale     值乘数（如 100.0 将 ratio 转为百分比；1.0 表示不变）
     * @param filters   可选属性等值过滤（白名单键，如 {@code {"group":"fe","type":"used"}}）
     * @param filtersNe 可选属性不等过滤（如 {@code {"device":"lo"}} 表示 device != "lo"）
     */
    public PrometheusVectorResult queryInstant(Integer clusterId, String metric,
                                               String agg, double scale,
                                               String instance, String job,
                                               Map<String, String> filters,
                                               Map<String, String> filtersNe,
                                               long evalTime) {
        return queryInstant(clusterId, metric, agg, scale, instance, job, filters, filtersNe, evalTime, "gauge");
    }

    /**
     * Instant 查询（可指定表），返回 Prometheus vector 格式。
     *
     * @param table 查询的指标表："gauge"（默认）或 "sum"（non-monotonic sum，如 hostmetrics 的
     *              {@code system.memory.usage} / {@code system.linux.memory.available} /
     *              {@code system.filesystem.usage}）
     */
    public PrometheusVectorResult queryInstant(Integer clusterId, String metric,
                                               String agg, double scale,
                                               String instance, String job,
                                               Map<String, String> filters,
                                               Map<String, String> filtersNe,
                                               long evalTime, String table) {
        return queryInstant(clusterId, metric, agg, scale, instance, job, filters, filtersNe,
                null, null, evalTime, table);
    }

    public PrometheusVectorResult queryInstant(Integer clusterId, String metric,
                                               String agg, double scale,
                                               String instance, String job,
                                               Map<String, String> filters,
                                               Map<String, String> filtersNe,
                                               Map<String, String> filtersRegex,
                                               Map<String, String> filtersNotRegex,
                                               long evalTime, String table) {
        JdbcClient client = createReader(clusterId);
        String otelTable = "sum".equalsIgnoreCase(table) ? "otel_metrics_sum" : "otel_metrics_gauge";
        boolean hasAgg = agg != null && !agg.isBlank();
        String sql = hasAgg
                ? buildInstantAggSql(agg, needsFilter(instance), needsFilter(job),
                        filters, filtersNe, filtersRegex, filtersNotRegex, otelTable)
                : buildInstantNoAggSql(needsFilter(instance), needsFilter(job),
                        filters, filtersNe, filtersRegex, filtersNotRegex, otelTable);

        JdbcClient.StatementSpec spec = client.sql(sql)
                .param("metric", metric)
                .param("evalTime", evalTime);
        if (needsFilter(instance)) {
            spec = spec.param("instance", instance);
        }
        if (needsFilter(job)) {
            spec = spec.param("job", job);
        }
        spec = bindAttrFilterParams(spec, filters, filtersNe, filtersRegex, filtersNotRegex);

        List<Map<String, Object>> rows = spec.query().listOfRows();
        return buildVector(rows, scale);
    }

    /**
     * Range 查询，返回 Prometheus matrix 格式。
     *
     * @param rateWindow  速率窗口（"1m"/"5m"；null 表示 gauge，直接取平均；table="histogram" 时
     *                    复用作相邻采样对差分的回溯窗口）
     * @param scale       值乘数
     * @param filters     可选属性等值过滤（白名单键）
     * @param filtersNe   可选属性不等过滤（白名单键）
     * @param groupByKeys 额外 GROUP BY 维度（attributes MAP 键，白名单；如 {@code ["mode","path"]}）
     * @param table       查询的指标表："gauge"（默认）/"sum"/"summary"/"histogram"
     * @param quantile    分位数（0~1），table="summary"/"histogram" 时生效
     */
    public PrometheusMatrixResult queryRange(Integer clusterId, String metric,
                                             String rateWindow, double scale,
                                             String instance, String job,
                                             Map<String, String> filters,
                                             Map<String, String> filtersNe,
                                             List<String> groupByKeys,
                                             long start, long end, long step,
                                             String table, double quantile) {
        return queryRange(clusterId, metric, rateWindow, scale, instance, job, filters, filtersNe,
                groupByKeys, start, end, step, table, quantile, null);
    }

    /**
     * Range 查询，返回 Prometheus matrix 格式。
     *
     * @param field histogram/summary 表专用："count"/"sum" 表示对 count/sum 列做 counter-rate；
     *              缺省或 "quantile" 表示按 {@code quantile} 查询分位数。
     */
    public PrometheusMatrixResult queryRange(Integer clusterId, String metric,
                                             String rateWindow, double scale,
                                             String instance, String job,
                                             Map<String, String> filters,
                                             Map<String, String> filtersNe,
                                             List<String> groupByKeys,
                                             long start, long end, long step,
                                             String table, double quantile, String field) {
        return queryRange(clusterId, metric, rateWindow, scale, instance, job, filters, filtersNe,
                null, null, groupByKeys, start, end, step, table, quantile, field);
    }

    public PrometheusMatrixResult queryRange(Integer clusterId, String metric,
                                             String rateWindow, double scale,
                                             String instance, String job,
                                             Map<String, String> filters,
                                             Map<String, String> filtersNe,
                                             Map<String, String> filtersRegex,
                                             Map<String, String> filtersNotRegex,
                                             List<String> groupByKeys,
                                             long start, long end, long step,
                                             String table, double quantile, String field) {
        JdbcClient client = createReader(clusterId);

        List<String> validGroupBy = toValidGroupBy(groupByKeys);
        boolean fieldRate = "count".equalsIgnoreCase(field) || "sum".equalsIgnoreCase(field);

        if ("summary".equalsIgnoreCase(table) && fieldRate) {
            String sql = buildRangeFieldRateSql(field.toLowerCase(), needsFilter(instance), needsFilter(job),
                    filters, filtersNe, filtersRegex, filtersNotRegex, validGroupBy, "otel_metrics_summary");
            JdbcClient.StatementSpec spec = client.sql(sql)
                    .param("metric", metric)
                    .param("start", start)
                    .param("end", end)
                    .param("step", step)
                    .param("rateWindow", parseRateWindow(rateWindow));
            if (needsFilter(instance)) {
                spec = spec.param("instance", instance);
            }
            if (needsFilter(job)) {
                spec = spec.param("job", job);
            }
            spec = bindAttrFilterParams(spec, filters, filtersNe, filtersRegex, filtersNotRegex);
            List<Map<String, Object>> rows = spec.query().listOfRows();
            return buildMatrix(rows, scale);
        }

        if ("summary".equalsIgnoreCase(table)) {
            String sql = buildRangeSummarySql(needsFilter(instance), needsFilter(job),
                    filters, filtersNe, filtersRegex, filtersNotRegex);
            JdbcClient.StatementSpec spec = client.sql(sql)
                    .param("metric", metric)
                    .param("start", start)
                    .param("end", end)
                    .param("step", step)
                    .param("quantile", quantile);
            if (needsFilter(instance)) {
                spec = spec.param("instance", instance);
            }
            if (needsFilter(job)) {
                spec = spec.param("job", job);
            }
            spec = bindAttrFilterParams(spec, filters, filtersNe, filtersRegex, filtersNotRegex);
            List<Map<String, Object>> rows = spec.query().listOfRows();
            return buildMatrix(rows, scale);
        }

        if ("histogram".equalsIgnoreCase(table)) {
            String sql = fieldRate
                    ? buildRangeFieldRateSql(field.toLowerCase(), needsFilter(instance), needsFilter(job),
                            filters, filtersNe, filtersRegex, filtersNotRegex, validGroupBy, "otel_metrics_histogram")
                    : buildRangeHistogramSql(needsFilter(instance), needsFilter(job),
                            filters, filtersNe, filtersRegex, filtersNotRegex, validGroupBy);
            JdbcClient.StatementSpec spec = client.sql(sql)
                    .param("metric", metric)
                    .param("start", start)
                    .param("end", end)
                    .param("step", step)
                    .param("rateWindow", parseRateWindow(rateWindow));
            if (!fieldRate) {
                spec = spec.param("quantile", quantile);
            }
            if (needsFilter(instance)) {
                spec = spec.param("instance", instance);
            }
            if (needsFilter(job)) {
                spec = spec.param("job", job);
            }
            spec = bindAttrFilterParams(spec, filters, filtersNe, filtersRegex, filtersNotRegex);
            List<Map<String, Object>> rows = spec.query().listOfRows();
            return buildMatrix(rows, scale);
        }

        boolean hasRate = rateWindow != null && !rateWindow.isBlank();
        String otelTable = "sum".equalsIgnoreCase(table) ? "otel_metrics_sum" : "otel_metrics_gauge";
        String sql = hasRate
                ? buildRangeRateSql(needsFilter(instance), needsFilter(job), filters, filtersNe,
                        filtersRegex, filtersNotRegex, validGroupBy, otelTable)
                : buildRangeGaugeSql(needsFilter(instance), needsFilter(job), filters, filtersNe,
                        filtersRegex, filtersNotRegex, validGroupBy, otelTable);

        JdbcClient.StatementSpec spec = client.sql(sql)
                .param("metric", metric)
                .param("start", start)
                .param("end", end)
                .param("step", step);
        if (hasRate) {
            spec = spec.param("rateWindow", parseRateWindow(rateWindow));
        }
        if (needsFilter(instance)) {
            spec = spec.param("instance", instance);
        }
        if (needsFilter(job)) {
            spec = spec.param("job", job);
        }
        spec = bindAttrFilterParams(spec, filters, filtersNe, filtersRegex, filtersNotRegex);

        List<Map<String, Object>> rows = spec.query().listOfRows();
        return buildMatrix(rows, scale);
    }

    /**
     * 查询指标的 instance/job 标签值，用于工具栏下拉派生（替代 Prometheus {@code up} 查询）。
     * 联合查询 gauge 和 sum 两表（某些指标只在其中一张）；instance/job 分别从
     * {@code service_instance_id}/{@code service_name} 取。
     */
    public LabelsResult queryLabels(Integer clusterId, String metric) {
        return queryLabels(clusterId, metric, MATCH_ALL);
    }

    public LabelsResult queryLabels(Integer clusterId, String metric, String job) {
        JdbcClient client = createReader(clusterId);
        // UNION 确保同时覆盖 gauge 和 sum 两表里的指标
        String sql = "SELECT DISTINCT " + INST_EXPR + " AS instance,\n"
                + "  " + JOB_EXPR + " AS job,\n"
                + "  CAST(attributes['vol_name'] AS STRING) AS vol_name,\n"
                + "  CAST(attributes['mp'] AS STRING) AS mp,\n"
                + "  CAST(attributes['method'] AS STRING) AS method\n"
                + "FROM otel.otel_metrics_gauge\n"
                + "WHERE metric_name = :metric\n"
                + "  AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)\n"
                + (needsFilter(job) ? "  AND " + JOB_EXPR + " REGEXP :job\n" : "")
                + "UNION\n"
                + "SELECT DISTINCT " + INST_EXPR + " AS instance,\n"
                + "  " + JOB_EXPR + " AS job,\n"
                + "  CAST(attributes['vol_name'] AS STRING) AS vol_name,\n"
                + "  CAST(attributes['mp'] AS STRING) AS mp,\n"
                + "  CAST(attributes['method'] AS STRING) AS method\n"
                + "FROM otel.otel_metrics_sum\n"
                + "WHERE metric_name = :metric\n"
                + "  AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)"
                + (needsFilter(job) ? "\n  AND " + JOB_EXPR + " REGEXP :job" : "");
        JdbcClient.StatementSpec spec = client.sql(sql).param("metric", metric);
        if (needsFilter(job)) {
            spec = spec.param("job", job);
        }
        List<Map<String, Object>> rows = spec.query().listOfRows();
        Set<String> instances = new LinkedHashSet<>();
        Set<String> jobs = new LinkedHashSet<>();
        Map<String, Set<String>> attributes = new LinkedHashMap<>();
        for (String key : List.of("vol_name", "mp", "method")) {
            attributes.put(key, new LinkedHashSet<>());
        }
        for (Map<String, Object> row : rows) {
            Object inst = row.get("instance");
            Object j = row.get("job");
            if (inst != null) {
                instances.add(inst.toString());
            }
            if (j != null) {
                jobs.add(j.toString());
            }
            for (String key : attributes.keySet()) {
                Object value = row.get(key);
                if (value != null) {
                    attributes.get(key).add(value.toString());
                }
            }
        }
        Map<String, List<String>> attrValues = attributes.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
        return new LabelsResult(List.copyOf(instances), List.copyOf(jobs), attrValues);
    }

    /**
     * 统计集群内指定角色的 RUNNING 实例数，用于节点计数类面板（替代 PromQL count(up==1)）。
     * 直接查角色注册表，不查指标表，无需 Doris 连接。
     *
     * @param roleName 角色名（如 "DorisFE" / "DorisBE"），与 meta DDL 的 roles[].name 一致
     * @return RUNNING 实例数
     */
    public int countNodes(Integer clusterId, String roleName) {
        return (int) roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, roleName)
                .stream()
                .filter(r -> ServiceRoleState.RUNNING.equals(r.getServiceRoleState()))
                .count();
    }

    public record LabelsResult(List<String> instances, List<String> jobs, Map<String, List<String>> attributes) {

        public LabelsResult(List<String> instances, List<String> jobs) {
            this(instances, jobs, Map.of());
        }
    }

    // ─── SQL 构建（package-private for testing） ──────────────────────────────────

    static String buildInstantNoAggSql(boolean filterInstance, boolean filterJob,
                                       Map<String, String> filters, Map<String, String> filtersNe,
                                       String otelTable) {
        return buildInstantNoAggSql(filterInstance, filterJob, filters, filtersNe, null, null, otelTable);
    }

    static String buildInstantNoAggSql(boolean filterInstance, boolean filterJob,
                                       Map<String, String> filters, Map<String, String> filtersNe,
                                       Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                       String otelTable) {
        StringBuilder sql = new StringBuilder(
                "SELECT instance, job, value, ts\n"
                        + "FROM (\n"
                        + "  SELECT " + INST_EXPR + " AS instance,\n"
                        + "         " + JOB_EXPR + " AS job,\n"
                        + "         value, UNIX_TIMESTAMP(timestamp) AS ts,\n"
                        + "         ROW_NUMBER() OVER(\n"
                        + "           PARTITION BY " + INST_EXPR + ",\n"
                        + "                        " + JOB_EXPR + "\n"
                        + "           ORDER BY timestamp DESC\n"
                        + "         ) AS rn\n"
                        + "  FROM otel." + otelTable + "\n"
                        + "  WHERE metric_name = :metric\n"
                        + "    AND timestamp >= FROM_UNIXTIME(:evalTime - 300)");
        appendFilters(sql, filterInstance, filterJob);
        appendAttrFilters(sql, filters, filtersNe, filtersRegex, filtersNotRegex);
        sql.append("\n) latest\n"
                + "WHERE rn = 1");
        return sql.toString();
    }

    static String buildInstantAggSql(String agg, boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe,
                                     String otelTable) {
        return buildInstantAggSql(agg, filterInstance, filterJob, filters, filtersNe, null, null, otelTable);
    }

    static String buildInstantAggSql(String agg, boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe,
                                     Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                     String otelTable) {
        String fn = "max".equalsIgnoreCase(agg) ? "MAX" : ("count".equalsIgnoreCase(agg) ? "COUNT" : "SUM");
        StringBuilder inner = new StringBuilder(
                "SELECT value\n"
                        + "FROM (\n"
                        + "  SELECT value,\n"
                        + "         ROW_NUMBER() OVER(\n"
                        + "           PARTITION BY " + INST_EXPR + ",\n"
                        + "                        " + JOB_EXPR
                        + buildExtraGroupBy(INSTANT_SERIES_ATTR_KEYS)
                        + "\n"
                        + "           ORDER BY timestamp DESC\n"
                        + "         ) AS rn\n"
                        + "  FROM otel." + otelTable + "\n"
                        + "  WHERE metric_name = :metric\n"
                        + "    AND timestamp >= FROM_UNIXTIME(:evalTime - 300)");
        appendFilters(inner, filterInstance, filterJob);
        appendAttrFilters(inner, filters, filtersNe, filtersRegex, filtersNotRegex);
        inner.append("\n) latest\n"
                + "WHERE rn = 1");
        return "SELECT " + fn + "(value) AS value, UNIX_TIMESTAMP(NOW()) AS ts FROM (" + inner + ") t";
    }

    static String buildRangeSummarySql() {
        return buildRangeSummarySql(false, false, null, null, null, null);
    }

    static String buildRangeSummarySql(boolean filterInstance, boolean filterJob,
                                       Map<String, String> filters, Map<String, String> filtersNe,
                                       Map<String, String> filtersRegex, Map<String, String> filtersNotRegex) {
        StringBuilder sql = new StringBuilder("SELECT " + INST_EXPR + " AS instance,\n"
                + "       " + JOB_EXPR + " AS job,\n"
                + "       FLOOR(UNIX_TIMESTAMP(s.timestamp) / :step) * :step AS bucket,\n"
                + "       AVG(STRUCT_ELEMENT(qv, 'value')) AS value\n"
                + "FROM otel.otel_metrics_summary s\n"
                + "LATERAL VIEW EXPLODE(s.quantile_values) t AS qv\n"
                + "WHERE s.metric_name = :metric\n"
                + "  AND s.timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)\n"
                + "  AND STRUCT_ELEMENT(qv, 'quantile') = :quantile");
        appendFilters(sql, filterInstance, filterJob);
        appendAttrFilters(sql, filters, filtersNe, filtersRegex, filtersNotRegex);
        sql.append("\n"
                + "GROUP BY " + INST_EXPR + ",\n"
                + "         " + JOB_EXPR + ",\n"
                + "         bucket\n"
                + "ORDER BY instance, job, bucket");
        return sql.toString();
    }

    /**
     * histogram range 分位数查询（p50/p90/p99 等），查 {@code otel_metrics_histogram} 表。
     *
     * <p>OTel {@code HistogramDataPoint.bucket_counts} 语义：每行代表自被采集进程启动以来
     * 落在各桶的累计计数（prometheusreceiver 从 Prometheus 原生 cumulative {@code le} buckets
     * 转换而来，仍随时间单调递增）。要得到某时间窗口内的分位数，须对相邻两次采样的
     * {@code bucket_counts} 逐桶求差（与 {@link #buildRangeRateSql} 处理 counter 的思路一致），
     * 得到窗口内新增计数，再按桶累加定位分位数所在区间并线性插值。
     *
     * <h3>完整 series 身份对齐（Codex 审查修正）</h3>
     * 同一 instance/job 下常常存在多条实际 histogram series（如 APISIX 的 route/service/
     * node/consumer 等维度都不在 {@code groupByKeys} 内时）。若只按 (instance,job,extraCols)
     * 做 {@code LAG}/{@code JOIN}，会把不同 series 的采样点相互配对，产出跨 series 的伪 delta，
     * 分位数被错误样本污染。因此额外引入 {@code CAST(attributes AS STRING) AS series_key}
     * （实测确认对同一 attributes 组合序列化稳定、按 key 字母序），贯穿
     * {@code ordered → with_lag → deltas → curr_exploded/prev_exploded} 直到 {@code exploded}
     * 的 JOIN 键，保证只有同一条原始 series 的相邻两次采样才会配对；到 {@code agg} 阶段才按
     * 调用方要求的 {@code groupByKeys} 粒度对 delta 求和（故意跨 series 聚合），
     * 语义等价于 Prometheus 先 per-series 求 rate 再 {@code sum() by (...)}。
     *
     * <h3>reset 处理（Codex 审查修正）</h3>
     * 与 {@link #buildRangeRateSql} 的 {@code value >= prev_val} 守卫一致：用 histogram 行自带
     * 的 {@code count}（等价 {@code sum(bucket_counts)}）列及其 LAG 判断是否发生了计数器重置
     * （如 APISIX 进程重启），{@code hist_count < prev_hist_count} 时整对采样直接丢弃，
     * 而不是逐桶 {@code GREATEST(...,0)} 把差值压成 0——后者会静默丢失 reset 后的真实新增量，
     * 使分位数系统性偏低。{@code GREATEST(...,0)} 仍保留作为兜底（理论上 reset 守卫生效后每桶
     * 差值不应为负，双重保险防御未预见的边缘情况）。
     *
     * <p>10 段 CTE：{@code ordered}/{@code with_lag}/{@code deltas} 做相邻采样对差分（复用 rate
     * 查询套路，PARTITION BY 含 series_key）。{@code curr_exploded}/{@code prev_exploded} 分别对
     * 当前行、上一行的 {@code bucket_counts} 做 {@code LATERAL VIEW POSEXPLODE}（{@code pos}
     * 0-indexed），按 (instance,job,extraCols,series_key,bucket,pos) JOIN 对齐两次采样的同一个
     * 桶，{@code exploded} 求逐桶差值并用 {@code element_at(explicit_bounds, pos+1)} 取桶上界
     * （溢出桶为 NULL）。之所以不像 summary 查询那样直接
     * {@code element_at(prev_bucket_counts, pos+1)}：Doris 对 {@code LAG()} 作用在 ARRAY 列上的
     * 版本兼容性较差（3.0.8 会尝试把 ARRAY 转成 VARCHAR 而失败）。因此这里只 LAG 标量
     * {@code ts/count}，再用 {@code prev_ts} 回 join {@code ordered} 取上一行 {@code bucket_counts}，
     * 最后用两次 POSEXPLODE + JOIN 按桶位置对齐。
     * {@code agg} 汇总多个采样对/series 的 delta（此处丢弃 series_key）；{@code cum1}/
     * {@code cum2} 用窗口函数算累计计数/总数，并 LAG 出上一桶的累计数与上界；最终 SELECT 用
     * {@code ROW_NUMBER() ... AS rn} 子查询定位 {@code cum_count >= quantile * total_count} 的第一个桶，
     * 在 [lower_bound, upper_bound] 区间线性插值；落入溢出桶（upper_bound IS NULL）时退化为返回
     * lower_bound（近似 Prometheus {@code histogram_quantile} 对 +Inf 桶的处理）。
     *
     * <p>算法已用合成数据 + 真实 APISIX standalone 沙箱抓取的 {@code apisix_http_latency}
     * 数据在真实 Doris 上验证（p90 场景，含溢出桶与普通桶插值两种情形；真实数据验证时发现并
     * 修正了上述①②两个 Doris ARRAY/窗口函数限制，合成数据阶段未触发,因为合成数据走的是子查询
     * 字面量数组而非真实表列/LAG 结果）。series_key 对齐 + reset 守卫已用真实沙箱数据复测。
     */
    static String buildRangeHistogramSql(boolean filterInstance, boolean filterJob,
                                         Map<String, String> filters, Map<String, String> filtersNe,
                                         List<String> groupByKeys) {
        return buildRangeHistogramSql(filterInstance, filterJob, filters, filtersNe, null, null, groupByKeys);
    }

    static String buildRangeHistogramSql(boolean filterInstance, boolean filterJob,
                                         Map<String, String> filters, Map<String, String> filtersNe,
                                         Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                         List<String> groupByKeys) {
        String extraSelect = buildExtraSelect(groupByKeys);
        String extraCols = buildExtraCols(groupByKeys);
        StringBuilder filterStr = new StringBuilder();
        appendFilters(filterStr, filterInstance, filterJob);
        appendAttrFilters(filterStr, filters, filtersNe, filtersRegex, filtersNotRegex);
        String partitionCols = "instance, job" + extraCols;
        String seriesPartitionCols = partitionCols + ", series_key";
        String joinCols = buildJoinCols(groupByKeys);
        return "WITH ordered AS (\n"
                + "  SELECT " + INST_EXPR + " AS instance,\n"
                + "         " + JOB_EXPR + " AS job"
                + extraSelect
                + ",\n         CAST(attributes AS STRING) AS series_key,\n"
                + "         UNIX_TIMESTAMP(timestamp) AS ts, count AS hist_count, bucket_counts, explicit_bounds\n"
                + "  FROM otel.otel_metrics_histogram\n"
                + "  WHERE metric_name = :metric\n"
                + "    AND timestamp BETWEEN FROM_UNIXTIME(:start - :rateWindow) AND FROM_UNIXTIME(:end)\n"
                + filterStr
                + "),\n"
                + "with_lag AS (\n"
                + "  SELECT instance, job" + extraCols
                + ", series_key, ts, hist_count, bucket_counts, explicit_bounds,\n"
                + "    LAG(ts) OVER(PARTITION BY " + seriesPartitionCols + " ORDER BY ts) AS prev_ts,\n"
                + "    LAG(hist_count) OVER(PARTITION BY " + seriesPartitionCols
                + " ORDER BY ts) AS prev_hist_count\n"
                + "  FROM ordered\n"
                + "),\n"
                + "deltas AS (\n"
                + "  SELECT instance, job" + extraCols + ", series_key,\n"
                + "    FLOOR(ts / :step) * :step AS bucket,\n"
                + "    bucket_counts, explicit_bounds, prev_ts\n"
                + "  FROM with_lag\n"
                // reset 守卫：与 buildRangeRateSql 的 value >= prev_val 一致，计数器重置（如进程
                // 重启）时整对采样丢弃，不逐桶压成 0（避免系统性低估 reset 后的真实新增量）。
                + "  WHERE prev_ts IS NOT NULL AND ts > prev_ts AND hist_count >= prev_hist_count\n"
                + "),\n"
                + "prev_rows AS (\n"
                + "  SELECT d.instance, d.job" + buildExtraColsQualified(groupByKeys, "d")
                + ", d.series_key, d.bucket, p.bucket_counts\n"
                + "  FROM deltas d\n"
                + "  JOIN ordered p\n"
                + "    ON d.instance = p.instance AND d.job = p.job\n"
                + "   AND d.series_key = p.series_key AND d.prev_ts = p.ts\n"
                + "),\n"
                + "curr_exploded AS (\n"
                + "  SELECT instance, job" + extraCols
                + ", series_key, bucket, pos, bc AS curr_count, explicit_bounds\n"
                + "  FROM deltas\n"
                + "  LATERAL VIEW POSEXPLODE(bucket_counts) t AS pos, bc\n"
                + "),\n"
                + "prev_exploded AS (\n"
                + "  SELECT instance, job" + extraCols + ", series_key, bucket, pos, bc AS prev_count\n"
                + "  FROM prev_rows\n"
                + "  LATERAL VIEW POSEXPLODE(bucket_counts) t AS pos, bc\n"
                + "),\n"
                // Doris 对 element_at() 作用在 LAG() 窗口函数结果上、且下标非常量时会报
                // "must be constant"（对真实表列/CTE 透传列则不受限）。用两次 POSEXPLODE + JOIN
                // 按 pos 对齐当前/上一次采样的同一个桶，规避这个限制（实测确认）。
                // JOIN 键含 series_key：防止不同实际 series（如不同 route/service/node）在同一
                // (instance,job,extraCols,bucket,pos) 下被误配对（Codex 审查发现的跨 series 污染）。
                + "exploded AS (\n"
                + "  SELECT c.instance AS instance, c.job AS job" + buildExtraColsQualified(groupByKeys, "c")
                + ", c.bucket AS bucket, c.pos AS pos,\n"
                + "    GREATEST(c.curr_count - COALESCE(p.prev_count, 0), 0) AS bucket_delta,\n"
                + "    element_at(c.explicit_bounds, c.pos + 1) AS upper_bound\n"
                + "  FROM curr_exploded c\n"
                + "  LEFT JOIN prev_exploded p\n"
                + "    ON c.instance = p.instance AND c.job = p.job"
                + joinCols
                + " AND c.series_key = p.series_key AND c.bucket = p.bucket AND c.pos = p.pos\n"
                + "),\n"
                + "agg AS (\n"
                + "  SELECT instance, job" + extraCols + ", bucket, pos, upper_bound,\n"
                + "    SUM(bucket_delta) AS bucket_delta\n"
                + "  FROM exploded\n"
                + "  WHERE bucket >= :start\n"
                + "  GROUP BY instance, job" + extraCols + ", bucket, pos, upper_bound\n"
                + "),\n"
                + "cum1 AS (\n"
                + "  SELECT instance, job" + extraCols + ", bucket, pos, upper_bound, bucket_delta,\n"
                + "    SUM(bucket_delta) OVER(PARTITION BY " + partitionCols + ", bucket ORDER BY pos) AS cum_count,\n"
                + "    SUM(bucket_delta) OVER(PARTITION BY " + partitionCols + ", bucket) AS total_count\n"
                + "  FROM agg\n"
                + "),\n"
                + "cum2 AS (\n"
                + "  SELECT instance, job" + extraCols
                + ", bucket, pos, upper_bound, bucket_delta, cum_count, total_count,\n"
                + "    LAG(upper_bound) OVER(PARTITION BY " + partitionCols + ", bucket ORDER BY pos) AS lower_bound,\n"
                + "    LAG(cum_count) OVER(PARTITION BY " + partitionCols + ", bucket ORDER BY pos) AS lower_cum\n"
                + "  FROM cum1\n"
                + "),\n"
                + "quantile_candidates AS (\n"
                + "  SELECT instance, job" + extraCols + ", bucket,\n"
                + "    COALESCE(lower_bound, 0) +\n"
                + "    CASE WHEN upper_bound IS NULL THEN 0\n"
                + "         WHEN bucket_delta = 0 THEN 0\n"
                + "         ELSE (:quantile * total_count - COALESCE(lower_cum, 0)) / bucket_delta\n"
                + "              * (upper_bound - COALESCE(lower_bound, 0))\n"
                + "    END AS value,\n"
                + "    ROW_NUMBER() OVER(PARTITION BY " + partitionCols + ", bucket ORDER BY pos) AS rn\n"
                + "  FROM cum2\n"
                + "  WHERE total_count > 0 AND cum_count >= :quantile * total_count\n"
                + ")\n"
                + "SELECT instance, job" + extraCols + ", bucket, value\n"
                + "FROM quantile_candidates\n"
                + "WHERE rn = 1\n"
                + "ORDER BY instance, job" + extraCols + ", bucket";
    }

    /**
     * histogram/summary 表 {@code count}/{@code sum} 列的 counter-rate 查询。
     *
     * <p>用于 Prometheus 经典 histogram/summary 的 {@code rate(metric_count[...])} /
     * {@code rate(metric_sum[...])} 语义。分区仍包含 {@code series_key}，先 per-series
     * 求 rate，再按调用方 groupBy 粒度 SUM；reset 守卫统一使用 {@code count} 列，
     * 即使请求的是 {@code sum} 字段也一样。
     */
    static String buildRangeFieldRateSql(String field, boolean filterInstance, boolean filterJob,
                                         Map<String, String> filters, Map<String, String> filtersNe,
                                         List<String> groupByKeys, String otelTable) {
        return buildRangeFieldRateSql(field, filterInstance, filterJob, filters, filtersNe,
                null, null, groupByKeys, otelTable);
    }

    static String buildRangeFieldRateSql(String field, boolean filterInstance, boolean filterJob,
                                         Map<String, String> filters, Map<String, String> filtersNe,
                                         Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                         List<String> groupByKeys, String otelTable) {
        if (!"count".equals(field) && !"sum".equals(field)) {
            throw new IllegalArgumentException("Unsupported field-rate field: " + field);
        }
        String extraSelect = buildExtraSelect(groupByKeys);
        String extraCols = buildExtraCols(groupByKeys);
        StringBuilder filterStr = new StringBuilder();
        appendFilters(filterStr, filterInstance, filterJob);
        appendAttrFilters(filterStr, filters, filtersNe, filtersRegex, filtersNotRegex);
        String partitionCols = "instance, job" + extraCols;
        String seriesPartitionCols = partitionCols + ", series_key";
        return "WITH ordered AS (\n"
                + "  SELECT " + INST_EXPR + " AS instance,\n"
                + "         " + JOB_EXPR + " AS job"
                + extraSelect
                + ",\n         CAST(attributes AS STRING) AS series_key,\n"
                + "         UNIX_TIMESTAMP(timestamp) AS ts, count AS reset_count, " + field + " AS value\n"
                + "  FROM otel." + otelTable + "\n"
                + "  WHERE metric_name = :metric\n"
                + "    AND timestamp BETWEEN FROM_UNIXTIME(:start - :rateWindow) AND FROM_UNIXTIME(:end)\n"
                + filterStr
                + "),\n"
                + "with_lag AS (\n"
                + "  SELECT " + partitionCols + ", series_key, ts, reset_count, value,\n"
                + "    LAG(ts) OVER(PARTITION BY " + seriesPartitionCols + " ORDER BY ts) AS prev_ts,\n"
                + "    LAG(reset_count) OVER(PARTITION BY " + seriesPartitionCols
                + " ORDER BY ts) AS prev_reset_count,\n"
                + "    LAG(value) OVER(PARTITION BY " + seriesPartitionCols + " ORDER BY ts) AS prev_val\n"
                + "  FROM ordered\n"
                + "),\n"
                + "rates AS (\n"
                + "  SELECT " + partitionCols + ", series_key,\n"
                + "    FLOOR(ts / :step) * :step AS bucket,\n"
                + "    CASE WHEN prev_ts IS NOT NULL AND ts > prev_ts AND reset_count >= prev_reset_count\n"
                + "      THEN (value - prev_val) / (ts - prev_ts)\n"
                + "      ELSE NULL END AS rate\n"
                + "  FROM with_lag\n"
                + "),\n"
                + "per_series AS (\n"
                + "  SELECT " + partitionCols + ", bucket, AVG(rate) AS rate\n"
                + "  FROM rates\n"
                + "  WHERE rate IS NOT NULL AND bucket >= :start\n"
                + "  GROUP BY " + seriesPartitionCols + ", bucket\n"
                + ")\n"
                + "SELECT " + partitionCols + ", bucket, SUM(rate) AS value\n"
                + "FROM per_series\n"
                + "GROUP BY " + partitionCols + ", bucket\n"
                + "ORDER BY " + partitionCols + ", bucket";
    }

    /**
     * gauge/sum 表的 range 均值查询。
     * {@code groupByKeys} 为已经过白名单过滤的 attributes 键列表（可为空）。
     */
    static String buildRangeGaugeSql(boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe,
                                     List<String> groupByKeys, String otelTable) {
        return buildRangeGaugeSql(filterInstance, filterJob, filters, filtersNe,
                null, null, groupByKeys, otelTable);
    }

    static String buildRangeGaugeSql(boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe,
                                     Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                     List<String> groupByKeys, String otelTable) {
        String extraSelect = buildExtraSelect(groupByKeys);
        String extraCols = buildExtraCols(groupByKeys);
        StringBuilder sql = new StringBuilder(
                "SELECT " + INST_EXPR + " AS instance,\n"
                        + "       " + JOB_EXPR + " AS job"
                        + extraSelect
                        + ",\n       FLOOR(UNIX_TIMESTAMP(timestamp) / :step) * :step AS bucket,\n"
                        + "       AVG(value) AS value\n"
                        + "FROM otel." + otelTable + "\n"
                        + "WHERE metric_name = :metric\n"
                        + "  AND timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)");
        appendFilters(sql, filterInstance, filterJob);
        appendAttrFilters(sql, filters, filtersNe, filtersRegex, filtersNotRegex);
        sql.append("\nGROUP BY " + INST_EXPR + ",\n"
                + "         " + JOB_EXPR
                + (groupByKeys.isEmpty() ? "" : buildExtraGroupBy(groupByKeys))
                + ",\n         bucket\n"
                + "ORDER BY instance, job" + extraCols + ", bucket");
        return sql.toString();
    }

    /**
     * gauge/sum 表的 range rate 查询（counter 单调递增差分）。
     * {@code groupByKeys} 为已经过白名单过滤的 attributes 键列表（可为空）。
     *
     * <h3>完整 series 身份对齐（Codex 复审修正，与 {@link #buildRangeHistogramSql} 同一套路）</h3>
     * 同一 instance/job/groupByKeys 下常常存在多条实际 series（如 RustFS 的 {@code bucket} 标签
     * 不在 groupByKeys 内时）。若只按 (instance,job,extraCols) 做 {@code LAG}，会把不同 series 的
     * 采样点相互配对，产出跨 series 的伪 delta。因此额外引入 {@code CAST(attributes AS STRING) AS
     * series_key} 贯穿 {@code ordered → with_lag → rates} 直到 {@code per_series} 的 PARTITION BY /
     * GROUP BY，保证只有同一条原始 series 的相邻两次采样才会配对；{@code per_series} 先按 series_key
     * 粒度 AVG 同一 series 在同一 bucket 内的多个速率样本（语义与此前单 series 场景一致），最外层
     * SELECT 才按调用方 groupByKeys 粒度对各 series 的速率求 {@code SUM}，等价于 Prometheus
     * {@code sum(rate(metric[window])) by (groupByKeys)}。当 groupByKeys 已完全区分实际 series 时
     * （此前所有已上线看板的场景），每组只有一条 series，SUM 退化为原值，与旧行为完全一致。
     */
    static String buildRangeRateSql(boolean filterInstance, boolean filterJob,
                                    Map<String, String> filters, Map<String, String> filtersNe,
                                    List<String> groupByKeys, String otelTable) {
        return buildRangeRateSql(filterInstance, filterJob, filters, filtersNe,
                null, null, groupByKeys, otelTable);
    }

    static String buildRangeRateSql(boolean filterInstance, boolean filterJob,
                                    Map<String, String> filters, Map<String, String> filtersNe,
                                    Map<String, String> filtersRegex, Map<String, String> filtersNotRegex,
                                    List<String> groupByKeys, String otelTable) {
        String extraSelect = buildExtraSelect(groupByKeys);
        String extraCols = buildExtraCols(groupByKeys);
        StringBuilder filterStr = new StringBuilder();
        appendFilters(filterStr, filterInstance, filterJob);
        appendAttrFilters(filterStr, filters, filtersNe, filtersRegex, filtersNotRegex);
        String partitionCols = "instance, job" + extraCols;
        String seriesPartitionCols = partitionCols + ", series_key";
        return "WITH ordered AS (\n"
                + "  SELECT " + INST_EXPR + " AS instance,\n"
                + "         " + JOB_EXPR + " AS job"
                + extraSelect
                + ",\n         CAST(attributes AS STRING) AS series_key,\n"
                + "         UNIX_TIMESTAMP(timestamp) AS ts, value\n"
                + "  FROM otel." + otelTable + "\n"
                + "  WHERE metric_name = :metric\n"
                + "    AND timestamp BETWEEN FROM_UNIXTIME(:start - :rateWindow) AND FROM_UNIXTIME(:end)\n"
                + filterStr
                + "),\n"
                + "with_lag AS (\n"
                + "  SELECT " + partitionCols + ", series_key, ts, value,\n"
                + "    LAG(ts) OVER(PARTITION BY " + seriesPartitionCols + " ORDER BY ts) AS prev_ts,\n"
                + "    LAG(value) OVER(PARTITION BY " + seriesPartitionCols + " ORDER BY ts) AS prev_val\n"
                + "  FROM ordered\n"
                + "),\n"
                + "rates AS (\n"
                + "  SELECT " + partitionCols + ", series_key,\n"
                + "    FLOOR(ts / :step) * :step AS bucket,\n"
                + "    CASE WHEN prev_ts IS NOT NULL AND ts > prev_ts AND value >= prev_val\n"
                + "      THEN (value - prev_val) / (ts - prev_ts)\n"
                + "      ELSE NULL END AS rate\n"
                + "  FROM with_lag\n"
                + "),\n"
                + "per_series AS (\n"
                + "  SELECT " + partitionCols + ", bucket, AVG(rate) AS rate\n"
                + "  FROM rates\n"
                + "  WHERE rate IS NOT NULL AND bucket >= :start\n"
                + "  GROUP BY " + seriesPartitionCols + ", bucket\n"
                + ")\n"
                + "SELECT " + partitionCols + ", bucket, SUM(rate) AS value\n"
                + "FROM per_series\n"
                + "GROUP BY " + partitionCols + ", bucket\n"
                + "ORDER BY " + partitionCols + ", bucket";
    }

    // ─── 行映射（package-private for testing） ────────────────────────────────────

    /**
     * instant 查询原始行 → PrometheusVectorResult。
     * 行中除 {@code ts} / {@code value} 外的所有列均作为 metric labels 输出，
     * 支持 groupBy 带来的额外维度列（如 {@code mode}）。
     */
    static PrometheusVectorResult buildVector(List<Map<String, Object>> rows, double scale) {
        List<VectorSample> samples = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object rawValue = row.get("value");
            // 聚合查询（如 buildInstantAggSql 的 SUM/MAX）在无匹配行时仍返回一行，value 列为 SQL NULL；
            // 与"无数据"同义，跳过该行而非抛异常，交给前端按空 vector 统一处理为 NaN。
            if (rawValue == null) {
                continue;
            }
            Map<String, String> labels = extractLabels(row, Set.of("ts", "value"));
            long ts = ((Number) row.get("ts")).longValue();
            double val = ((Number) rawValue).doubleValue() * scale;
            samples.add(new VectorSample(labels, new Object[]{ts, String.valueOf(val)}));
        }
        return PrometheusVectorResult.of(samples);
    }

    /**
     * range 查询原始行 → PrometheusMatrixResult。
     * 行中除 {@code bucket} / {@code value} 外的所有列均作为 metric labels，
     * 支持 groupBy 带来的额外维度列（如 {@code mode}、{@code path}）。
     */
    static PrometheusMatrixResult buildMatrix(List<Map<String, Object>> rows, double scale) {
        Map<String, MatrixSeries> seriesMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object rawValue = row.get("value");
            if (rawValue == null) {
                continue;
            }
            Map<String, String> labels = extractLabels(row, Set.of("bucket", "value"));
            long bucket = ((Number) row.get("bucket")).longValue();
            double val = ((Number) rawValue).doubleValue() * scale;
            String key = labels.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
            seriesMap.computeIfAbsent(key, k -> new MatrixSeries(labels, new ArrayList<>()))
                    .values().add(new Object[]{bucket, String.valueOf(val)});
        }
        return PrometheusMatrixResult.of(new ArrayList<>(seriesMap.values()));
    }

    // ─── 内部工具 ──────────────────────────────────────────────────────────────────

    /** 用 otel_reader 账号（SELECT-only，满足 F1 凭据隔离）创建 JdbcClient。 */
    JdbcClient createReader(Integer clusterId) {
        return readerFactory.create(clusterId);
    }

    static boolean needsFilter(String value) {
        return value != null && !value.isBlank() && !MATCH_ALL.equals(value);
    }

    static long parseRateWindow(String rateWindow) {
        if (rateWindow == null) {
            return 60L;
        }
        return switch (rateWindow) {
            case "1h" -> 3600L;
            case "5m" -> 300L;
            case "2m" -> 120L;
            case "15m" -> 900L;
            default -> 60L;
        };
    }

    /**
     * instance/job 过滤：使用 Doris OTel schema 的扁平 service 列。
     */
    private static void appendFilters(StringBuilder sql, boolean filterInstance, boolean filterJob) {
        if (filterInstance) {
            sql.append("\n  AND " + INST_EXPR + " REGEXP :instance");
        }
        if (filterJob) {
            sql.append("\n  AND " + JOB_EXPR + " REGEXP :job");
        }
    }

    /**
     * 属性等值/不等过滤（attributes MAP）。
     * 键名白名单化（{@link #ALLOWED_ATTR_FILTER_KEYS} 内的常量直接拼入 SQL），
     * 值通过命名参数绑定（{@code af_key} / {@code afne_key}），无注入风险。
     */
    static void appendAttrFilters(StringBuilder sql,
                                  Map<String, String> filters, Map<String, String> filtersNe) {
        appendAttrFilters(sql, filters, filtersNe, null, null);
    }

    static void appendAttrFilters(StringBuilder sql,
                                  Map<String, String> filters, Map<String, String> filtersNe,
                                  Map<String, String> filtersRegex, Map<String, String> filtersNotRegex) {
        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                String key = entry.getKey();
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING)")
                            .append(needsRegexp(entry.getValue()) ? " REGEXP " : " = ")
                            .append(":af_").append(key);
                }
            }
        }
        if (filtersNe != null) {
            for (Map.Entry<String, String> entry : filtersNe.entrySet()) {
                String key = entry.getKey();
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING)")
                            .append(needsRegexp(entry.getValue()) ? " NOT REGEXP " : " != ")
                            .append(":afne_").append(key);
                }
            }
        }
        if (filtersRegex != null) {
            for (Map.Entry<String, String> entry : filtersRegex.entrySet()) {
                String key = entry.getKey();
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING) REGEXP :afr_").append(key);
                }
            }
        }
        if (filtersNotRegex != null) {
            for (Map.Entry<String, String> entry : filtersNotRegex.entrySet()) {
                String key = entry.getKey();
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING) NOT REGEXP :afnr_").append(key);
                }
            }
        }
    }

    /** 绑定属性过滤参数（af_ / afne_ 前缀，仅白名单键）。 */
    private static JdbcClient.StatementSpec bindAttrFilterParams(JdbcClient.StatementSpec spec,
                                                                 Map<String, String> filters,
                                                                 Map<String, String> filtersNe) {
        return bindAttrFilterParams(spec, filters, filtersNe, null, null);
    }

    /** 绑定属性过滤参数（af_ / afne_ / afr_ / afnr_ 前缀，仅白名单键）。 */
    private static JdbcClient.StatementSpec bindAttrFilterParams(JdbcClient.StatementSpec spec,
                                                                 Map<String, String> filters,
                                                                 Map<String, String> filtersNe,
                                                                 Map<String, String> filtersRegex,
                                                                 Map<String, String> filtersNotRegex) {
        if (filters != null) {
            for (Map.Entry<String, String> e : filters.entrySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(e.getKey())) {
                    spec = spec.param("af_" + e.getKey(), e.getValue());
                }
            }
        }
        if (filtersNe != null) {
            for (Map.Entry<String, String> e : filtersNe.entrySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(e.getKey())) {
                    spec = spec.param("afne_" + e.getKey(), e.getValue());
                }
            }
        }
        if (filtersRegex != null) {
            for (Map.Entry<String, String> e : filtersRegex.entrySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(e.getKey())) {
                    spec = spec.param("afr_" + e.getKey(), e.getValue());
                }
            }
        }
        if (filtersNotRegex != null) {
            for (Map.Entry<String, String> e : filtersNotRegex.entrySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(e.getKey())) {
                    spec = spec.param("afnr_" + e.getKey(), e.getValue());
                }
            }
        }
        return spec;
    }

    /**
     * 过滤 groupByKeys，只保留白名单中的键（防止非法键混入 SQL）。
     */
    private static List<String> toValidGroupBy(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream().filter(ALLOWED_ATTR_FILTER_KEYS::contains).toList();
    }

    /**
     * SELECT 中的额外属性列（用于 groupBy）。例如 {@code ["mode"]} →
     * {@code ",\n       CAST(attributes['mode'] AS STRING) AS mode"}。
     */
    private static String buildExtraSelect(List<String> validKeys) {
        if (validKeys.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : validKeys) {
            sb.append(",\n       CAST(attributes['").append(key).append("'] AS STRING) AS ").append(key);
        }
        return sb.toString();
    }

    /**
     * GROUP BY 中的额外属性表达式（用于 gauge/sum GROUP BY 子句）。
     * 例如 {@code ["mode"]} → {@code ",\n         CAST(attributes['mode'] AS STRING)"}。
     */
    private static String buildExtraGroupBy(List<String> validKeys) {
        StringBuilder sb = new StringBuilder();
        for (String key : validKeys) {
            sb.append(",\n         CAST(attributes['").append(key).append("'] AS STRING)");
        }
        return sb.toString();
    }

    /**
     * 逗号分隔的额外列名（用于 CTE 内部引用、ORDER BY 等）。
     * 例如 {@code ["mode"]} → {@code ", mode"}。
     */
    private static String buildExtraCols(List<String> validKeys) {
        if (validKeys.isEmpty()) {
            return "";
        }
        return ", " + String.join(", ", validKeys);
    }

    /**
     * JOIN ON 子句里的额外维度等值条件（{@code c.}/{@code p.} 表别名前缀）。
     * 用于 {@link #buildRangeHistogramSql} 按 pos 对齐当前/上一次采样时，
     * 同时按 groupBy 维度对齐（否则不同维度值的桶会被误配对）。
     * 例如 {@code ["mode"]} → {@code " AND c.mode = p.mode"}。
     */
    private static String buildJoinCols(List<String> validKeys) {
        StringBuilder sb = new StringBuilder();
        for (String key : validKeys) {
            sb.append(" AND c.").append(key).append(" = p.").append(key);
        }
        return sb.toString();
    }

    /**
     * 逗号分隔的额外列名，带表别名限定并显式 {@code AS}（用于消除 {@link #buildRangeHistogramSql}
     * 的 {@code exploded} CTE 里 {@code curr_exploded c LEFT JOIN prev_exploded p} 两边同名列的歧义；
     * 不加限定时 Doris 报 {@code <col> is ambiguous}——此前所有 histogram 分位数调用方均未传
     * groupBy，故未触发；JuiceFS J09 groupBy=['mp'] 是第一个触发该路径的调用方，已用真实沙箱数据复现）。
     * 例如 {@code (["mp"], "c")} → {@code ", c.mp AS mp"}。
     */
    private static String buildExtraColsQualified(List<String> validKeys, String alias) {
        StringBuilder sb = new StringBuilder();
        for (String key : validKeys) {
            sb.append(", ").append(alias).append(".").append(key).append(" AS ").append(key);
        }
        return sb.toString();
    }

    private static boolean needsRegexp(String value) {
        return value != null && value.matches(".*[.*+?^${}()|\\[\\]\\\\].*");
    }

    /**
     * 将 SQL 结果行提取为 metric label map。
     * 所有列（排除 skipCols）都作为 label，null 值跳过。
     */
    private static Map<String, String> extractLabels(Map<String, Object> row, Set<String> skipCols) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!skipCols.contains(entry.getKey()) && entry.getValue() != null) {
                labels.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return labels;
    }
}
