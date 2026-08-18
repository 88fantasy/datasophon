package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class K8sTakeoverInstanceServiceTest {

    @Test
    @DisplayName("取消接管只删登记记录，不触碰目标集群")
    void cancelRemovesOnlyLocalRecords() {
        Fixture fixture = new Fixture(importedInstance());

        fixture.service.cancelTakeover(7, 42);

        verify(fixture.valuesService).removeByInstanceId(42);
        verify(fixture.instanceService).removeById(42);
        // 这是本用例的核心断言：不能走到任何会调 helm uninstall 的路径
        verify(fixture.instanceService, never()).removeInstanceId(anyInt());
        verifyNoInteractions(fixture.helmReader);
    }

    @Test
    @DisplayName("平台安装的实例不能走取消接管")
    void rejectsInstalledInstance() {
        K8sServiceInstanceVO installed = importedInstance();
        installed.setSource(InstanceSource.INSTALLED.name());
        Fixture fixture = new Fixture(installed);

        assertThatThrownBy(() -> fixture.service.cancelTakeover(7, 42))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("平台安装");
        verify(fixture.instanceService, never()).removeById(anyInt());
    }

    @Test
    @DisplayName("跨集群访问实例被拒")
    void rejectsInstanceFromAnotherCluster() {
        Fixture fixture = new Fixture(importedInstance());

        assertThatThrownBy(() -> fixture.service.cancelTakeover(9, 42))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("不属于该集群");
    }

    @Test
    @DisplayName("反查配置走 helm get values，按登记的 release 名与命名空间")
    void readsValuesByRegisteredRelease() {
        Fixture fixture = new Fixture(importedInstance());
        when(fixture.helmReader.getValues(any(), eq("zookeeper"), eq("prod"))).thenReturn("{\"replicas\":3}");

        assertThat(fixture.service.readValues(7, 42)).isEqualTo("{\"replicas\":3}");
    }

    @Test
    @DisplayName("未登记 release 名时明确报错，而不是拿空名去调 helm")
    void rejectsMissingReleaseName() {
        K8sServiceInstanceVO noRelease = importedInstance();
        noRelease.setReleaseName("  ");
        Fixture fixture = new Fixture(noRelease);

        assertThatThrownBy(() -> fixture.service.readValues(7, 42))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("release 名");
        verifyNoInteractions(fixture.helmReader);
    }

    private static K8sServiceInstanceVO importedInstance() {
        K8sServiceInstanceVO instance = new K8sServiceInstanceVO();
        instance.setId(42);
        instance.setClusterId(7);
        instance.setNamespace("prod");
        instance.setServiceName("zookeeper");
        instance.setSource(InstanceSource.IMPORTED.name());
        instance.setReleaseName("zookeeper");
        return instance;
    }

    private static final class Fixture {

        final K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        final K8sServiceInstanceValuesService valuesService = mock(K8sServiceInstanceValuesService.class);
        final K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        final HelmReleaseReader helmReader = mock(HelmReleaseReader.class);
        final K8sTakeoverInstanceService service;

        Fixture(K8sServiceInstanceVO instance) {
            when(instanceService.getVoById(42)).thenReturn(Optional.of(instance));
            when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
            service = new K8sTakeoverInstanceService(
                    instanceService, valuesService, configService, helmReader);
        }
    }
}
