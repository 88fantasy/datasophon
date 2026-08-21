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
import com.datasophon.dao.enums.k8s.InstanceSourceKind;
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

    @Test
    @DisplayName("operator CR 扫描结果标 sourceKind=CR，进入 matched 不落 pending")
    void crResultEntersMatchedWithSourceKindCr() {
        FrameK8sServiceEntity dorisDefinition = definition(5, "doris", "MIDDLEWARE", null);
        K8sCrScanner.ScannedCr cr = new K8sCrScanner.ScannedCr(
                dorisDefinition, "doris-disaggregated-cluster", "doris", "DorisDisaggregatedCluster");

        K8sTakeoverScanResult result = scan(List.of(), List.of(dorisDefinition), List.of(), List.of(cr));

        assertThat(result.pending()).isEmpty();
        assertThat(result.matched()).hasSize(1);
        K8sTakeoverScanResult.ScannedRelease scanned = result.matched().get(0);
        assertThat(scanned.releaseName()).isEqualTo("doris-disaggregated-cluster");
        assertThat(scanned.namespace()).isEqualTo("doris");
        assertThat(scanned.sourceKind()).isEqualTo(InstanceSourceKind.CR);
        assertThat(scanned.frameServiceId()).isEqualTo(5);
        assertThat(scanned.chart()).isNull();
    }

    @Test
    @DisplayName("Helm release 结果标 sourceKind=HELM")
    void helmResultHasSourceKindHelm() {
        List<HelmReleaseListItemVO> releases = List.of(release("zookeeper", "prod", "zookeeper-13.8.7"));
        List<FrameK8sServiceEntity> definitions = List.of(definition(2, "zookeeper", "MIDDLEWARE", null));

        K8sTakeoverScanResult result = scan(releases, definitions);

        assertThat(result.matched().get(0).sourceKind()).isEqualTo(InstanceSourceKind.HELM);
    }

    @Test
    @DisplayName("已登记的 CR 实例不会被误报进 missing")
    void registeredCrInstanceNotReportedMissing() {
        FrameK8sServiceEntity dorisDefinition = definition(5, "doris", "MIDDLEWARE", null);
        K8sCrScanner.ScannedCr cr = new K8sCrScanner.ScannedCr(
                dorisDefinition, "doris-disaggregated-cluster", "doris", "DorisDisaggregatedCluster");
        List<K8sServiceInstanceVO> registered =
                List.of(imported(201, "doris", "doris-disaggregated-cluster", "doris", "CR"));

        K8sTakeoverScanResult result = scan(List.of(), List.of(dorisDefinition), registered, List.of(cr));

        assertThat(result.missing()).isEmpty();
        assertThat(result.matched().get(0).registered()).isTrue();
    }

    @Test
    @DisplayName("CR 扫描不完整（有 CRD 失败）时，已登记的 CR 实例不被误报进 missing")
    void doesNotReportCrInstanceMissingWhenCrScanIncomplete() {
        List<K8sServiceInstanceVO> registered =
                List.of(imported(201, "doris", "doris-disaggregated-cluster", "doris", "CR"));

        // 模拟本次 CR 扫描一个 CR 都没扫到（CRD 扫描失败），但带上失败标记
        K8sTakeoverScanResult result = scan(List.of(), List.of(), registered, List.of(),
                List.of("dorisdisaggregatedclusters.disaggregated.cluster.doris.com"));

        assertThat(result.missing()).isEmpty();
        assertThat(result.failedCrds()).containsExactly("dorisdisaggregatedclusters.disaggregated.cluster.doris.com");
    }

    @Test
    @DisplayName("CR 扫描不完整时，Helm 类目的 missing 判定不受影响，照常判定")
    void helmMissingStillDetectedWhenCrScanIncomplete() {
        List<FrameK8sServiceEntity> definitions = List.of(definition(2, "zookeeper", "MIDDLEWARE", null));
        List<K8sServiceInstanceVO> registered = List.of(imported(101, "prod", "zookeeper", "zookeeper"));

        K8sTakeoverScanResult result = scan(List.of(), definitions, registered, List.of(),
                List.of("nacos.nacos.io"));

        assertThat(result.missing()).hasSize(1);
        assertThat(result.missing().get(0).instanceId()).isEqualTo(101);
    }

    @Test
    @DisplayName("CR 与 Helm release 同 namespace+name 时仍作为两个部署单元返回")
    void distinguishesCrFromHelmOnNameCollision() {
        // 极端边界：operator 自身恰好用 helm 装且与 CR 实例撞了 namespace/name
        List<HelmReleaseListItemVO> releases = List.of(release("doris", "doris", "doris-something-1.0.0"));
        FrameK8sServiceEntity dorisHelmDefinition = definition(4, "doris-something", "ENVIRONMENT", null);
        FrameK8sServiceEntity dorisCrDefinition = definition(5, "doris", "MIDDLEWARE", null);
        K8sCrScanner.ScannedCr cr = new K8sCrScanner.ScannedCr(dorisCrDefinition, "doris", "doris", "Doris");

        K8sTakeoverScanResult result = scan(releases, List.of(dorisHelmDefinition, dorisCrDefinition),
                List.of(), List.of(cr));

        assertThat(result.matched()).hasSize(2);
        assertThat(result.matched()).extracting(K8sTakeoverScanResult.ScannedRelease::sourceKind)
                .containsExactly(InstanceSourceKind.HELM, InstanceSourceKind.CR);
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions) {
        return scan(releases, definitions, List.of());
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions,
                                       List<K8sServiceInstanceVO> registered) {
        return scan(releases, definitions, registered, List.of());
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions,
                                       List<K8sServiceInstanceVO> registered,
                                       List<K8sCrScanner.ScannedCr> crs) {
        return scan(releases, definitions, registered, crs, List.of());
    }

    private K8sTakeoverScanResult scan(List<HelmReleaseListItemVO> releases,
                                       List<FrameK8sServiceEntity> definitions,
                                       List<K8sServiceInstanceVO> registered,
                                       List<K8sCrScanner.ScannedCr> crs,
                                       List<String> failedCrds) {
        HelmReleaseReader reader = mock(HelmReleaseReader.class);
        K8sCrScanner crScanner = mock(K8sCrScanner.class);
        FrameK8sServiceService frameService = mock(FrameK8sServiceService.class);
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        K8sTakeoverReconcileService reconcileService = mock(K8sTakeoverReconcileService.class);
        when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());
        when(frameService.listNewest(7)).thenReturn(definitions);
        when(reader.listDeployed(any())).thenReturn(releases);
        when(crScanner.scan(any(), any())).thenReturn(new K8sCrScanner.CrScanResult(crs, failedCrds));
        when(instanceService.queryInstanceList(7)).thenReturn(registered);

        return new K8sTakeoverScanService(
                reader, crScanner, frameService, configService, instanceService, reconcileService).scan(7);
    }

    /** 构造一条已登记的接管实例。 */
    private static K8sServiceInstanceVO imported(int id, String namespace, String releaseName,
                                                 String serviceName) {
        return imported(id, namespace, releaseName, serviceName, InstanceSourceKind.HELM);
    }

    private static K8sServiceInstanceVO imported(int id, String namespace, String releaseName,
                                                 String serviceName, String sourceKind) {
        return imported(id, namespace, releaseName, serviceName, InstanceSourceKind.valueOf(sourceKind));
    }

    private static K8sServiceInstanceVO imported(int id, String namespace, String releaseName,
                                                 String serviceName, InstanceSourceKind sourceKind) {
        K8sServiceInstanceVO instance = new K8sServiceInstanceVO();
        instance.setId(id);
        instance.setNamespace(namespace);
        instance.setReleaseName(releaseName);
        instance.setServiceName(serviceName);
        instance.setSource(InstanceSource.IMPORTED);
        instance.setSourceKind(sourceKind);
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
