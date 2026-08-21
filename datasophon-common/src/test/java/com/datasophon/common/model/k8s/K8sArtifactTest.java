package com.datasophon.common.model.k8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONObject;

/**
 * K8sArtifact / K8sOperatorArtifact 序列化往返与 kind 推断规则测试。
 */
class K8sArtifactTest {

    @Test
    void parseObject_helmOnly_effectiveKindIsHelm() {
        String json = "{\"helm\":\"apisix-2.12.5.tgz\"}";
        K8sArtifact artifact = JSONObject.parseObject(json, K8sArtifact.class);
        assertNull(artifact.getKind());
        assertEquals("apisix-2.12.5.tgz", artifact.getHelm());
        assertEquals(K8sArtifact.KIND_HELM, artifact.effectiveKind());
    }

    @Test
    void parseObject_yamlOnly_effectiveKindIsYaml() {
        String json = "{\"yaml\":\"easyflow.yaml\"}";
        K8sArtifact artifact = JSONObject.parseObject(json, K8sArtifact.class);
        assertNull(artifact.getKind());
        assertEquals("easyflow.yaml", artifact.getYaml());
        assertEquals(K8sArtifact.KIND_YAML, artifact.effectiveKind());
    }

    @Test
    void parseObject_operatorKindWithYamlPreserved_roundTrips() {
        // Doris 场景：kind=operator 且保留原有 yaml（新建 MANAGED 集群装 Doris 的 CR 清单，安装路径不变）
        String json = "{"
                + "\"yaml\":\"ddc-cluster.yaml\","
                + "\"kind\":\"operator\","
                + "\"operator\":{"
                + "  \"group\":\"disaggregated.cluster.doris.com\","
                + "  \"kind\":\"DorisDisaggregatedCluster\","
                + "  \"plural\":\"dorisdisaggregatedclusters\","
                + "  \"monitorProfile\":\"doris-disaggregated\","
                + "  \"roles\":[{\"name\":\"fe\",\"jobPattern\":\"-fe$\"},"
                + "             {\"name\":\"compute\",\"jobPattern\":\"-cg\\\\d+$\"}]"
                + "}}";
        K8sArtifact artifact = JSONObject.parseObject(json, K8sArtifact.class);

        assertEquals(K8sArtifact.KIND_OPERATOR, artifact.getKind());
        assertEquals(K8sArtifact.KIND_OPERATOR, artifact.effectiveKind());
        assertEquals("ddc-cluster.yaml", artifact.getYaml());

        K8sOperatorArtifact operator = artifact.getOperator();
        assertEquals("disaggregated.cluster.doris.com", operator.getGroup());
        assertEquals("DorisDisaggregatedCluster", operator.getKind());
        assertEquals("dorisdisaggregatedclusters", operator.getPlural());
        assertEquals("doris-disaggregated", operator.getMonitorProfile());

        List<K8sOperatorArtifact.Role> roles = operator.getRoles();
        assertEquals(2, roles.size());
        assertEquals("fe", roles.get(0).getName());
        assertEquals("-fe$", roles.get(0).getJobPattern());
        assertEquals("compute", roles.get(1).getName());
        assertEquals("-cg\\d+$", roles.get(1).getJobPattern());

        // 往返：序列化回 JSON 再解析一次，字段应保持一致
        String serialized = JSONObject.toJSONString(artifact);
        K8sArtifact roundTripped = JSONObject.parseObject(serialized, K8sArtifact.class);
        assertEquals(artifact.getKind(), roundTripped.getKind());
        assertEquals(artifact.getOperator().getPlural(), roundTripped.getOperator().getPlural());
        assertEquals(artifact.getOperator().getRoles().size(), roundTripped.getOperator().getRoles().size());
    }

    @Test
    void operatorOf_returnsOperatorOnlyForOperatorArtifacts() {
        String operatorJson = "{\"kind\":\"operator\",\"operator\":{\"group\":\"nacos.io\",\"plural\":\"nacos\"}}";

        assertEquals("nacos.io", K8sArtifact.operatorOf(operatorJson).getGroup());
        assertNull(K8sArtifact.operatorOf("{\"helm\":\"nacos.tgz\"}"));
        assertNull(K8sArtifact.operatorOf(null));
    }
}
