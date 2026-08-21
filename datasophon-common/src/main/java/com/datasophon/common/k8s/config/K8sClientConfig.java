package com.datasophon.common.k8s.config;

/**
 * K8s 客户端建立连接所需的最小配置视图。
 *
 * <p>由上层持久化模型实现，避免公共客户端模块依赖具体的 DAO 实体。
 */
public interface K8sClientConfig {

    String getKubeConfig();

    String getServerHost();

    String getServerCert();

    String getToken();

    String getUsername();

    String getPassword();

    String getAuthType();
}
