package com.datasophon.dao.enums.k8s;

/**
 * K8s 服务实例的部署形态识别方式，与 {@link InstanceSource}（INSTALLED/IMPORTED，表达"平台安装 vs
 * 扫描接管"）正交：本枚举表达"该实例对应的是 Helm release 还是 operator 自定义资源（CR）"。
 *
 * <p>HELM：由 Helm release 匹配识别，{@code releaseName} 是 Helm release 名。
 * <p>CR：由 operator CR 扫描识别（见 {@link com.datasophon.common.model.k8s.K8sArtifact#KIND_OPERATOR}），
 * {@code releaseName} 复用存 CR 实例名。
 */
public enum InstanceSourceKind {

    HELM,
    CR
}
