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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.ClusterDashboardResponse;
import com.datasophon.api.service.ClusterDashboardService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterDashboardV2ControllerTest {

    private ClusterDashboardService service;
    private ClusterDashboardV2Controller controller;

    @BeforeEach
    void setUp() {
        service = mock(ClusterDashboardService.class);
        controller = new ClusterDashboardV2Controller(service);
    }

    @Test
    void summary_returnsSuccessWithServiceResult() {
        ClusterDashboardResponse response = ClusterDashboardResponse.builder()
                .stats(ClusterDashboardResponse.Stats.builder().hostTotal(5).build())
                .build();
        when(service.getDashboard(eq(1))).thenReturn(response);

        ApiResponse<ClusterDashboardResponse> result = controller.summary(1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStats().getHostTotal()).isEqualTo(5);
        verify(service).getDashboard(1);
    }

    @Test
    void summary_returnsFailureWhenServiceThrows() {
        when(service.getDashboard(eq(1))).thenThrow(new RuntimeException("boom"));

        ApiResponse<ClusterDashboardResponse> result = controller.summary(1);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(500);
    }
}
