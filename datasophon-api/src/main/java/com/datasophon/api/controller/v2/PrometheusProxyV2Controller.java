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

import com.datasophon.api.configuration.PrometheusProxyProperties;
import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

/**
 * Prometheus 查询代理（v2 API）。
 *
 * <p>把前端 PromQL 请求透传到配置文件中指定的 Prometheus 实例（{@code datasophon.prometheus.url}），
 * 并将 Prometheus 原生响应体的 {@code data} 字段包装为标准 v2 信封返回。
 *
 * <p>完整路径（context-path=/ddh + path-prefix=/api）：
 * <ul>
 *   <li>GET {@code /ddh/api/v2/prometheus/query}       —— instant query</li>
 *   <li>GET {@code /ddh/api/v2/prometheus/query_range}  —— range query</li>
 * </ul>
 *
 * <p>{@code clusterId} 参数目前忽略（当前仅配置单一全局 Prometheus 地址），
 * 保留入参兼容前端约定，未来可扩展为按集群动态解析地址。
 */
@RestController
@RequestMapping("/v2/prometheus")
public class PrometheusProxyV2Controller extends ApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(PrometheusProxyV2Controller.class);
    
    private final PrometheusProxyProperties props;
    private final ObjectMapper objectMapper;
    
    public PrometheusProxyV2Controller(PrometheusProxyProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Instant query — 对应 Prometheus {@code /api/v1/query}。
     *
     * @param query    PromQL 表达式（已由前端完成变量替换）
     * @param time     评估时间戳（Unix 秒，可选；缺省由 Prometheus 取当前时间）
     * @param clusterId 集群 ID（当前忽略，保留参数兼容前端）
     */
    @GetMapping("/query")
    public ApiResponse<JsonNode> query(
                                       @RequestParam String query,
                                       @RequestParam(required = false) String time,
                                       @RequestParam(required = false) Integer clusterId) {
        return forward(props.getUrl() + "/api/v1/query", query, time, null, null, null);
    }
    
    /**
     * Range query — 对应 Prometheus {@code /api/v1/query_range}。
     *
     * @param query    PromQL 表达式（已由前端完成变量替换）
     * @param start    起始时间戳（Unix 秒）
     * @param end      结束时间戳（Unix 秒）
     * @param step     步长（秒）
     * @param clusterId 集群 ID（当前忽略，保留参数兼容前端）
     */
    @GetMapping("/query_range")
    public ApiResponse<JsonNode> queryRange(
                                            @RequestParam String query,
                                            @RequestParam String start,
                                            @RequestParam String end,
                                            @RequestParam String step,
                                            @RequestParam(required = false) Integer clusterId) {
        return forward(props.getUrl() + "/api/v1/query_range", query, null, start, end, step);
    }
    
    // ─── 内部转发逻辑 ──────────────────────────────────────────────────────────
    
    /**
     * 统一转发到 Prometheus HTTP API，并把 {@code data} 字段包装为 v2 信封。
     *
     * <p>Prometheus 响应格式：
     * <pre>{@code { "status": "success"|"error", "data": {...}, "error": "..." } }</pre>
     *
     * <p>成功时只回传 {@code data}（即 {@code {resultType, result}}），
     * 与前端 {@code promql.ts} 的 {@code PrometheusVector/PrometheusMatrix} 类型对齐。
     */
    private ApiResponse<JsonNode> forward(String url,
                                          String query,
                                          String time,
                                          String start,
                                          String end,
                                          String step) {
        try {
            String responseBody = doHttpGet(url, query, time, start, end, step);
            JsonNode root = objectMapper.readTree(responseBody);
            String status = root.path("status").asText();
            
            if ("success".equals(status)) {
                return ApiResponse.ok(root.get("data"));
            }
            
            String errorMsg = root.path("error").asText("Prometheus 返回未知错误");
            logger.warn("Prometheus 查询失败: url={} query={} error={}", url, query, errorMsg);
            return ApiResponse.fail(400, errorMsg);
        } catch (HttpException e) {
            logger.error("Prometheus 不可达: url={} reason={}", url, e.getMessage());
            return ApiResponse.fail(502, "Prometheus 不可达: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Prometheus 代理异常: url={} reason={}", url, e.getMessage(), e);
            return ApiResponse.fail(500, "代理内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 执行实际的 HTTP GET 请求，返回响应体字符串。
     *
     * <p>{@code protected} 可见性允许测试子类 override 以注入固定响应，无需真实网络连接。
     */
    protected String doHttpGet(String url,
                               String query,
                               String time,
                               String start,
                               String end,
                               String step) {
        // 使用 URLEncoder 手动构建 URL：Hutool form() 对 GET 请求以 RFC 3986 编码 '+'（保留原样），
        // 但 Prometheus/Go net/url.ParseQuery() 将裸 '+' 解码为空格，导致 PromQL 二元运算符丢失。
        // URLEncoder.encode() 把 '+' 编为 '%2B'，与 Go URL 解码语义一致。
        StringBuilder sb = new StringBuilder(url)
                .append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (time != null) {
            sb.append("&time=").append(URLEncoder.encode(time, StandardCharsets.UTF_8));
        }
        if (start != null) {
            sb.append("&start=").append(URLEncoder.encode(start, StandardCharsets.UTF_8));
        }
        if (end != null) {
            sb.append("&end=").append(URLEncoder.encode(end, StandardCharsets.UTF_8));
        }
        if (step != null) {
            sb.append("&step=").append(URLEncoder.encode(step, StandardCharsets.UTF_8));
        }
        try (HttpResponse resp = HttpRequest.get(sb.toString()).timeout(props.getTimeoutMs()).execute()) {
            return resp.body();
        }
    }
}
