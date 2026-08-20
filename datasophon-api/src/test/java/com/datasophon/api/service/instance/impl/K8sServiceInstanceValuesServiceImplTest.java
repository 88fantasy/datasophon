package com.datasophon.api.service.instance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.security.ImportedClusterWriteGuard;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.enums.ManageMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 覆盖 P0-1 修复：{@link K8sServiceInstanceValuesServiceImpl#update} 必须按记录自身的
 * clusterId 判定只读门禁——不管调用方是走 {@code /v2/cluster/{clusterId}/k8s/instance/{instanceId}/config}
 * （clusterId 可能与记录不一致）还是旧版 {@code /frame/k8sInstanceValues/update}（压根没有 clusterId
 * 路径变量），都必须在此拦截接管集群的写入。
 */
class K8sServiceInstanceValuesServiceImplTest {

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ImportedClusterWriteGuard writeGuard = new ImportedClusterWriteGuard(clusterInfoService);

    @Test
    @DisplayName("接管集群的记录拒绝写入")
    void rejectsUpdateOnImportedClusterRecord() {
        givenCluster(7, ManageMode.IMPORTED);
        K8sServiceInstanceValuesServiceImpl service = serviceWithGuard();
        K8sServiceInstanceValues db = existingRecord(7);
        doReturn(db).when(service).getById(db.getId());

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(db.getId());
        req.setDeltaValues("replicas: 99");

        assertThatThrownBy(() -> service.update(req))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("接管模式");
    }

    @Test
    @DisplayName("普通集群的记录正常写入")
    void allowsUpdateOnManagedClusterRecord() {
        givenCluster(7, ManageMode.MANAGED);
        K8sServiceInstanceValuesServiceImpl service = serviceWithGuard();
        K8sServiceInstanceValues db = existingRecord(7);
        doReturn(db).when(service).getById(db.getId());
        doReturn(true).when(service).updateById(any());

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(db.getId());
        req.setDeltaValues("replicas: 3");

        K8sServiceInstanceValues result = service.update(req);

        assertThat(result.getDeltaValues()).isEqualTo("replicas: 3");
    }

    @Test
    @DisplayName("记录不存在时按原逻辑报错")
    void rejectsUpdateWhenRecordMissing() {
        K8sServiceInstanceValuesServiceImpl service = serviceWithGuard();
        doReturn(null).when(service).getById(123);

        K8sServiceInstanceValuesUpdateDTO req = new K8sServiceInstanceValuesUpdateDTO();
        req.setId(123);

        assertThatThrownBy(() -> service.update(req))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("对象不存在");
    }

    private void givenCluster(int clusterId, ManageMode mode) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(clusterId);
        cluster.setClusterName("bjsy");
        cluster.setManageMode(mode);
        when(clusterInfoService.getById(clusterId)).thenReturn(cluster);
    }

    private K8sServiceInstanceValuesServiceImpl serviceWithGuard() {
        K8sServiceInstanceValuesServiceImpl service = spy(new K8sServiceInstanceValuesServiceImpl());
        ReflectionTestUtils.setField(service, "importedClusterWriteGuard", writeGuard);
        return service;
    }

    private static K8sServiceInstanceValues existingRecord(int clusterId) {
        K8sServiceInstanceValues values = new K8sServiceInstanceValues();
        values.setId(42);
        values.setClusterId(clusterId);
        values.setDeltaValues("replicas: 1");
        return values;
    }
}
