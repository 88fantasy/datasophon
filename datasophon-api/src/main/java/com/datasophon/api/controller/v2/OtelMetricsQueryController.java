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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.OtelMetricsQueryService.LabelsResult;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusVectorResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Doris OTel 指标查询控制器。
 *
 * <p>响应格式与 {@code PrometheusProxyV2Controller} 的 {@code ApiResponse<data>} 信封对齐，
 * 其中 {@code data} 是 Prometheus vector/matrix 格式，前端 {@code promql.ts} 工具函数
 * ({@code vectorToScalar / matrixToSeries / mergeNamedSeries}) 可直接复用，渲染层零改动。
 *
 * <p>完整路径（context-path=/ddh + path-prefix=/api）：
 * <ul>
 *   <li>GET {@code /ddh/api/v2/observability/otel/metrics/query}       — instant 查询</li>
 *   <li>GET {@code /ddh/api/v2/observability/otel/metrics/query_range}  — range 查询</li>
 *   <li>GET {@code /ddh/api/v2/observability/otel/metrics/labels}       — 标签值枚举</li>
 *   <li>GET {@code /ddh/api/v2/observability/otel/metrics/nodes}        — 节点计数</li>
 * </ul>
 *
 * <h3>filters / filtersNe 格式</h3>
 * 逗号分隔的 {@code key:value} 对，key 必须在白名单内（group/type/mode/path/device）：
 * {@code filters=group:fe,type:used}；{@code filtersNe=device:lo}（不等过滤）。
 *
 * <h3>groupBy 格式</h3>
 * 逗号分隔的 attributes 键名：{@code groupBy=mode} 或 {@code groupBy=path,device}。
 */
@RestController
@RequestMapping("/v2/observability/otel/metrics")
public class OtelMetricsQueryController extends ApiController {
    
    private static final Logger log = LoggerFactory.getLogger(OtelMetricsQueryController.class);
    
    private final OtelMetricsQueryService queryService;
    
    public OtelMetricsQueryController(OtelMetricsQueryService queryService) {
        this.queryService = queryService;
    }
    
    /**
     * Instant 查询，对应 Prometheus {@code /api/v1/query}。
     *
     * @param metric    指标名（如 {@code jvm_vm_uptime}）
     * @param agg       可选聚合（"sum"/"max"；缺省每 series 各一样本）
     * @param scale     可选值乘数（如 100.0 将 ratio 转为百分比；缺省 1.0）
     * @param instance  可选 instance 正则过滤（".+" 表示全部）
     * @param job       可选 job 正则过滤
     * @param time      评估时间（Unix 秒；缺省取当前时间）
     * @param clusterId 集群 ID
     * @param filters   可选等值过滤，格式 {@code "group:fe,type:used"}
     * @param filtersNe 可选不等过滤，格式 {@code "device:lo"}
     */
    @GetMapping("/query")
    public ApiResponse<PrometheusVectorResult> query(
                                                     @RequestParam String metric,
                                                     @RequestParam(required = false) String agg,
                                                     @RequestParam(required = false, defaultValue = "1.0") double scale,
                                                     @RequestParam(required = false, defaultValue = ".+") String instance,
                                                     @RequestParam(required = false, defaultValue = ".+") String job,
                                                     @RequestParam(required = false) Long time,
                                                     @RequestParam(required = false, defaultValue = "1") Integer clusterId,
                                                     @RequestParam(required = false) String filters,
                                                     @RequestParam(required = false) String filtersNe) {
        try {
            long evalTime = time != null ? time : System.currentTimeMillis() / 1000;
            return ApiResponse.ok(queryService.queryInstant(clusterId, metric, agg, scale,
                    instance, job, parseFilters(filters), parseFilters(filtersNe), evalTime));
        } catch (Exception e) {
            log.error("Doris instant query failed: metric={} cluster={} reason={}",
                    metric, clusterId, e.getMessage(), e);
            return ApiResponse.fail(500, "指标查询失败");
        }
    }
    
