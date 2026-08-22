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

package com.datasophon.api.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.mapper.ClusterInfoMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 覆盖 C2 修复：接管（{@link ManageMode#IMPORTED}）的 K8s 集群按定义就是在运行的、
 * 且平台刻意不允许停它们（只读接管承诺），因此删除该类集群时必须跳过
 * running-instance 前置检查；非 IMPORTED 的 K8s 集群行为不变。
 */
class ClusterInfoServiceImplDeleteClusterTest {

    private final ClusterInfoMapper clusterInfoMapper = mock(ClusterInfoMapper.class);
    private final K8sServiceInstanceService k8sServiceInstanceService = mock(K8sServiceInstanceService.class);
    private final ClusterInfoServiceImpl service = new ClusterInfoServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", clusterInfoMapper);
        ReflectionTestUtils.setField(service, "k8sServiceInstanceService", k8sServiceInstanceService);
        // deleteCluster 用 TransactionSynchronizationManager.registerSynchronization 挂载
        // 异步删除回调，未激活事务同步时会抛 IllegalStateException。
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void importedK8sClusterSkipsRunningInstanceCheckEvenWhenInstancesAreRunning() {
        ClusterInfoEntity cluster = k8sCluster(1, ManageMode.IMPORTED, ClusterState.RUNNING);
        when(clusterInfoMapper.selectById(1)).thenReturn(cluster);
        when(k8sServiceInstanceService.hasRunningInstance(1)).thenReturn(true);

        assertThatCode(() -> service.deleteCluster(1)).doesNotThrowAnyException();

        verify(k8sServiceInstanceService, never()).hasRunningInstance(1);
    }

    @Test
    void nonImportedK8sClusterRejectsDeleteWhenInstancesAreRunning() {
        ClusterInfoEntity cluster = k8sCluster(2, ManageMode.MANAGED, ClusterState.RUNNING);
        when(clusterInfoMapper.selectById(2)).thenReturn(cluster);
        when(k8sServiceInstanceService.hasRunningInstance(2)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCluster(2))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("存在正在运行的实例");
    }

    @Test
    void nonImportedK8sClusterAllowsDeleteWhenNoInstancesAreRunning() {
        ClusterInfoEntity cluster = k8sCluster(3, ManageMode.MANAGED, ClusterState.RUNNING);
        when(clusterInfoMapper.selectById(3)).thenReturn(cluster);
        when(k8sServiceInstanceService.hasRunningInstance(3)).thenReturn(false);

        assertThatCode(() -> service.deleteCluster(3)).doesNotThrowAnyException();

        verify(k8sServiceInstanceService).hasRunningInstance(3);
    }

    private static ClusterInfoEntity k8sCluster(Integer id, ManageMode manageMode, ClusterState state) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(id);
        cluster.setClusterName("cluster-" + id);
        cluster.setArchType(ClusterArchType.k8s);
        cluster.setManageMode(manageMode);
        cluster.setClusterState(state);
        return cluster;
    }
}
