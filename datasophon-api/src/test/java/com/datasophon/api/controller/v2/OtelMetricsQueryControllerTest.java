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
        when(service.queryInstant(eq(1), eq("jvm_vm_uptime"), isNull(), eq(1.0),
                anyString(), anyString(), anyLong()))
                        .thenReturn(PrometheusVectorResult.of(List.of(sample)));
        
        ApiResponse<PrometheusVectorResult> response =
                controller.query("jvm_vm_uptime", null, 1.0, ".+", ".+", null, 1);
        
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
        when(service.queryRange(eq(1), eq("jvm_memory_heap_used"), isNull(), eq(1.0),
                anyString(), anyString(), anyLong(), anyLong(), anyLong(), any(), anyDouble()))
                        .thenReturn(matrix);
        
        ApiResponse<PrometheusMatrixResult> response =
                controller.queryRange("jvm_memory_heap_used", null, 1.0, ".+", ".+",
                        1000L, 2000L, 15L, 1, null, 0.5);
        
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
    void query_serviceThrows_returnsFailResponse() {
        when(service.queryInstant(any(), any(), any(), anyDouble(), any(), any(), anyLong()))
                .thenThrow(new IllegalStateException("No running DorisFE for cluster 1"));
        
        ApiResponse<PrometheusVectorResult> response =
                controller.query("jvm_vm_uptime", null, 1.0, ".+", ".+", null, 1);
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(500);
        // 错误消息不暴露内部主机名/凭据
        assertThat(response.getErrorMessage()).doesNotContain("DorisFE");
    }
    
    @Test
    void queryRange_serviceThrows_returnsFailResponse() {
        when(service.queryRange(any(), any(), any(), anyDouble(), any(), any(),
                anyLong(), anyLong(), anyLong(), any(), anyDouble()))
                        .thenThrow(new RuntimeException("connection refused"));
        
        ApiResponse<PrometheusMatrixResult> response =
                controller.queryRange("some_metric", null, 1.0, ".+", ".+",
                        1000L, 2000L, 15L, 1, null, 0.5);
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(500);
    }
    
    @Test
    void query_responseEnvelopeMatchesPrometheusFormat() {
        // 断言信封中 data 字段含 resultType，符合前端 promql.ts PrometheusVector 类型要求
        when(service.queryInstant(any(), any(), any(), anyDouble(), any(), any(), anyLong()))
                .thenReturn(PrometheusVectorResult.of(Collections.emptyList()));
        
        ApiResponse<PrometheusVectorResult> response =
                controller.query("m", null, 1.0, ".+", ".+", null, 1);
        
        assertThat(response.getData().resultType()).isEqualTo("vector");
        assertThat(response.getData().result()).isNotNull();
    }
}