    /**
     * Range 查询，对应 Prometheus {@code /api/v1/query_range}。
     *
     * @param rateWindow 可选速率窗口（"1m"/"5m"；缺省 gauge 直接取平均）
     * @param filters    可选等值过滤，格式 {@code "group:fe,type:used"}
     * @param filtersNe  可选不等过滤，格式 {@code "device:lo"}
     * @param groupBy    可选额外 GROUP BY 维度，格式 {@code "mode"} 或 {@code "mode,path"}
     */
    @GetMapping("/query_range")
    public ApiResponse<PrometheusMatrixResult> queryRange(
                                                          @RequestParam String metric,
                                                          @RequestParam(required = false) String rateWindow,
                                                          @RequestParam(required = false, defaultValue = "1.0") double scale,
                                                          @RequestParam(required = false, defaultValue = ".+") String instance,
                                                          @RequestParam(required = false, defaultValue = ".+") String job,
                                                          @RequestParam long start,
                                                          @RequestParam long end,
                                                          @RequestParam long step,
                                                          @RequestParam(required = false, defaultValue = "1") Integer clusterId,
                                                          @RequestParam(required = false, defaultValue = "gauge") String table,
                                                          @RequestParam(required = false, defaultValue = "0.5") double quantile,
                                                          @RequestParam(required = false) String filters,
                                                          @RequestParam(required = false) String filtersNe,
                                                          @RequestParam(required = false) String groupBy) {
        try {
            return ApiResponse.ok(queryService.queryRange(clusterId, metric, rateWindow, scale,
                    instance, job, parseFilters(filters), parseFilters(filtersNe), parseGroupBy(groupBy),
                    start, end, step, table, quantile));
        } catch (Exception e) {
            log.error("Doris range query failed: metric={} cluster={} reason={}",
                    metric, clusterId, e.getMessage(), e);
            return ApiResponse.fail(500, "指标查询失败");
        }
    }
    
    /**
     * 查询指标的 instance/job 标签枚举值，用于工具栏下拉（替代 Prometheus {@code up} 查询）。
     */
    @GetMapping("/labels")
    public ApiResponse<LabelsResult> labels(
                                            @RequestParam String metric,
                                            @RequestParam(required = false, defaultValue = "1") Integer clusterId) {
        try {
            return ApiResponse.ok(queryService.queryLabels(clusterId, metric));
        } catch (Exception e) {
            log.error("Doris labels query failed: metric={} cluster={} reason={}",
                    metric, clusterId, e.getMessage(), e);
            return ApiResponse.fail(500, "标签查询失败");
        }
    }
    
    /**
     * 查询集群内指定角色的 RUNNING 节点数（替代 PromQL {@code count(up==1)}）。
     *
     * @param roleName  角色名，与 meta DDL 的 roles[].name 一致（如 "DorisFE" / "DorisBE"）
     * @param clusterId 集群 ID
     */
    @GetMapping("/nodes")
    public ApiResponse<Integer> countNodes(
                                           @RequestParam String roleName,
                                           @RequestParam(required = false, defaultValue = "1") Integer clusterId) {
        try {
            return ApiResponse.ok(queryService.countNodes(clusterId, roleName));
        } catch (Exception e) {
            log.error("Node count failed: roleName={} cluster={} reason={}",
                    roleName, clusterId, e.getMessage(), e);
            return ApiResponse.fail(500, "节点计数失败");
        }
    }
    
    // ─── 参数解析 ──────────────────────────────────────────────────────────────────
    
    /**
     * 解析 {@code "key:value,key:value"} 格式的过滤参数为 Map。
     * 白名单校验由 {@link OtelMetricsQueryService} 负责，此处仅做格式解析。
     */
    static Map<String, String> parseFilters(String filtersParam) {
        if (filtersParam == null || filtersParam.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : filtersParam.split(",")) {
            int idx = pair.indexOf(':');
            if (idx > 0) {
                result.put(pair.substring(0, idx).trim(), pair.substring(idx + 1).trim());
            }
        }
        return result;
    }
    
    /**
     * 解析 {@code "mode"} 或 {@code "mode,path"} 格式的 groupBy 参数为 List。
     */
    static List<String> parseGroupBy(String groupByParam) {
        if (groupByParam == null || groupByParam.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : groupByParam.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
