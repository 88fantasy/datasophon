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
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
 *   <li>Prometheus 抓取指标的 {@code instance} / {@code job} 落位于
 *       {@code resource_attributes}，不在 {@code attributes}：
 *       prometheusreceiver 将这两个标签提升为 resource attribute。
 *   <li>Doris 业务维度（{@code group} / {@code type} / {@code mode} /
 *       {@code path} / {@code device}）落在 {@code attributes} MAP。
 *   <li>FE summary 指标（{@code doris_fe_query_latency_ms} 等）写入
 *       {@code otel_metrics_summary}；BE counter（cpu/network/compaction）写入
 *       {@code otel_metrics_sum}；其余写入 {@code otel_metrics_gauge}。
 * </ul>
 */
@Component
public class OtelMetricsQueryService {
    
    private static final Logger log = LoggerFactory.getLogger(OtelMetricsQueryService.class);
    private static final String MATCH_ALL = ".+";
    
    /**
     * Prometheus 抓取指标的 instance 字段：
     * prometheusreceiver 将 job/instance 提升为 resource attribute 而非普通 attribute。
     * 所有 SQL builder 统一使用此常量，禁止使用 {@code attributes['instance']}（始终 NULL）。
     */
    static final String INST_EXPR =
            "CAST(resource_attributes['service']['instance']['id'] AS STRING)";
    
    /**
     * Prometheus 抓取指标的 job 字段（同上，resource_attributes 落位）。
     */
    static final String JOB_EXPR =
            "CAST(resource_attributes['service']['name'] AS STRING)";
    
    /**
     * attributes MAP 的属性过滤键白名单。
     * 键名会被直接拼入 SQL（如 {@code attributes['group']}），必须严格白名单化；
     * 对应值通过命名参数绑定（af_key / afne_key），不存在注入风险。
     */
    static final Set<String> ALLOWED_ATTR_FILTER_KEYS =
            Set.of("group", "type", "mode", "path", "device");
    
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;
    private final OtelCredentialService credentialService;
    
    /** 开发/测试直连兜底：配置后跳过集群注册表查询，直连指定 Doris FE 主机。生产环境留空。 */
    @Value("${datasophon.otel.doris.fallback-host:}")
    private String fallbackHost;
    
    @Value("${datasophon.otel.doris.fallback-port:9030}")
    private String fallbackPort;
    
    @Value("${datasophon.otel.doris.fallback-password:}")
    private String fallbackPassword;
    
    public OtelMetricsQueryService(ClusterServiceRoleInstanceService roleService,
                                   ClusterVariableService variableService,
                                   OtelCredentialService credentialService) {
        this.roleService = roleService;
        this.variableService = variableService;
        this.credentialService = credentialService;
    }
    
    // ─── 公开查询接口 ──────────────────────────────────────────────────────────────
    
