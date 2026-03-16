package com.datasophon.common.k8s.config;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ClientOptions {

    private String kubeConfig;

    private String serverName;

    private String serverCert;

    private String token;

    private String username;

    private String password;

    private boolean fastFail;

    private String namespace;


}
