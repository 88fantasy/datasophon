package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.common.k8s.vo.k8s.K8sResource;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * K8sCrScanner 单测：mock K8sService.batchExec，覆盖去重、失败隔离、非 operator 定义跳过三个场景。
 */
class K8sCrScannerTest {

    private static final K8sClusterConfig CONFIG = new K8sClusterConfig();

    @Test
    @DisplayName("kind=operator 的定义会触发 CR 扫描，产出 name/namespace/kind")
    void scansOperatorKindDefinition() {
        K8sService k8sService = mock(K8sService.class);
        when(k8sService.batchExec(any(), any(), any())).thenReturn(crList(cr("doris-disaggregated-cluster", "doris")));

        FrameK8sServiceEntity doris = definition("doris", dorisArtifact());
        K8sCrScanner.CrScanResult result = new K8sCrScanner(k8sService).scan(CONFIG, List.of(doris));

        assertThat(result.crs()).hasSize(1);
        assertThat(result.crs().get(0).name()).isEqualTo("doris-disaggregated-cluster");
        assertThat(result.crs().get(0).namespace()).isEqualTo("doris");
        assertThat(result.crs().get(0).kind()).isEqualTo("DorisDisaggregatedCluster");
        assertThat(result.crs().get(0).definition()).isSameAs(doris);
        assertThat(result.complete()).isTrue();
    }

    @Test
    @DisplayName("多个定义指向同一 CRD（同 group+plural）只发一次 kubectl 请求")
    void dedupesRequestsForSameCrd() {
        K8sService k8sService = mock(K8sService.class);
        when(k8sService.batchExec(any(), any(), any())).thenReturn(crList(cr("doris-disaggregated-cluster", "doris")));

        // 构造两个 artifact 完全相同（同 group+plural）的定义，模拟"同一 CRD 被多个框架服务引用"
        FrameK8sServiceEntity a = definition("doris", dorisArtifact());
        FrameK8sServiceEntity b = definition("doris-alias", dorisArtifact());

        K8sCrScanner.CrScanResult result = new K8sCrScanner(k8sService).scan(CONFIG, List.of(a, b));

        assertThat(result.crs()).hasSize(1);
        verify(k8sService, times(1)).batchExec(any(), any(), any());
    }

    @Test
    @DisplayName("单个 CRD 扫描失败（如 CRD 未安装）不阻断其他 CRD 的扫描")
    void isolatesFailureOfOneCrd() {
        K8sService k8sService = mock(K8sService.class);
        when(k8sService.batchExec(any(), any(), eq("扫描 CRD dorisdisaggregatedclusters.disaggregated.cluster.doris.com")))
                .thenThrow(new RuntimeException("CRD 未安装"));
        when(k8sService.batchExec(any(), any(), eq("扫描 CRD nacos.nacos.io")))
                .thenReturn(crList(cr("nacos", "prod")));

        FrameK8sServiceEntity doris = definition("doris", dorisArtifact());
        FrameK8sServiceEntity nacos = definition("nacos", nacosArtifact());

        K8sCrScanner.CrScanResult result = new K8sCrScanner(k8sService).scan(CONFIG, List.of(doris, nacos));

        assertThat(result.crs()).hasSize(1);
        assertThat(result.crs().get(0).name()).isEqualTo("nacos");
        assertThat(result.complete()).isFalse();
        assertThat(result.failedCrds()).containsExactly("dorisdisaggregatedclusters.disaggregated.cluster.doris.com");
    }

    @Test
    @DisplayName("kind 非 operator（helm/yaml）的定义不触发 CR 扫描")
    void skipsNonOperatorDefinitions() {
        K8sService k8sService = mock(K8sService.class);

        FrameK8sServiceEntity helmDef = definition("apisix", "{\"helm\":\"apisix-2.12.5.tgz\"}");
        FrameK8sServiceEntity yamlDef = definition("easyflow", "{\"yaml\":\"easyflow.yaml\"}");
        FrameK8sServiceEntity noArtifactDef = definition("bare", null);

        K8sCrScanner.CrScanResult result = new K8sCrScanner(k8sService)
                .scan(CONFIG, List.of(helmDef, yamlDef, noArtifactDef));

        assertThat(result.crs()).isEmpty();
        assertThat(result.complete()).isTrue();
        verify(k8sService, times(0)).batchExec(any(), any(), any());
    }

    private static String dorisArtifact() {
        return "{\"yaml\":\"ddc-cluster.yaml\",\"kind\":\"operator\",\"operator\":{"
                + "\"group\":\"disaggregated.cluster.doris.com\",\"version\":\"v1\","
                + "\"kind\":\"DorisDisaggregatedCluster\",\"plural\":\"dorisdisaggregatedclusters\","
                + "\"monitorProfile\":\"doris-disaggregated\","
                + "\"roles\":[{\"name\":\"fe\",\"jobPattern\":\"-fe$\"},{\"name\":\"compute\",\"jobPattern\":\"-cg\\\\d+$\"}]}}";
    }

    private static String nacosArtifact() {
        return "{\"kind\":\"operator\",\"operator\":{\"group\":\"nacos.io\",\"version\":\"v1alpha1\","
                + "\"kind\":\"Nacos\",\"plural\":\"nacos\"}}";
    }

    private static FrameK8sServiceEntity definition(String serviceName, String artifact) {
        FrameK8sServiceEntity definition = new FrameK8sServiceEntity();
        definition.setId(1);
        definition.setServiceName(serviceName);
        definition.setType("MIDDLEWARE");
        definition.setArtifact(artifact);
        return definition;
    }

    private static K8sResourceList<K8sResource> crList(K8sResource... items) {
        K8sResourceList<K8sResource> list = new K8sResourceList<>();
        list.setItems(List.of(items));
        return list;
    }

    private static K8sResource cr(String name, String namespace) {
        K8sResource resource = new K8sResource();
        K8sResource.Metadata metadata = new K8sResource.Metadata();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        resource.setMetadata(metadata);
        return resource;
    }
}