    /**
     * Instant 查询，返回 Prometheus vector 格式。
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
        JdbcClient client = createReader(clusterId);
        boolean hasAgg = agg != null && !agg.isBlank();
        String sql = hasAgg
                ? buildInstantAggSql(agg, needsFilter(instance), needsFilter(job), filters, filtersNe)
                : buildInstantNoAggSql(needsFilter(instance), needsFilter(job), filters, filtersNe);
        
        JdbcClient.StatementSpec spec = client.sql(sql)
                .param("metric", metric)
                .param("evalTime", evalTime);
        if (needsFilter(instance)) {
            spec = spec.param("instance", instance);
        }
        if (needsFilter(job)) {
            spec = spec.param("job", job);
        }
        spec = bindAttrFilterParams(spec, filters, filtersNe);
        
        List<Map<String, Object>> rows = spec.query().listOfRows();
        return buildVector(rows, scale);
    }
    
    /**
     * Range 查询，返回 Prometheus matrix 格式。
     *
     * @param rateWindow  速率窗口（"1m"/"5m"；null 表示 gauge，直接取平均）
     * @param scale       值乘数
     * @param filters     可选属性等值过滤（白名单键）
     * @param filtersNe   可选属性不等过滤（白名单键）
     * @param groupByKeys 额外 GROUP BY 维度（attributes MAP 键，白名单；如 {@code ["mode","path"]}）
     */
    public PrometheusMatrixResult queryRange(Integer clusterId, String metric,
                                             String rateWindow, double scale,
                                             String instance, String job,
                                             Map<String, String> filters,
                                             Map<String, String> filtersNe,
                                             List<String> groupByKeys,
                                             long start, long end, long step,
                                             String table, double quantile) {
        JdbcClient client = createReader(clusterId);
        
        if ("summary".equalsIgnoreCase(table)) {
            String sql = buildRangeSummarySql();
            List<Map<String, Object>> rows = client.sql(sql)
                    .param("metric", metric)
                    .param("start", start)
                    .param("end", end)
                    .param("step", step)
                    .param("quantile", quantile)
                    .query().listOfRows();
            return buildMatrix(rows, scale);
        }
        
        List<String> validGroupBy = toValidGroupBy(groupByKeys);
        boolean hasRate = rateWindow != null && !rateWindow.isBlank();
        String otelTable = "sum".equalsIgnoreCase(table) ? "otel_metrics_sum" : "otel_metrics_gauge";
        String sql = hasRate
                ? buildRangeRateSql(needsFilter(instance), needsFilter(job), filters, filtersNe,
                        validGroupBy, otelTable)
                : buildRangeGaugeSql(needsFilter(instance), needsFilter(job), filters, filtersNe,
                        validGroupBy, otelTable);
        
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
        spec = bindAttrFilterParams(spec, filters, filtersNe);
        
        List<Map<String, Object>> rows = spec.query().listOfRows();
        return buildMatrix(rows, scale);
    }
    
    /**
     * 查询指标的 instance/job 标签值，用于工具栏下拉派生（替代 Prometheus {@code up} 查询）。
     * 联合查询 gauge 和 sum 两表（某些指标只在其中一张）；instance/job 从
     * {@code resource_attributes} 取（prometheusreceiver 实际落位）。
     */
    public LabelsResult queryLabels(Integer clusterId, String metric) {
        JdbcClient client = createReader(clusterId);
        // UNION 确保同时覆盖 gauge 和 sum 两表里的指标
        String sql = "SELECT DISTINCT " + INST_EXPR + " AS instance,\n"
                + "  " + JOB_EXPR + " AS job\n"
                + "FROM otel.otel_metrics_gauge\n"
                + "WHERE metric_name = :metric\n"
                + "  AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)\n"
                + "UNION\n"
                + "SELECT DISTINCT " + INST_EXPR + " AS instance,\n"
                + "  " + JOB_EXPR + " AS job\n"
                + "FROM otel.otel_metrics_sum\n"
                + "WHERE metric_name = :metric\n"
                + "  AND timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP() - 300)";
        List<Map<String, Object>> rows = client.sql(sql).param("metric", metric).query().listOfRows();
        Set<String> instances = new LinkedHashSet<>();
        Set<String> jobs = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object inst = row.get("instance");
            Object j = row.get("job");
            if (inst != null) {
                instances.add(inst.toString());
            }
            if (j != null) {
                jobs.add(j.toString());
            }
        }
        return new LabelsResult(List.copyOf(instances), List.copyOf(jobs));
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
    
    public record LabelsResult(List<String> instances, List<String> jobs) {
    }
    
    // ─── SQL 构建（package-private for testing） ──────────────────────────────────
    
