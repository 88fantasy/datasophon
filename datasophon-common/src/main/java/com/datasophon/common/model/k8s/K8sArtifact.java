package com.datasophon.common.model.k8s;

import com.alibaba.fastjson2.JSONObject;

import lombok.Data;

/**
 * @author zhanghuangbin
 *
 * <p>K8s 组件的部署形态由 {@link #kind} 显式三分类：
 * <ul>
 *   <li>{@code yaml}：纯 K8s 资源清单，apply 后与 operator 无关的一次性资源（如 operator 本体自身的
 *       Deployment）</li>
 *   <li>{@code helm}：Helm release</li>
 *   <li>{@code operator}：组件的稳态运行身份是被 operator reconcile 的自定义资源（CR），配套
 *       {@link #operator} 描述其 GVK 与看板/角色探测信息</li>
 * </ul>
 * {@code kind} 缺省时按"有 {@link #helm} 填 helm，否则有 {@link #yaml} 填 yaml"向后推断
 * （见 {@link #effectiveKind()}），存量 manifest 不需要整体回填。
 */
@Data
public class K8sArtifact {

    public static final String KIND_YAML = "yaml";
    public static final String KIND_HELM = "helm";
    public static final String KIND_OPERATOR = "operator";

    private String kind;

    private String helm;

    private String yaml;

    private K8sOperatorArtifact operator;

    /** 缺省 {@link #kind} 时按现有 helm/yaml 字段向后推断，不强制要求存量 manifest 显式声明。 */
    public String effectiveKind() {
        if (kind != null && !kind.isBlank()) {
            return kind;
        }
        if (helm != null && !helm.isBlank()) {
            return KIND_HELM;
        }
        return KIND_YAML;
    }

    /**
     * 从框架服务的 artifact JSON 中取 operator 描述；非 operator 或空值返回 {@code null}。
     */
    public static K8sOperatorArtifact operatorOf(String artifactJson) {
        if (artifactJson == null || artifactJson.isBlank()) {
            return null;
        }
        K8sArtifact artifact = JSONObject.parseObject(artifactJson, K8sArtifact.class);
        if (artifact == null || !KIND_OPERATOR.equals(artifact.effectiveKind())) {
            return null;
        }
        return artifact.getOperator();
    }
}
