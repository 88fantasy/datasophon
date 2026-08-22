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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sTakeoverReconcileService;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClusterK8sV2ControllerTest {

    private final K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
    private final K8sClusterNamespaceService namespaceService = mock(K8sClusterNamespaceService.class);
    private final K8sTakeoverReconcileService reconcileService = mock(K8sTakeoverReconcileService.class);
    private final ClusterK8sV2Controller controller = new ClusterK8sV2Controller();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "k8sServiceInstanceService", instanceService);
        ReflectionTestUtils.setField(controller, "k8sClusterNamespaceService", namespaceService);
        ReflectionTestUtils.setField(controller, "k8sTakeoverReconcileService", reconcileService);
    }

    @Test
    void getInstanceReturnsNotFoundWhenInstanceDoesNotBelongToPathCluster() {
        when(instanceService.getVoByClusterAndId(1, 9)).thenReturn(Optional.empty());

        ApiResponse<K8sServiceInstanceVO> response = controller.getInstance(1, 9);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(instanceService).getVoByClusterAndId(1, 9);
    }

    @Test
    void resourceTypesRejectCrossClusterInstanceBeforeQueryingKubernetes() {
        when(instanceService.getVoByClusterAndId(1, 9)).thenReturn(Optional.empty());

        ApiResponse<?> response = controller.listResourceTypes(1, 9);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(instanceService, never()).listResourceType(any());
    }

    @Test
    void resourcesRejectCrossClusterInstanceBeforeQueryingKubernetes() {
        when(instanceService.getVoByClusterAndId(1, 9)).thenReturn(Optional.empty());

        ApiResponse<?> response = controller.listResources(1, 9, "Pod");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(instanceService, never()).listResource(any());
    }

    @Test
    void listAllInstancesDoesNotReconcileNamespacesOnPollingPath() {
        when(instanceService.queryInstanceList(1)).thenReturn(List.of());

        ApiResponse<List<K8sServiceInstanceVO>> response = controller.listAllInstances(1);

        assertThat(response.isSuccess()).isTrue();
        verify(namespaceService, never()).listAndUpdateNamespaceByClusterId(1);
        verify(reconcileService).markMissing(1, List.of());
    }
}
