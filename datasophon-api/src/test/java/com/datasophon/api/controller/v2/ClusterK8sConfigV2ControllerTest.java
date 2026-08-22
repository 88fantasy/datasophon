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
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 覆盖 P0-1 修复：{@code @ImportedReadOnly} 拦截器只按路径 clusterId 判定，
 * {@link ClusterK8sConfigV2Controller#save} 必须自行确认 body 里的 values id
 * 确实属于路径声明的集群，否则换个 clusterId 就能改到别的集群的配置。
 *
 * <p>另覆盖 C1 修复：{@code info} / {@code versions} 两个读端点同样必须校验
 * 路径声明的 clusterId（及 instanceId），否则换个 URL 就能跨集群读到 Helm values 全文。
 */
class ClusterK8sConfigV2ControllerTest {

    private final K8sServiceInstanceValuesService valuesService = mock(K8sServiceInstanceValuesService.class);
    private final ClusterK8sConfigV2Controller controller = new ClusterK8sConfigV2Controller();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "k8sServiceInstanceValuesService", valuesService);
    }

    @Test
    void rejectsWhenValuesRecordBelongsToAnotherCluster() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(9);
        when(valuesService.getById(42)).thenReturn(db);

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(42);
        req.setDeltaValues("replicas: 3");

        // 路径声明的集群是 1，记录实际属于集群 9——典型的换 URL 越权写入。
        ApiResponse<Void> response = controller.save(1, req);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(valuesService, never()).update(req);
    }

    @Test
    void rejectsWhenValuesRecordDoesNotExist() {
        when(valuesService.getById(42)).thenReturn(null);

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(42);

        ApiResponse<Void> response = controller.save(1, req);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(valuesService, never()).update(req);
    }

    @Test
    void allowsWhenValuesRecordBelongsToPathCluster() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(1);
        when(valuesService.getById(42)).thenReturn(db);

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(42);
        req.setDeltaValues("replicas: 3");

        ApiResponse<Void> response = controller.save(1, req);

        assertThat(response.isSuccess()).isTrue();
        verify(valuesService).update(req);
    }

    @Test
    void infoRejectsWhenRecordBelongsToAnotherCluster() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(9);
        db.setInstanceId(5);
        when(valuesService.getById(42)).thenReturn(db);

        // 路径声明的集群是 1，记录实际属于集群 9。
        ApiResponse<K8sServiceInstanceValues> response = controller.info(1, 5, 42);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
    }

    @Test
    void infoRejectsWhenRecordBelongsToAnotherInstance() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(1);
        db.setInstanceId(9);
        when(valuesService.getById(42)).thenReturn(db);

        // 路径声明的集群匹配，但实例不匹配。
        ApiResponse<K8sServiceInstanceValues> response = controller.info(1, 5, 42);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
    }

    @Test
    void infoRejectsWhenRecordDoesNotExist() {
        when(valuesService.getById(42)).thenReturn(null);

        ApiResponse<K8sServiceInstanceValues> response = controller.info(1, 5, 42);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
    }

    @Test
    void infoAllowsWhenRecordBelongsToPathClusterAndInstance() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(1);
        db.setInstanceId(5);
        when(valuesService.getById(42)).thenReturn(db);

        ApiResponse<K8sServiceInstanceValues> response = controller.info(1, 5, 42);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(db);
    }

    @Test
    void versionsRejectsWhenRecordsBelongToAnotherCluster() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(9);
        db.setInstanceId(5);
        when(valuesService.listSimpleByInstanceId(5)).thenReturn(List.of(db));

        // 路径声明的集群是 1，instanceId=5 实际属于集群 9——换个 instanceId 即可越权枚举版本列表。
        ApiResponse<List<K8sServiceInstanceValues>> response = controller.versions(1, 5);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
    }

    @Test
    void versionsAllowsWhenRecordsBelongToPathCluster() {
        K8sServiceInstanceValues db = new K8sServiceInstanceValues();
        db.setId(42);
        db.setClusterId(1);
        db.setInstanceId(5);
        when(valuesService.listSimpleByInstanceId(5)).thenReturn(List.of(db));

        ApiResponse<List<K8sServiceInstanceValues>> response = controller.versions(1, 5);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly(db);
    }

    @Test
    void versionsAllowsWhenInstanceHasNoRecordsYet() {
        when(valuesService.listSimpleByInstanceId(5)).thenReturn(List.of());

        ApiResponse<List<K8sServiceInstanceValues>> response = controller.versions(1, 5);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
    }
}
