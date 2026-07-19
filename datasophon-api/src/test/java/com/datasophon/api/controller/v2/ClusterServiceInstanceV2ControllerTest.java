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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClusterServiceInstanceV2ControllerTest {

    private final ClusterServiceInstanceService service = mock(ClusterServiceInstanceService.class);
    private final ClusterServiceInstanceV2Controller controller = new ClusterServiceInstanceV2Controller();

    ClusterServiceInstanceV2ControllerTest() {
        ReflectionTestUtils.setField(controller, "clusterServiceInstanceService", service);
    }

    @Test
    void deleteRejectsInstanceFromAnotherCluster() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(9);
        entity.setClusterId(2);
        when(service.getById(9)).thenReturn(entity);

        ApiResponse<Void> response = controller.delete(1, 9);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(service, never()).delServiceInstance(9);
    }

    @Test
    void deleteRemovesInstanceFromRequestedCluster() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(9);
        entity.setClusterId(1);
        when(service.getById(9)).thenReturn(entity);
        when(service.delServiceInstance(9)).thenReturn(Result.success());

        ApiResponse<Void> response = controller.delete(1, 9);

        assertThat(response.isSuccess()).isTrue();
        verify(service).delServiceInstance(9);
    }
}
