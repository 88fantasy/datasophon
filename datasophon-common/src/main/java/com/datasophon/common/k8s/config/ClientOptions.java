package com.datasophon.common.k8s.config;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ClientOptions {

    public static ClientOptions from(K8sClientConfig config, boolean readOnly) {
        ClientOptions options = new ClientOptions();
        options.setKubeConfig(config.getKubeConfig());
        options.setServerName(config.getServerHost());
        options.setServerCert(config.getServerCert());
        options.setToken(config.getToken());
        options.setUsername(config.getUsername());
        options.setPassword(config.getPassword());
        options.setType(config.getAuthType());
        options.setReadOnly(readOnly);
        return options;
    }

    private String kubeConfig;

    private String serverName;

    private String serverCert;

    private String token;

    private String username;

    private String password;

    /**
     * 凭据类型，取值与 {@code com.datasophon.dao.enums.k8s.K8sAuthType} 的枚举名一致
     * （config_file/token/password）。本模块不依赖 datasophon-api，故用 String 承载；
     * 由 K8sClusterConfig 转换为 ClientOptions 时（如 BeanUtil.toBean）自动按枚举 name() 填充。
     * 缺省为空时，{@link com.datasophon.common.k8s.client.SecureKubeConfigWriter} 回落旧的
     * 按字段非空判断的优先级顺序。
     */
    private String type;

    /** 接管集群仅允许读取；客户端写命令必须拒绝执行。 */
    private boolean readOnly;

}
