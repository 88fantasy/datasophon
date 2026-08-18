package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sTakeoverRegisterResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.enums.k8s.InstanceSource;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class K8sTakeoverRegisterServiceTest {

    @Test
    @DisplayName("登记时标记来源为 IMPORTED 并写入 release 名与探测到的 job")
    void marksInstanceAsImportedWithProbedJobs() {
        Fixture fixture = new Fixture();
        when(fixture.jobProbe.probe(any(), eq("dolphinscheduler"), eq("prod"), any()))
                .thenReturn("dolphinscheduler-api,dolphinscheduler-master-headless");

        List<K8sTakeoverRegisterResult> results = fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("dolphinscheduler", "prod", 3)));

        ArgumentCaptor<K8sServiceInstance> captor = ArgumentCaptor.forClass(K8sServiceInstance.class);
        verify(fixture.instanceService).updateById(captor.capture());
        K8sServiceInstance saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(InstanceSource.IMPORTED);
        assertThat(saved.getReleaseName()).isEqualTo("dolphinscheduler");
        assertThat(saved.getMetricsJob()).isEqualTo("dolphinscheduler-api,dolphinscheduler-master-headless");
        assertThat(saved.getState()).isEqualTo(1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).scraped()).isTrue();
    }

    @Test
    @DisplayName("未接入采集的服务照常登记，但标记 scraped=false 供前端提示")
    void registersUnscrapedServiceWithDiagnostic() {
        Fixture fixture = new Fixture();
        when(fixture.jobProbe.probe(any(), eq("redis-cluster"), eq("prod"), any())).thenReturn(null);

        List<K8sTakeoverRegisterResult> results = fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("redis-cluster", "prod", 5)));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).scraped()).isFalse();
        assertThat(results.get(0).metricsJob()).isNull();
    }

    @Test
    @DisplayName("整批登记只查一次 Doris 活跃 job")
    void queriesActiveJobsOnlyOncePerBatch() {
        Fixture fixture = new Fixture();

        fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("apisix", "apisix", 1),
                new K8sTakeoverRegisterService.Binding("zookeeper", "prod", 2),
                new K8sTakeoverRegisterService.Binding("kyuubi", "spark", 3)));

        verify(fixture.jobProbe).activeJobs(7);
    }

    @Test
    @DisplayName("未选择服务或集群未配置连接时给出明确错误")
    void rejectsInvalidInput() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.register(7, List.of()))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("未选择");

        Fixture noConfig = new Fixture();
        when(noConfig.configService.getByClusterId(7)).thenReturn(null);
        assertThatThrownBy(() -> noConfig.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("apisix", "apisix", 1))))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("K8s 连接信息");
    }

    /** 组装一套默认可用的依赖，各用例只覆写关心的部分。 */
    private static final class Fixture {

        final K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        final K8sClusterNamespaceService namespaceService = mock(K8sClusterNamespaceService.class);
        final K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        final K8sMetricsJobProbeService jobProbe = mock(K8sMetricsJobProbeService.class);
        final K8sTakeoverRegisterService service;

        Fixture() {
            when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
            K8sClusterNamespace namespace = new K8sClusterNamespace();
            namespace.setId(11);
            when(namespaceService.createIfAbsent(any(), anyInt())).thenReturn(namespace);
            when(instanceService.createIfAbsent(anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> new K8sServiceInstance());
            when(jobProbe.activeJobs(anyInt())).thenReturn(Set.of("apisix-prometheus-metrics"));
            when(jobProbe.probe(any(), anyString(), anyString(), any())).thenReturn(null);
            service = new K8sTakeoverRegisterService(
                    configService, namespaceService, instanceService, jobProbe);
        }
    }
}
