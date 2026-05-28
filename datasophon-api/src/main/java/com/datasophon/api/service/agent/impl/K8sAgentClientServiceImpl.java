/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package com.datasophon.api.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.datasophon.api.service.agent.K8sAgentClientService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.common.K8sAgentAuthConstants;
import com.datasophon.common.utils.JacksonUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author zhanghuangbin
 */
@Slf4j
@Service("k8sAgentClientService")
public class K8sAgentClientServiceImpl implements K8sAgentClientService {

    private static final int DEFAULT_K8S_AGENT_PORT = 32552;
    private static final String AGENT_SERVLET_PATH = "/api";

    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;

    @Override
    public <T> T call(Integer clusterId, String url, Object payload, Class<T> responseType) {
        // 1. 获取 K8sClusterConfig 得到 serverHost
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(clusterId);
        String serverHost = config.getServerHost();
        if (serverHost == null || serverHost.isEmpty()) {
            throw new IllegalStateException("K8s cluster config serverHost is not configured for clusterId: " + clusterId);
        }

        // 2. 从 common.properties 获取 agent 端口
        int agentPort = PropertyUtils.getInt(K8sAgentAuthConstants.AGENT_NODE_PORT, DEFAULT_K8S_AGENT_PORT);

        // 3. 构建完整 URL
        String fullUrl = "http://" + serverHost + ":" + agentPort + AGENT_SERVLET_PATH + url;

        Map<String, String> headers = new HashMap<>();
        // 4. 如果开启鉴权，生成签名并添加请求头
        boolean authEnabled = PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED);
        if (authEnabled) {
            buildAuthHeaders(headers);
        }

        return doCall(fullUrl, payload, responseType, headers, true);
    }

    private <T> T doCall(String url, Object payload, Class<T> responseType, Map<String, String> headers, boolean longReq) {
        RequestConfig config;
        if (longReq) {
            config = RequestConfig.custom()
                    .setConnectTimeout(5000)
                    .setSocketTimeout(300_000)
                    .build();
        } else {
            config = RequestConfig.custom()
                    .setConnectTimeout(5000)
                    .setSocketTimeout(30_000)
                    .build();
        }

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");

            // 添加鉴权请求头
            for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                post.setHeader(entry.getKey(), entry.getValue());
            }

            // 序列化 payload
            ObjectMapper mapper = JacksonUtils.getInstance();
            String reqBody = null;
            if (payload == null) {
                reqBody = "{}";
            } else if (payload instanceof String) {
                reqBody = StrUtil.isBlank(payload.toString()) ? "{}" : payload.toString();
            } else {
                reqBody = mapper.writeValueAsString(payload);
            }
            post.setEntity(new StringEntity(reqBody, ContentType.APPLICATION_JSON));
            log.info("Calling K8s agent: {}", url);

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    throw new RuntimeException("K8s agent call failed, status: " + statusCode + ", body: " + body);
                }


                TypeFactory ft = TypeFactory.defaultInstance();
                JavaType type = ft.constructGeneralizedType(ft.constructType(Result.class), responseType);

                Result<T> result = mapper.readValue(body, type);
                if (result.isSuccess()) {
                    return result.getData();
                }
                throw new IllegalStateException("K8s agent call failed, status: " + statusCode + ", message: " + result.getMsg());
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to call K8s agent: " + url, e);
        }
    }

    private void buildAuthHeaders(Map<String, String> headers) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String privateKeyPem = PropertyUtils.getString(K8sAgentAuthConstants.AUTH_PRIVATE_KEY);
        String signature = signWithPrivateKey(timestamp + nonce, privateKeyPem);

        headers.put(K8sAgentAuthConstants.HEADER_TIMESTAMP, timestamp);
        headers.put(K8sAgentAuthConstants.HEADER_NONCE, nonce);
        headers.put(K8sAgentAuthConstants.HEADER_SIGNATURE, signature);
    }

    private String signWithPrivateKey(String data, String privateKeyPem) {
        try {
            PrivateKey privateKey = parsePrivateKey(privateKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request data", e);
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] derBytes = Base64.getDecoder().decode(cleaned);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(new PKCS8EncodedKeySpec(derBytes));
    }
}
