package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class K8sTakeoverReconcileServiceTest {

    @Test
    @DisplayName("release 还在的标 missing=false，已消失的标 true")
    void marksOnlyDisappearedReleases() {
        Fixture fixture = new Fixture();
        when(fixture.k8sService.listHelmReleaseKeys(any()))
                .thenReturn(List.of("prod/zookeeper", "apisix/apisix"));

        List<K8sServiceInstanceVO> instances = List.of(
                imported(1, "prod", "zookeeper"),
                imported(2, "spark", "kyuubi"));
        fixture.service.markMissing(7, instances);

        assertThat(instances.get(0).getMissing()).isFalse();
        assertThat(instances.get(1).getMissing()).isTrue();
    }

    @Test
    @DisplayName("平台安装的实例不参与对账，也不触发任何集群查询")
    void skipsWhenNoImportedInstance() {
        Fixture fixture = new Fixture();
        K8sServiceInstanceVO installed = imported(1, "prod", "zookeeper");
        installed.setSource(InstanceSource.INSTALLED.name());

        fixture.service.markMissing(7, List.of(installed));

        assertThat(installed.getMissing()).isNull();
        verifyNoInteractions(fixture.k8sService);
    }

    @Test
    @DisplayName("查询失败时一个都不标记——把连不上集群误报成服务没了比不报更糟")
    void doesNotMarkOnQueryFailure() {
        Fixture fixture = new Fixture();
        when(fixture.k8sService.listHelmReleaseKeys(any()))
                .thenThrow(new BusinessException("connection refused"));

        K8sServiceInstanceVO instance = imported(1, "prod", "zookeeper");
        fixture.service.markMissing(7, List.of(instance));

        assertThat(instance.getMissing()).isNull();
    }

    @Test
    @DisplayName("TTL 内重复调用命中缓存，3 秒轮询不会打穿到集群")
    void reusesCacheWithinTtl() {
        Fixture fixture = new Fixture();
        when(fixture.k8sService.listHelmReleaseKeys(any())).thenReturn(List.of("prod/zookeeper"));

        for (int i = 0; i < 10; i++) {
            fixture.service.markMissing(7, List.of(imported(1, "prod", "zookeeper")));
        }

        verify(fixture.k8sService, times(1)).listHelmReleaseKeys(any());
    }

    @Test
    @DisplayName("evict 后立刻重查，供重新扫描后刷新")
    void refetchesAfterEvict() {
        Fixture fixture = new Fixture();
        when(fixture.k8sService.listHelmReleaseKeys(any())).thenReturn(List.of("prod/zookeeper"));

        fixture.service.markMissing(7, List.of(imported(1, "prod", "zookeeper")));
        fixture.service.evict(7);
        fixture.service.markMissing(7, List.of(imported(1, "prod", "zookeeper")));

        verify(fixture.k8sService, times(2)).listHelmReleaseKeys(any());
    }

    private static K8sServiceInstanceVO imported(int id, String namespace, String releaseName) {
        K8sServiceInstanceVO instance = new K8sServiceInstanceVO();
        instance.setId(id);
        instance.setNamespace(namespace);
        instance.setReleaseName(releaseName);
        instance.setSource(InstanceSource.IMPORTED.name());
        return instance;
    }

    private static final class Fixture {

        final K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        final K8sService k8sService = mock(K8sService.class);
        final K8sTakeoverReconcileService service;

        Fixture() {
            when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
            service = new K8sTakeoverReconcileService(configService, k8sService);
        }
    }
}
