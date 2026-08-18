package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sTakeoverScanResult;
import com.datasophon.common.k8s.vo.helm.HelmReleaseListItemVO;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 接管扫描匹配逻辑测试。
 *
 * <p>release fixture 取自真实目标集群（2026-08-17，11 条 deployed release），
 * 重点覆盖 release 名与 chart 名不一致的情况。
 */
class K8sTakeoverScanServiceTest {

    @Test
    @DisplayName("按 chart 名匹配框架服务名，release 名与 chart 名不同时也能匹配上")
    void matchesByChartNameNotReleaseName() {
        // 真实边界：release 叫 fdb-cluster / otel-operator，chart 却叫 fdb-operator / opentelemetry-operator
        List<HelmReleaseListItemVO> releases = List.of(
                release("fdb-cluster", "doris", "fdb-operator-0.3.0"),
                release("otel-operator", "otel", "opentelemetry-operator-0.114.1"));
        List<FrameK8sServiceEntity> definitions = List.of(
                definition(1, "fdb-operator", "ENVIRONMENT", null),
                definition(2, "opentelemetry-operator", "ENVIRONMENT", null));

        K8sTakeoverScanResult result = scan(releases, definitions);

        assertThat(result.pending()).isEmpty();
        assertThat(result.matched()).hasSize(2);
        assertThat(result.matched().get(0).releaseName()).isEqualTo("fdb-cluster");
        assertThat(result.matched().get(0).chartName()).isEqualTo("fdb-operator");
        assertThat(result.matched().get(0).frameServiceId()).isEqualTo(1);
        assertThat(result.matched().get(1).frameServiceName()).isEqualTo("opentelemetry-operator");
    }

