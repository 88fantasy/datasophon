package com.datasophon.common.k8s.client;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.utils.CertificateUtils;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * @author zhanghuangbin
 */
public class K8sClientFactory {


    public static KubernetesClient newClient(ClientOptions options) {
        return new KubernetesClientBuilder().withConfig(newConfig(options)).build();
    }


    public static Config newConfig(ClientOptions options) {
        Config config = null;
        if (StrUtil.isNotBlank(options.getKubeConfig())) {
            try {
                config = Config.fromKubeconfig(options.getKubeConfig());
            } catch (Exception e) {
                throw new IllegalArgumentException(String.format("参数错误，解析kubeConfig失败，%s", e.getMessage()), e);
            }
        } else {
            ConfigBuilder builder = new ConfigBuilder()
                    .withMasterUrl(options.getServerName());
            if (StrUtil.isBlank(options.getServerCert())) {
                builder = builder.withTrustCerts(true);
            } else {
                boolean selfSigned = false;
                try {
                    byte[] decoded = Base64.getDecoder().decode(options.getServerCert().trim());
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    selfSigned = CertificateUtils.isSelfSigned((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded)));
                } catch (CertificateException e) {
                    throw new IllegalStateException("服务器证书不是合法的pem证书，请检查", e);
                }
                if (selfSigned) {
                    builder = builder.withTrustCerts(true);
                } else {
                    builder = builder.withCaCertData(options.getServerCert());
                }
            }
            if (StrUtil.isNotBlank(options.getToken())) {
                builder = builder.withOauthToken(options.getToken());
            } else {
                builder = builder.withUsername(options.getUsername())
                        .withPassword(options.getPassword());
            }
            config = builder.build();
        }
        if (options.isFastFail()) {
            config.setConnectionTimeout(1500);
            config.setRequestRetryBackoffLimit(1);
            config.setRequestTimeout(2 * 1000);
        }
        return config;
    }



}
