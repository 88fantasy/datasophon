package com.datasophon.common.model.k8s;

import java.util.List;

import lombok.Data;

/**
 * {@link K8sArtifact#getKind()} 为 {@link K8sArtifact#KIND_OPERATOR} 时的配套描述：组件的 GVK
 * （用于接管扫描按 CRD 只读枚举 CR 实例）+ 看板画像 + 角色探测规则（用于把该 CR 关联的 K8s Service
 * 按角色分类，进而分流看板查询的 job 过滤）。
 *
 * <p>只描述 CR 本身，不描述 operator controller 本体（如 doris-operator Deployment）——本次范围只做
 * CR 扫描 + 监控看板，operator 本体的纳管识别不在范围内。
 */
@Data
public class K8sOperatorArtifact {

    /** CRD 的 apiGroup，如 {@code disaggregated.cluster.doris.com}。 */
    private String group;

    /** CRD 的 apiVersion，如 {@code v1}。 */
    private String version;

    /** CR 的 K8s Kind，如 {@code DorisDisaggregatedCluster}（展示用，非扫描必需）。 */
    private String kind;

    /** CRD 的复数资源名，如 {@code dorisdisaggregatedclusters}（{@code kubectl get <plural>.<group>}）。 */
    private String plural;

    /** 前端看板模式判定信号，如 {@code doris-disaggregated}；为空时前端按存算一体（默认）处理。 */
    private String monitorProfile;

    /** 角色探测规则：把该 CR 关联的 Service/job 名按角色分类。 */
    private List<Role> roles;

    @Data
    public static class Role {

        /** 角色名，如 {@code fe}/{@code compute}。 */
        private String name;

        /** 作用于 Service/job 名的 Java 正则，如 {@code -fe$}、{@code -cg\d+$}。 */
        private String jobPattern;
    }
}
