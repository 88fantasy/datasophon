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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @DisplayName("缓存过期瞬间的并发请求只触发一次 kubectl 查询，不发生穿透风暴")
    void deduplicatesConcurrentQueriesOnCacheMiss() throws InterruptedException {
        Fixture fixture = new Fixture();
        int concurrency = 16;
        CountDownLatch releaseAll = new CountDownLatch(1);
        when(fixture.k8sService.listHelmReleaseKeys(any())).thenAnswer(invocation -> {
            // 故意拉长这一次调用的耗时，扩大并发窗口，逼真模拟"多个 Tab 同时撞上 TTL 到期"
            releaseAll.await(2, TimeUnit.SECONDS);
            return List.of("prod/zookeeper");
        });

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            CountDownLatch allStarted = new CountDownLatch(concurrency);
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(() -> {
                    allStarted.countDown();
                    fixture.service.markMissing(7, List.of(imported(1, "prod", "zookeeper")));
                }));
            }
            allStarted.await(2, TimeUnit.SECONDS);
            releaseAll.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdown();
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

    @Test
    @DisplayName("sourceKind=CR 的实例不参与 Helm release 对账，不被误标失联")
    void doesNotMarkCrSourcedInstanceAsMissing() {
        Fixture fixture = new Fixture();
        // listHelmReleaseKeys 只查 Helm Secret 标签，CR 实例的 release_name 永远不会出现在这里，
        // 若不跳过就会被误判为 missing
        when(fixture.k8sService.listHelmReleaseKeys(any()))
                .thenReturn(List.of("apisix/apisix"));

        K8sServiceInstanceVO crInstance = imported(1, "doris", "doris-disaggregated-cluster");
        crInstance.setSourceKind("CR");
        fixture.service.markMissing(7, List.of(crInstance));

        assertThat(crInstance.getMissing()).isNull();
        verifyNoInteractions(fixture.k8sService);
    }

    @Test
    @DisplayName("CR 与普通 Helm 实例混合时，只对 Helm 实例做对账查询与标记")
    void onlyReconcilesHelmInstancesWhenMixedWithCr() {
        Fixture fixture = new Fixture();
        when(fixture.k8sService.listHelmReleaseKeys(any())).thenReturn(List.of("prod/zookeeper"));

        K8sServiceInstanceVO crInstance = imported(1, "doris", "doris-disaggregated-cluster");
        crInstance.setSourceKind("CR");
        K8sServiceInstanceVO helmInstance = imported(2, "spark", "kyuubi");

        fixture.service.markMissing(7, List.of(crInstance, helmInstance));

        assertThat(crInstance.getMissing()).isNull();
        assertThat(helmInstance.getMissing()).isTrue();
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
