package com.datasophon.api.service.k8s;

import com.datasophon.common.k8s.vo.k8s.K8sResource;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

/**
 * 只读枚举「operator CR」类型框架服务的 CR 实例，供接管扫描把它们当 pseudo-release 登记
 * （见 {@link K8sArtifact#KIND_OPERATOR} / {@link K8sOperatorArtifact}）。
 *
 * <p>本类不做写操作，也不识别 operator controller 本体（如 doris-operator Deployment）——
 * CR 能被扫到即隐含 operator 健康，本次范围只做 CR 扫描 + 监控看板。
 */
@Service
public class K8sCrScanner {

    private static final Logger log = LoggerFactory.getLogger(K8sCrScanner.class);

    private final K8sService k8sService;

    public K8sCrScanner(K8sService k8sService) {
        this.k8sService = k8sService;
    }

    /** 扫描到的单个 CR 实例。 */
    public record ScannedCr(FrameK8sServiceEntity definition, String name, String namespace, String kind) {
    }

    /**
     * 扫描全部 {@code kind=operator} 框架服务定义对应的 CR 实例。
     *
     * <p>多个框架服务定义指向同一 CRD（同 group+plural）时只发一次 kubectl 请求；单个 CRD 扫描
     * 失败（如 CRD 未安装，属常态，不是每个接管集群都装了每种 operator）只记 warn 日志跳过，
     * 不阻断其他 CRD 的扫描。
     */
    public List<ScannedCr> scan(K8sClusterConfig config, List<FrameK8sServiceEntity> definitions) {
        Map<String, FrameK8sServiceEntity> crdToDefinition = new LinkedHashMap<>();
        Map<String, K8sOperatorArtifact> crdToOperator = new LinkedHashMap<>();
        for (FrameK8sServiceEntity definition : definitions) {
            K8sOperatorArtifact operator = operatorOf(definition);
            if (operator == null || isBlank(operator.getGroup()) || isBlank(operator.getPlural())) {
                continue;
            }
            String key = crdKey(operator);
            crdToDefinition.put(key, definition);
            crdToOperator.put(key, operator);
        }

        List<ScannedCr> results = new ArrayList<>();
        for (Map.Entry<String, K8sOperatorArtifact> entry : crdToOperator.entrySet()) {
            K8sOperatorArtifact operator = entry.getValue();
            FrameK8sServiceEntity definition = crdToDefinition.get(entry.getKey());
            try {
                K8sResourceList<K8sResource> crs = k8sService.batchExec(config,
                        client -> client.getCustomResourcesAllNamespaces(operator.getPlural(), operator.getGroup()),
                        "扫描 CRD " + entry.getKey());
                if (crs == null || crs.getItems() == null) {
                    continue;
                }
                for (K8sResource cr : crs.getItems()) {
                    if (cr.getMetadata() == null || cr.getMetadata().getName() == null) {
                        continue;
                    }
                    results.add(new ScannedCr(definition, cr.getMetadata().getName(),
                            cr.getMetadata().getNamespace(), operator.getKind()));
                }
            } catch (Exception e) {
                log.warn("扫描 CRD {} 失败：{}", entry.getKey(), e.getMessage());
            }
        }
        return results;
    }

    private K8sOperatorArtifact operatorOf(FrameK8sServiceEntity definition) {
        if (definition.getArtifact() == null || definition.getArtifact().isBlank()) {
            return null;
        }
        K8sArtifact artifact = JSONObject.parseObject(definition.getArtifact(), K8sArtifact.class);
        if (artifact == null || !K8sArtifact.KIND_OPERATOR.equals(artifact.effectiveKind())) {
            return null;
        }
        return artifact.getOperator();
    }

    private static String crdKey(K8sOperatorArtifact operator) {
        return operator.getPlural() + "." + operator.getGroup();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
