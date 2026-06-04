package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.config.ClientOptions;

import org.junit.jupiter.api.Test;

/**
 * @author zhanghuangbin
 */
public class KubectlClientNoMockTest {
    
    @Test
    public void test() {
        ClientOptions options = new ClientOptions();
        options.setKubeConfig("D:\\tmp\\datasophon\\sensitive\\trKdB30ekErI\\kubeConfig.yaml");
        try (KubectlClient client = new KubectlClient(options)) {
            client.attachSecretToServiceAccount("vos", "default", "nexus-registry-secret");
        }
    }
}
