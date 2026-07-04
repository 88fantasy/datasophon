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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.observability.OtelMetricsQueryService;
import com.datasophon.api.observability.OtelMetricsQueryService.LabelsResult;
import com.datasophon.api.observability.PrometheusMatrixResult;
import com.datasophon.api.observability.PrometheusVectorResult;
import com.datasophon.api.observability.PrometheusVectorResult.VectorSample;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OtelMetricsQueryControllerTest {

    private OtelMetricsQueryService service;
    private OtelMetricsQueryController controller;

    @BeforeEach
    void setUp() {
        service = mock(OtelMetricsQueryService.class);
        controller = new OtelMetricsQueryController(service);
    }

    @Test
    void query_returnsSucessWithVectorData() {
        VectorSample sample = new VectorSample(
                Map.of("instance", "h:8081", "job", "nexus"),
                new Object[]{1234567890L, "42.0"});
        // queryInstant signature: (clusterId, metric, agg, scale, instance, job, filters, filtersNe, evalTime, table)
        when(service.queryInstant(eq(1), eq("jvm_vm_uptime"), isNull(), eq(1.0),
                anyString(), anyString(), any(), any(), anyLong(), eq("gauge")))
                .thenReturn(PrometheusVectorResult.of(List.of(sample)));

        ApiResponse<PrometheusVectorResult> response =
                controller.query("jvm_vm_uptime", null, 1.0, ".+", ".+", null, 1, null, null, "gauge");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().resultType()).isEqualTo("vector");
        assertThat(response.getData().result()).hasSize(1);
        assertThat(response.getData().result().get(0).metric()).containsEntry("instance", "h:8081");
    }

    @Test
    void queryRange_returnsSuccessWithMatrixData() {
        PrometheusMatrixResult matrix = PrometheusMatrixResult.of(List.of(
                new PrometheusMatrixResult.MatrixSeries(
                        Map.of("instance", "h:8081", "job", "nexus"),
                        List.<Object[]>of(new Object[]{1000L, "10.5"}))));
        // queryRange signature: (clusterId, metric, rateWindow, scale, instance, job,
        // filters, filtersNe, groupByKeys, start, end, step, table, quantile, field)
        when(service.queryRange(eq(1), eq("jvm_memory_heap_used"), isNull(), eq(1.0),
                anyString(), anyString(), any(), any(), any(), anyLong(), anyLong(), anyLong(),
                any(), anyDouble(), eq("sum")))
                .thenReturn(matrix);

        ApiResponse<PrometheusMatrixResult> response =
                controller.queryRange("jvm_memory_heap_used", null, 1.0, ".+", ".+",
                        1000L, 2000L, 15L, 1, null, 0.5, "sum", null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().resultType()).isEqualTo("matrix");
        assertThat(response.getData().result()).hasSize(1);
        assertThat(response.getData().result().get(0).values()).hasSize(1);
    }

    @Test
    void labels_returnsInstancesAndJobs() {
        when(service.queryLabels(eq(1), eq("jvm_vm_uptime")))
                .thenReturn(new LabelsResult(List.of("h:8081"), List.of("nexus")));
        
        ApiResponse<LabelsResult> response = controller.labels("jvm_vm_uptime", 1);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().instances()).containsExactly("h:8081");
        assertThat(response.getData().jobs()).containsExactly("nexus");
    }

    @Test
    void countNodes_returnsRunningCount() {
        when(service.countNodes(eq(1), eq("DorisFE"))).thenReturn(3);
        
        ApiResponse<Integer> response = controller.countNodes("DorisFE", 1);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(3);
    }

    @Test
    void countNodes_serviceThrows_returnsFailResponse() {
        when(service.countNodes(any(), any()))
                .thenThrow(new RuntimeException("DB error"));
        
        ApiResponse<Integer> response = controller.countNodes("DorisFE", 1);
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(500);
    }

    @Test
    void query_serviceThrows_returnsFailResponse() {
        when(service.queryInstant(any(), any(), any(), anyDouble(), any(), any(), any(), any(), anyLong(), any()))
                .thenThrow(new IllegalStateException("No running DorisFE for cluster 1"));

        ApiResponse<PrometheusVectorResult> response =
                controller.query("jvm_vm_uptime", null, 1.0, ".+", ".+", null, 1, null, null, "gauge");
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(500);
        // 错误消息不暴露内部主机名/凭据
        assertThat(response.getErrorMessage()).doesNotContain("DorisFE");
    }

    @Test
    void queryRange_serviceThrows_returnsFailResponse() {
        when(service.queryRange(any(), any(), any(), anyDouble(), any(), any(),
                any(), any(), any(), anyLong(), anyLong(), anyLong(), any(), anyDouble(), any()))
                        .thenThrow(new RuntimeException("connection refused"));
        
        ApiResponse<PrometheusMatrixResult> response =
                controller.queryRange("some_metric", null, 1.0, ".+", ".+",
                        1000L, 2000L, 15L, 1, null, 0.5, null, null, null, null);
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(500);
    }

    @Test
    void query_responseEnvelopeMatchesPrometheusFormat() {
        when(service.queryInstant(any(), any(), any(), anyDouble(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(PrometheusVectorResult.of(Collections.emptyList()));

        ApiResponse<PrometheusVectorResult> response =
                controller.query("m", null, 1.0, ".+", ".+", null, 1, null, null, "gauge");
        
        assertThat(response.getData().resultType()).isEqualTo("vector");
        assertThat(response.getData().result()).isNotNull();
    }

    // ─── parseFilters / parseGroupBy 测试 ────────────────────────────────────────

    @Nested
    class ParamParsing {

        @Test
        void parseFilters_nullInput_returnsEmptyMap() {
            assertThat(OtelMetricsQueryController.parseFilters(null)).isEmpty();
        }

        @Test
        void parseFilters_blankInput_returnsEmptyMap() {
            assertThat(OtelMetricsQueryController.parseFilters("   ")).isEmpty();
        }

        @Test
        void parseFilters_singlePair_parsedCorrectly() {
            Map<String, String> result = OtelMetricsQueryController.parseFilters("group:fe");
            assertThat(result).containsEntry("group", "fe");
        }

        @Test
        void parseFilters_multiplePairs_allParsed() {
            Map<String, String> result = OtelMetricsQueryController.parseFilters("group:fe,type:used");
            assertThat(result).containsEntry("group", "fe").containsEntry("type", "used");
        }

        @Test
        void parseFilters_valueWithColon_takesFirstColon() {
            // value 含冒号时只在第一个冒号分割
            Map<String, String> result = OtelMetricsQueryController.parseFilters("path:/var/lib/doris");
            assertThat(result).containsEntry("path", "/var/lib/doris");
        }

        @Test
        void parseGroupBy_nullInput_returnsEmptyList() {
            assertThat(OtelMetricsQueryController.parseGroupBy(null)).isEmpty();
        }

        @Test
        void parseGroupBy_singleKey_returnsOneElement() {
            assertThat(OtelMetricsQueryController.parseGroupBy("mode")).containsExactly("mode");
        }

        @Test
        void parseGroupBy_multipleKeys_allParsed() {
            assertThat(OtelMetricsQueryController.parseGroupBy("mode,path"))
                    .containsExactly("mode", "path");
        }

        @Test
        void parseGroupBy_whitespaceAround_trimmed() {
            assertThat(OtelMetricsQueryController.parseGroupBy(" mode , path "))
                    .containsExactly("mode", "path");
        }
    }
}
