package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.security.K8sTakeoverAccessGuard;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sTakeoverRegisterResult;
import com.datasophon.api.vo.k8s.K8sTakeoverScanResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.enums.k8s.InstanceSourceKind;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class K8sTakeoverRegisterServiceTest {

    @Test
    @DisplayName("登记时标记来源为 IMPORTED 并写入 release 名与探测到的 job")
    void marksInstanceAsImportedWithProbedJobs() {
        Fixture fixture = new Fixture();
        when(fixture.jobProbe.probe(any(), eq("dolphinscheduler"), eq("prod"), any(), isNull()))
                .thenReturn(new K8sMetricsJobProbeService.ProbeResult(
                        "dolphinscheduler-api,dolphinscheduler-master-headless", Map.of()));

        List<K8sTakeoverRegisterResult> results = fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("dolphinscheduler", "prod", 3, null)));

        ArgumentCaptor<K8sServiceInstance> captor = ArgumentCaptor.forClass(K8sServiceInstance.class);
        verify(fixture.instanceService).updateById(captor.capture());
        K8sServiceInstance saved = captor.getValue();
        assertThat(saved.getSource()).isEqualTo(InstanceSource.IMPORTED);
        assertThat(saved.getSourceKind()).isEqualTo(InstanceSourceKind.HELM);
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
        when(fixture.jobProbe.probe(any(), eq("redis-cluster"), eq("prod"), any(), isNull()))
                .thenReturn(new K8sMetricsJobProbeService.ProbeResult(null, Map.of()));

        List<K8sTakeoverRegisterResult> results = fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("redis-cluster", "prod", 5, null)));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).scraped()).isFalse();
        assertThat(results.get(0).metricsJob()).isNull();
    }

    @Test
    @DisplayName("整批登记只查一次 Doris 活跃 job")
    void queriesActiveJobsOnlyOncePerBatch() {
        Fixture fixture = new Fixture();

        fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("apisix", "apisix", 1, null),
                new K8sTakeoverRegisterService.Binding("zookeeper", "prod", 2, null),
                new K8sTakeoverRegisterService.Binding("kyuubi", "spark", 3, null)));

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
                new K8sTakeoverRegisterService.Binding("apisix", "apisix", 1, null))))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("K8s 连接信息");
    }

    @Test
    @DisplayName("sourceKind=CR 时解析 artifact.operator，写 source_kind=CR 与 monitor_profile")
    void registersCrBindingWithMonitorProfile() {
        Fixture fixture = new Fixture();
        FrameK8sServiceEntity dorisDefinition = new FrameK8sServiceEntity();
        dorisDefinition.setId(5);
        dorisDefinition.setArtifact("{\"yaml\":\"ddc-cluster.yaml\",\"kind\":\"operator\",\"operator\":{"
                + "\"group\":\"disaggregated.cluster.doris.com\",\"version\":\"v1\","
                + "\"kind\":\"DorisDisaggregatedCluster\",\"plural\":\"dorisdisaggregatedclusters\","
                + "\"monitorProfile\":\"doris-disaggregated\","
                + "\"roles\":[{\"name\":\"fe\",\"jobPattern\":\"-fe$\"},"
                + "{\"name\":\"compute\",\"jobPattern\":\"-cg\\\\d+$\"}]}}");
        when(fixture.frameService.getById(5)).thenReturn(dorisDefinition);
        Map<String, List<String>> roleJobs = Map.of(
                "fe", List.of("doris-disaggregated-cluster-fe"),
                "compute", List.of("doris-disaggregated-cluster-cg1", "doris-disaggregated-cluster-cg2"));
        when(fixture.jobProbe.probe(any(), eq("doris-disaggregated-cluster"), eq("doris"), any(), any()))
                .thenReturn(new K8sMetricsJobProbeService.ProbeResult(
                        "doris-disaggregated-cluster-fe,doris-disaggregated-cluster-cg1,"
                                + "doris-disaggregated-cluster-cg2",
                        roleJobs));

        List<K8sTakeoverRegisterResult> results = fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("doris-disaggregated-cluster", "doris", 5, "CR")));

        ArgumentCaptor<K8sServiceInstance> captor = ArgumentCaptor.forClass(K8sServiceInstance.class);
        verify(fixture.instanceService).updateById(captor.capture());
        K8sServiceInstance saved = captor.getValue();
        assertThat(saved.getSourceKind()).isEqualTo(InstanceSourceKind.CR);
        assertThat(saved.getMonitorProfile()).contains("doris-disaggregated").contains("fe").contains("compute");

        assertThat(results.get(0).roleJobs()).isEqualTo(roleJobs);
    }

    @Test
    @DisplayName("请求中的 release 不在最新扫描结果时拒绝登记")
    void rejectsBindingNotPresentInLatestScan() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("forged-release", "prod", 3, "HELM"))))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("不在最新扫描结果");
        verify(fixture.instanceService, org.mockito.Mockito.never())
                .createImportedIfAbsent(anyInt(), anyInt(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("sourceKind=CR 但探测不到任何 job 时不写 monitor_profile")
    void crBindingWithoutRoleJobsLeavesMonitorProfileNull() {
        Fixture fixture = new Fixture();
        FrameK8sServiceEntity definition = new FrameK8sServiceEntity();
        definition.setId(5);
        definition.setArtifact("{\"kind\":\"operator\",\"operator\":{\"group\":\"g\",\"plural\":\"p\"}}");
        when(fixture.frameService.getById(5)).thenReturn(definition);
        when(fixture.jobProbe.probe(any(), anyString(), anyString(), any(), any()))
                .thenReturn(new K8sMetricsJobProbeService.ProbeResult(null, Map.of()));

        fixture.service.register(7, List.of(
                new K8sTakeoverRegisterService.Binding("doris-disaggregated-cluster", "doris", 5, "CR")));

        ArgumentCaptor<K8sServiceInstance> captor = ArgumentCaptor.forClass(K8sServiceInstance.class);
        verify(fixture.instanceService).updateById(captor.capture());
        assertThat(captor.getValue().getMonitorProfile()).isNull();
    }

    /** 组装一套默认可用的依赖，各用例只覆写关心的部分。 */
    private static final class Fixture {

        final K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        final K8sClusterNamespaceService namespaceService = mock(K8sClusterNamespaceService.class);
        final K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        final K8sMetricsJobProbeService jobProbe = mock(K8sMetricsJobProbeService.class);
        final FrameK8sServiceService frameService = mock(FrameK8sServiceService.class);
        final K8sTakeoverScanService scanService = mock(K8sTakeoverScanService.class);
        final K8sTakeoverAccessGuard accessGuard = mock(K8sTakeoverAccessGuard.class);
        final K8sTakeoverRegisterService service;

        Fixture() {
            when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
            K8sClusterNamespace namespace = new K8sClusterNamespace();
            namespace.setId(11);
            when(namespaceService.createIfAbsent(any(), anyInt())).thenReturn(namespace);
            when(instanceService.createImportedIfAbsent(anyInt(), anyInt(), anyInt(), any(), anyString()))
                    .thenAnswer(invocation -> new K8sServiceInstance());
            when(jobProbe.activeJobs(anyInt())).thenReturn(Set.of("apisix-prometheus-metrics"));
            when(jobProbe.probe(any(), anyString(), anyString(), any(), any()))
                    .thenReturn(new K8sMetricsJobProbeService.ProbeResult(null, Map.of()));
            when(scanService.scan(7)).thenReturn(new K8sTakeoverScanResult(
                    List.of(
                            scanned("dolphinscheduler", "prod", 3, "HELM"),
                            scanned("redis-cluster", "prod", 5, "HELM"),
                            scanned("apisix", "apisix", 1, "HELM"),
                            scanned("zookeeper", "prod", 2, "HELM"),
                            scanned("kyuubi", "spark", 3, "HELM"),
                            scanned("doris-disaggregated-cluster", "doris", 5, "CR")),
                    List.of(), List.of()));
            service = new K8sTakeoverRegisterService(
                    configService, namespaceService, instanceService, jobProbe, frameService, scanService, accessGuard);
        }

        private static K8sTakeoverScanResult.ScannedRelease scanned(
                                                                    String releaseName, String namespace,
                                                                    int frameServiceId, String sourceKind) {
            return new K8sTakeoverScanResult.ScannedRelease(
                    releaseName, namespace, null, null, null, null,
                    frameServiceId, "service-" + frameServiceId, "MIDDLEWARE",
                    false, sourceKind, "CR".equals(sourceKind) ? "CustomResource" : null);
        }
    }
}