    static String buildInstantNoAggSql(boolean filterInstance, boolean filterJob,
                                       Map<String, String> filters, Map<String, String> filtersNe) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + INST_EXPR + " AS instance,\n"
                        + "       " + JOB_EXPR + " AS job,\n"
                        + "       value, UNIX_TIMESTAMP(timestamp) AS ts\n"
                        + "FROM otel.otel_metrics_gauge\n"
                        + "WHERE metric_name = :metric\n"
                        + "  AND timestamp >= FROM_UNIXTIME(:evalTime - 300)");
        appendFilters(sql, filterInstance, filterJob);
        appendAttrFilters(sql, filters, filtersNe);
        sql.append("\nQUALIFY ROW_NUMBER() OVER(\n"
                + "  PARTITION BY " + INST_EXPR + ",\n"
                + "               " + JOB_EXPR + "\n"
                + "  ORDER BY timestamp DESC\n"
                + ") = 1");
        return sql.toString();
    }
    
    static String buildInstantAggSql(String agg, boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe) {
        String fn = "max".equalsIgnoreCase(agg) ? "MAX" : "SUM";
        StringBuilder inner = new StringBuilder(
                "SELECT value\n"
                        + "FROM otel.otel_metrics_gauge\n"
                        + "WHERE metric_name = :metric\n"
                        + "  AND timestamp >= FROM_UNIXTIME(:evalTime - 300)");
        appendFilters(inner, filterInstance, filterJob);
        appendAttrFilters(inner, filters, filtersNe);
        inner.append("\nQUALIFY ROW_NUMBER() OVER(\n"
                + "  PARTITION BY " + INST_EXPR + ",\n"
                + "               " + JOB_EXPR + "\n"
                + "  ORDER BY timestamp DESC\n"
                + ") = 1");
        return "SELECT " + fn + "(value) AS value, UNIX_TIMESTAMP(NOW()) AS ts FROM (" + inner + ") t";
    }
    
    static String buildRangeSummarySql() {
        return "SELECT " + INST_EXPR + " AS instance,\n"
                + "       " + JOB_EXPR + " AS job,\n"
                + "       FLOOR(UNIX_TIMESTAMP(s.timestamp) / :step) * :step AS bucket,\n"
                + "       AVG(qv.value) AS value\n"
                + "FROM otel.otel_metrics_summary s\n"
                + "LATERAL VIEW EXPLODE(s.quantile_values) t AS qv\n"
                + "WHERE s.metric_name = :metric\n"
                + "  AND s.timestamp BETWEEN FROM_UNIXTIME(:start) AND FROM_UNIXTIME(:end)\n"
                + "  AND qv.quantile = :quantile\n"
                + "GROUP BY " + INST_EXPR + ",\n"
                + "         " + JOB_EXPR + ",\n"
                + "         bucket\n"
                + "ORDER BY instance, job, bucket";
    }
    
    /**
     * gauge/sum 表的 range 均值查询。
     * {@code groupByKeys} 为已经过白名单过滤的 attributes 键列表（可为空）。
     */
    static String buildRangeGaugeSql(boolean filterInstance, boolean filterJob,
                                     Map<String, String> filters, Map<String, String> filtersNe,
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
        appendAttrFilters(sql, filters, filtersNe);
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
     */
    static String buildRangeRateSql(boolean filterInstance, boolean filterJob,
                                    Map<String, String> filters, Map<String, String> filtersNe,
                                    List<String> groupByKeys, String otelTable) {
        String extraSelect = buildExtraSelect(groupByKeys);
        String extraCols = buildExtraCols(groupByKeys);
        StringBuilder filterStr = new StringBuilder();
        appendFilters(filterStr, filterInstance, filterJob);
        appendAttrFilters(filterStr, filters, filtersNe);
        return "WITH ordered AS (\n"
                + "  SELECT " + INST_EXPR + " AS instance,\n"
                + "         " + JOB_EXPR + " AS job"
                + extraSelect
                + ",\n         UNIX_TIMESTAMP(timestamp) AS ts, value\n"
                + "  FROM otel." + otelTable + "\n"
                + "  WHERE metric_name = :metric\n"
                + "    AND timestamp BETWEEN FROM_UNIXTIME(:start - :rateWindow) AND FROM_UNIXTIME(:end)\n"
                + filterStr
                + "),\n"
                + "with_lag AS (\n"
                + "  SELECT instance, job" + extraCols + ", ts, value,\n"
                + "    LAG(ts) OVER(PARTITION BY instance, job" + extraCols + " ORDER BY ts) AS prev_ts,\n"
                + "    LAG(value) OVER(PARTITION BY instance, job" + extraCols + " ORDER BY ts) AS prev_val\n"
                + "  FROM ordered\n"
                + "),\n"
                + "rates AS (\n"
                + "  SELECT instance, job" + extraCols + ",\n"
                + "    FLOOR(ts / :step) * :step AS bucket,\n"
                + "    CASE WHEN prev_ts IS NOT NULL AND ts > prev_ts AND value >= prev_val\n"
                + "      THEN (value - prev_val) / (ts - prev_ts)\n"
                + "      ELSE NULL END AS rate\n"
                + "  FROM with_lag\n"
                + ")\n"
                + "SELECT instance, job" + extraCols + ", bucket, AVG(rate) AS value\n"
                + "FROM rates\n"
                + "WHERE rate IS NOT NULL AND bucket >= :start\n"
                + "GROUP BY instance, job" + extraCols + ", bucket\n"
                + "ORDER BY instance, job" + extraCols + ", bucket";
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
            Map<String, String> labels = extractLabels(row, Set.of("ts", "value"));
            long ts = ((Number) row.get("ts")).longValue();
            double val = ((Number) row.get("value")).doubleValue() * scale;
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
            Map<String, String> labels = extractLabels(row, Set.of("bucket", "value"));
            long bucket = ((Number) row.get("bucket")).longValue();
            double val = ((Number) row.get("value")).doubleValue() * scale;
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
        // 开发直连兜底：配置 datasophon.otel.doris.fallback-host 后跳过集群注册表
        if (fallbackHost != null && !fallbackHost.isBlank()) {
            log.debug("Using Doris fallback connection {}:{}", fallbackHost, fallbackPort);
            return buildJdbcClient(fallbackHost, fallbackPort, "root", fallbackPassword);
        }
        
        List<ClusterServiceRoleInstanceEntity> fes = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "DorisFE")
                .stream()
                .filter(r -> ServiceRoleState.RUNNING.equals(r.getServiceRoleState()))
                .toList();
        if (fes.isEmpty()) {
            throw new IllegalStateException("No running DorisFE for cluster " + clusterId);
        }
        String port = variableValue(clusterId, "query_port", "9030");
        String password = credentialService.getOrCreate(clusterId).readerPassword();
        return buildJdbcClient(fes.get(0).getHostname(), port, "otel_reader", password);
    }
    
    private static JdbcClient buildJdbcClient(String host, String port, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl("jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false");
        ds.setUsername(user);
        ds.setPassword(password);
        return JdbcClient.create(ds);
    }
    
    private String variableValue(Integer clusterId, String name, String defaultValue) {
        var v = variableService.getVariableByVariableName(clusterId, "DORIS", name);
        return v == null ? defaultValue : v.getVariableValue();
    }
    
    static boolean needsFilter(String value) {
        return value != null && !value.isBlank() && !MATCH_ALL.equals(value);
    }
    
    static long parseRateWindow(String rateWindow) {
        if (rateWindow == null) {
            return 60L;
        }
        return switch (rateWindow) {
            case "5m" -> 300L;
            case "2m" -> 120L;
            case "15m" -> 900L;
            default -> 60L;
        };
    }
    
    /**
     * instance/job 过滤：从 resource_attributes 取（prometheusreceiver 实际落位）。
     * 禁止使用 {@code attributes['instance']} / {@code attributes['job']}，它们始终为 NULL。
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
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING) = :af_").append(key);
                }
            }
        }
        if (filtersNe != null) {
            for (String key : filtersNe.keySet()) {
                if (ALLOWED_ATTR_FILTER_KEYS.contains(key)) {
                    sql.append("\n  AND CAST(attributes['").append(key)
                            .append("'] AS STRING) != :afne_").append(key);
                }
            }
        }
    }
    
    /** 绑定属性过滤参数（af_ / afne_ 前缀，仅白名单键）。 */
    private static JdbcClient.StatementSpec bindAttrFilterParams(JdbcClient.StatementSpec spec,
                                                                 Map<String, String> filters,
                                                                 Map<String, String> filtersNe) {
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