    @Test
    @DisplayName("框架服务名对不上时，回落用 artifact.helm 的包名匹配")
    void fallsBackToArtifactHelmPackageName() {
        List<HelmReleaseListItemVO> releases = List.of(
                release("apisix", "apisix", "apisix-2.12.5"));
        // 服务名取了别名，靠 artifact.helm 里的包名兜住
        List<FrameK8sServiceEntity> definitions = List.of(
                definition(9, "gateway", "MIDDLEWARE", "{\"helm\":\"apisix-2.12.5.tgz\"}"));

        K8sTakeoverScanResult result = scan(releases, definitions);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.matched().get(0).frameServiceId()).isEqualTo(9);
        assertThat(result.matched().get(0).catalog()).isEqualTo("MIDDLEWARE");
    }

    @Test
    @DisplayName("框架目录里没有的 release 进入 pending，字段仍完整可供人工绑定")
    void putsUnknownReleaseIntoPending() {
        List<HelmReleaseListItemVO> releases = List.of(
                release("kyuubi", "spark", "kyuubi-0.1.0"));

        K8sTakeoverScanResult result = scan(releases, List.of());

        assertThat(result.matched()).isEmpty();
        assertThat(result.pending()).hasSize(1);
        K8sTakeoverScanResult.ScannedRelease pending = result.pending().get(0);
        assertThat(pending.releaseName()).isEqualTo("kyuubi");
        assertThat(pending.namespace()).isEqualTo("spark");
        assertThat(pending.chartName()).isEqualTo("kyuubi");
        assertThat(pending.chartVersion()).isEqualTo("0.1.0");
        assertThat(pending.frameServiceId()).isNull();
    }

    @Test
    @DisplayName("重扫时已登记的 release 标 registered，供前端默认不重复勾选")
    void marksAlreadyRegisteredRelease() {
        List<HelmReleaseListItemVO> releases = List.of(
                release("zookeeper", "prod", "zookeeper-13.8.7"),
                release("kyuubi", "spark", "kyuubi-0.1.0"));
        List<FrameK8sServiceEntity> definitions = List.of(
                definition(2, "zookeeper", "MIDDLEWARE", null),
                definition(3, "kyuubi", "MIDDLEWARE", null));

        K8sTakeoverScanResult result = scan(releases, definitions,
                List.of(imported(101, "prod", "zookeeper", "zookeeper")));

        assertThat(result.matched()).hasSize(2);
        assertThat(result.matched().get(0).registered()).isTrue();
        assertThat(result.matched().get(1).registered()).isFalse();
        assertThat(result.missing()).isEmpty();
    }

    @Test
    @DisplayName("集群里已经卸掉的 release，其登记项进入 missing 但不自动删除")
    void reportsMissingInstanceWithoutDeleting() {
        List<HelmReleaseListItemVO> releases = List.of(
                release("zookeeper", "prod", "zookeeper-13.8.7"));
        List<FrameK8sServiceEntity> definitions = List.of(
                definition(2, "zookeeper", "MIDDLEWARE", null));

        K8sTakeoverScanResult result = scan(releases, definitions, List.of(
                imported(101, "prod", "zookeeper", "zookeeper"),
                imported(102, "spark", "kyuubi", "kyuubi")));

        assertThat(result.missing()).hasSize(1);
        K8sTakeoverScanResult.MissingInstance missing = result.missing().get(0);
        assertThat(missing.instanceId()).isEqualTo(102);
        assertThat(missing.releaseName()).isEqualTo("kyuubi");
        assertThat(missing.namespace()).isEqualTo("spark");
    }

    @Test
    @DisplayName("同名不同命名空间的 release 不会互相顶替")
    void distinguishesSameNameInDifferentNamespaces() {
        List<HelmReleaseListItemVO> releases = List.of(
                release("zookeeper", "prod", "zookeeper-13.8.7"));
        List<FrameK8sServiceEntity> definitions = List.of(
                definition(2, "zookeeper", "MIDDLEWARE", null));

        K8sTakeoverScanResult result = scan(releases, definitions,
                List.of(imported(101, "staging", "zookeeper", "zookeeper")));

        assertThat(result.matched().get(0).registered()).isFalse();
        assertThat(result.missing()).hasSize(1);
        assertThat(result.missing().get(0).namespace()).isEqualTo("staging");
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions) {
        return scan(releases, definitions, List.of());
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions,
                                       List<K8sServiceInstanceVO> registered) {
        HelmReleaseReader reader = mock(HelmReleaseReader.class);
        FrameK8sServiceService frameService = mock(FrameK8sServiceService.class);
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        K8sTakeoverReconcileService reconcileService = mock(K8sTakeoverReconcileService.class);
        when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
        when(frameService.listNewest(7)).thenReturn(definitions);
        when(reader.listDeployed(any())).thenReturn(releases);
        when(instanceService.queryInstanceList(7)).thenReturn(registered);

        return new K8sTakeoverScanService(
                reader, frameService, configService, instanceService, reconcileService).scan(7);
    }

    /** 构造一条已登记的接管实例。 */
    private static K8sServiceInstanceVO imported(int id, String namespace, String releaseName,
                                                 String serviceName) {
        K8sServiceInstanceVO instance = new K8sServiceInstanceVO();
        instance.setId(id);
        instance.setNamespace(namespace);
        instance.setReleaseName(releaseName);
        instance.setServiceName(serviceName);
        instance.setSource(InstanceSource.IMPORTED.name());
        return instance;
    }

    private static HelmReleaseListItemVO release(String name, String namespace, String chart) {
        HelmReleaseListItemVO release = new HelmReleaseListItemVO();
        release.setName(name);
        release.setNamespace(namespace);
        release.setChart(chart);
        release.setStatus("deployed");
        return release;
    }

    private static FrameK8sServiceEntity definition(Integer id, String serviceName, String type, String artifact) {
        FrameK8sServiceEntity definition = new FrameK8sServiceEntity();
        definition.setId(id);
        definition.setServiceName(serviceName);
        definition.setType(type);
        definition.setArtifact(artifact);
        return definition;
    }
}
