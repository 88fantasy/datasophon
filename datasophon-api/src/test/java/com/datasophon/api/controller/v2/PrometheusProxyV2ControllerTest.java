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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.configuration.PrometheusProxyProperties;
import com.datasophon.api.dto.ApiResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.http.HttpException;

/**
 * PrometheusProxyV2Controller 纯单元测试。
 *
 * <p>通过匿名子类覆盖 {@code doHttpGet} 注入固定响应字符串，
 * 无需启动 Spring 上下文，也无需真实 Prometheus 连接。
 */
class PrometheusProxyV2ControllerTest {
    
    private PrometheusProxyProperties props;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        props = new PrometheusProxyProperties();
        props.setUrl("http://localhost:9090");
        props.setTimeoutMs(5000);
        objectMapper = new ObjectMapper();
    }
    
    // ─── 辅助：构造 stub controller ────────────────────────────────────────────
    
    /** 构造 controller 并覆盖 doHttpGet，注入固定 body。 */
    private PrometheusProxyV2Controller stubController(String body) {
        return new PrometheusProxyV2Controller(props, objectMapper) {
            @Override
            protected String doHttpGet(String url, String query, String time,
                                       String start, String end, String step) {
                return body;
            }
        };
    }
    
    /** 构造 controller 覆盖 doHttpGet 抛出 HttpException（模拟网络不可达）。 */
    private PrometheusProxyV2Controller stubUnreachable() {
        return new PrometheusProxyV2Controller(props, objectMapper) {
            @Override
            protected String doHttpGet(String url, String query, String time,
                                       String start, String end, String step) {
                throw new HttpException("Connection refused: localhost/127.0.0.1:9090");
            }
        };
    }
    
    // ─── instant query 成功路径 ───────────────────────────────────────────────
    
    @Test
    @DisplayName("instant query: status=success 时回传 data 字段(vector)")
    void query_success_returnsData() {
        String prometheusResp = "{"
                + "\"status\":\"success\","
                + "\"data\":{"
                + "  \"resultType\":\"vector\","
                + "  \"result\":[{\"metric\":{\"instance\":\"localhost:9090\"},\"value\":[1718000000,\"1\"]}]"
                + "}"
                + "}";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.query("up", null, 1);
        
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().path("resultType").asText()).isEqualTo("vector");
        assertThat(resp.getData().path("result").isArray()).isTrue();
        assertThat(resp.getData().path("result").size()).isEqualTo(1);
    }
    
    // ─── range query 成功路径 ─────────────────────────────────────────────────
    
    @Test
    @DisplayName("range query: status=success 时回传 data 字段(matrix)")
    void queryRange_success_returnsMatrix() {
        String prometheusResp = "{"
                + "\"status\":\"success\","
                + "\"data\":{"
                + "  \"resultType\":\"matrix\","
                + "  \"result\":[{"
                + "    \"metric\":{\"instance\":\"localhost:9090\"},"
                + "    \"values\":[[1718000000,\"1\"],[1718000030,\"1\"]]"
                + "  }]"
                + "}"
                + "}";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.queryRange("up", "1718000000", "1718003600", "30", 1);
        
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().path("resultType").asText()).isEqualTo("matrix");
        assertThat(resp.getData().path("result").get(0).path("values").size()).isEqualTo(2);
    }
    
    // ─── Prometheus 返回 status=error ─────────────────────────────────────────
    
    @Test
    @DisplayName("Prometheus 返回 status=error 时，成功=false 且 errorCode=400")
    void query_prometheusError_returnsFail400() {
        String prometheusResp = "{"
                + "\"status\":\"error\","
                + "\"errorType\":\"bad_data\","
                + "\"error\":\"invalid parameter \\\"query\\\": 1:1: parse error\""
                + "}";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.query("{{invalid", null, null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo(400);
        assertThat(resp.getErrorMessage()).contains("parse error");
    }
    
    // ─── Prometheus 不可达 ────────────────────────────────────────────────────
    
    @Test
    @DisplayName("Prometheus 不可达时，成功=false 且 errorCode=502")
    void query_unreachable_returns502() {
        PrometheusProxyV2Controller ctrl = stubUnreachable();
        ApiResponse<JsonNode> resp = ctrl.query("up", null, null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo(502);
        assertThat(resp.getErrorMessage()).contains("Prometheus 不可达");
    }
    
    @Test
    @DisplayName("range query Prometheus 不可达时，成功=false 且 errorCode=502")
    void queryRange_unreachable_returns502() {
        PrometheusProxyV2Controller ctrl = stubUnreachable();
        ApiResponse<JsonNode> resp = ctrl.queryRange("up", "1718000000", "1718003600", "30", null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo(502);
    }
    
    // ─── 无 error 字段时使用默认描述 ──────────────────────────────────────────
    
    @Test
    @DisplayName("Prometheus status=error 且无 error 字段时，使用默认描述")
    void query_prometheusErrorNoMessage_usesDefaultDescription() {
        String prometheusResp = "{\"status\":\"error\"}";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.query("up", null, null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorMessage()).isEqualTo("Prometheus 返回未知错误");
    }
    
    @Test
    @DisplayName("Prometheus 返回非 JSON 文本时，返回 502 并保留上游错误摘要")
    void query_plainTextError_returnsBadGateway() {
        String prometheusResp = "Error parsing query: invalid parameter \"query\"";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.query("broken", null, null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo(502);
        assertThat(resp.getErrorMessage()).contains("Prometheus 返回非 JSON 响应");
        assertThat(resp.getErrorMessage()).contains("Error parsing query");
    }
    
    @Test
    @DisplayName("range query 返回非 JSON 文本时，返回 502 并保留上游错误摘要")
    void queryRange_plainTextError_returnsBadGateway() {
        String prometheusResp = "Error executing query: upstream timeout";
        
        PrometheusProxyV2Controller ctrl = stubController(prometheusResp);
        ApiResponse<JsonNode> resp = ctrl.queryRange("broken", "1718000000", "1718003600", "30", null);
        
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo(502);
        assertThat(resp.getErrorMessage()).contains("Prometheus 返回非 JSON 响应");
        assertThat(resp.getErrorMessage()).contains("Error executing query");
    }
    
    // ─── buildRequestUrl：URL 编码行为 ────────────────────────────────────────
    
    @Test
    @DisplayName("buildRequestUrl: PromQL '+' 编为 '%2B'（防 Go net/url.ParseQuery 解码为空格）")
    void buildRequestUrl_plusInQuery_encodedAsPercentTwoB() {
        String url = PrometheusProxyV2Controller.buildRequestUrl(
                "http://localhost:9090/api/v1/query",
                "node_load1+node_load5", null, null, null, null);
        
        assertThat(url).contains("query=node_load1%2Bnode_load5");
        assertThat(url).doesNotContain("query=node_load1+node_load5");
    }
    
    @Test
    @DisplayName("buildRequestUrl: null 可选参数不追加到 URL")
    void buildRequestUrl_nullOptionalParams_notAppended() {
        String instant = PrometheusProxyV2Controller.buildRequestUrl(
                "http://localhost:9090/api/v1/query",
                "up", "1718000000", null, null, null);
        assertThat(instant)
                .isEqualTo("http://localhost:9090/api/v1/query?query=up&time=1718000000");
        
        String range = PrometheusProxyV2Controller.buildRequestUrl(
                "http://localhost:9090/api/v1/query_range",
                "up", null, "1718000000", "1718003600", "30");
        assertThat(range)
                .isEqualTo("http://localhost:9090/api/v1/query_range"
                        + "?query=up&start=1718000000&end=1718003600&step=30");
    }
}
