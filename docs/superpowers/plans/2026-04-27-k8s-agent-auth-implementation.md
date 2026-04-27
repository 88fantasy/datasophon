# K8s Agent 鉴权模式实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 datasophon-k8s-agent 引入基于 RSA-SHA256 签名的 HTTP 请求鉴权机制，支持配置开关和时间戳防重放。

**Architecture:** 通过 Spring HandlerInterceptor 拦截请求，从 common.properties 读取公钥和配置，验证 HTTP header 中的时间戳、随机数及签名。健康检查端点排除鉴权。

**Tech Stack:** Java 8, Spring Boot 2.6.1, java.security (RSA-SHA256), PropertyUtils

---

### Task 1: 添加配置项到 common.properties

**Files:**
- Modify: `conf/common.properties` (末尾添加)

- [ ] **Step 1: 添加鉴权配置项**

在 `conf/common.properties` 末尾添加：

```properties
# k8s agent auth
k8s.agent.auth.enabled=false
k8s.agent.auth.public.key=
k8s.agent.auth.replay.window.seconds=300
```

### Task 2: 创建 K8sAgentAuthConstants（common 模块）

**Files:**
- Create: `datasophon-common/src/main/java/com/datasophon/common/utils/K8sAgentAuthConstants.java`

- [ ] **Step 1: 创建常量类**

```java
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.common.utils;

/**
 * K8s Agent Auth constants
 */
public final class K8sAgentAuthConstants {

    private K8sAgentAuthConstants() {
        throw new UnsupportedOperationException("Construct K8sAgentAuthConstants");
    }

    // Property keys (read from common.properties via PropertyUtils)
    public static final String AUTH_ENABLED = "k8s.agent.auth.enabled";
    public static final String AUTH_PUBLIC_KEY = "k8s.agent.auth.public.key";
    public static final String AUTH_REPLAY_WINDOW = "k8s.agent.auth.replay.window.seconds";

    // HTTP headers
    public static final String HEADER_TIMESTAMP = "x-vos-timestamp";
    public static final String HEADER_NONCE = "x-vos-nonce";
    public static final String HEADER_SIGNATURE = "x-vos-signature";

    // Defaults
    public static final int DEFAULT_REPLAY_WINDOW_SECONDS = 300;

    // Error messages
    public static final String ERR_MISSING_HEADERS = "Missing required auth headers";
    public static final String ERR_INVALID_TIMESTAMP = "Invalid timestamp format";
    public static final String ERR_TIMESTAMP_EXPIRED = "Request timestamp expired";
    public static final String ERR_SIGNATURE_FAILED = "Signature verification failed";
}
```

### Task 3: 创建 SignatureVerifier

**Files:**
- Create: `datasophon-k8s-agent/src/main/java/com/datasophon/k8sagent/auth/SignatureVerifier.java`
- Create: `datasophon-k8s-agent/src/test/java/com/datasophon/k8sagent/auth/SignatureVerifierTest.java`

- [ ] **Step 1: 创建签名验证类**

```java
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.k8sagent.auth;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA signature verifier (stateless utility)
 */
public class SignatureVerifier {

    private static final String ALGORITHM = "SHA256withRSA";
    private static final String KEY_FACTORY = "RSA";

    private final PublicKey publicKey;

    public SignatureVerifier(String publicKeyPem) {
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    /**
     * Verify signature for timestamp+nonce
     *
     * @param timestamp     timestamp string
     * @param nonce         nonce string
     * @param signatureBase64 base64-encoded signature
     * @return true if signature is valid
     */
    public boolean verify(String timestamp, String nonce, String signatureBase64) {
        try {
            String dataToSign = timestamp + nonce;
            Signature sig = Signature.getInstance(ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(dataToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
            return sig.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] derBytes = Base64.getDecoder().decode(cleaned);
            KeyFactory kf = KeyFactory.getInstance(KEY_FACTORY);
            return kf.generatePublic(new X509EncodedKeySpec(derBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse RSA public key", e);
        }
    }
}
```

- [ ] **Step 2: 创建单元测试**

```java
package com.datasophon.k8sagent.auth;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SignatureVerifierTest {

    private static KeyPair keyPair;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            keyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getPublicKeyPem() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private String sign(String timestamp, String nonce) throws Exception {
        String data = timestamp + nonce;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    @Test
    void testValidSignature() throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(getPublicKeyPem());
        String sig = sign("1714204800000", "abc123");
        assertTrue(verifier.verify("1714204800000", "abc123", sig));
    }

    @Test
    void testInvalidSignature() throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(getPublicKeyPem());
        assertFalse(verifier.verify("1714204800000", "abc123", "invalidBase64Signature=="));
    }

    @Test
    void testTamperedNonce() throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(getPublicKeyPem());
        String sig = sign("1714204800000", "abc123");
        assertFalse(verifier.verify("1714204800000", "tampered", sig));
    }

    @Test
    void testTamperedTimestamp() throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(getPublicKeyPem());
        String sig = sign("1714204800000", "abc123");
        assertFalse(verifier.verify("1714204800001", "abc123", sig));
    }

    @Test
    void testInvalidPublicKey() {
        assertThrows(IllegalArgumentException.class, () ->
                new SignatureVerifier("not-a-valid-key"));
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl datasophon-k8s-agent -Dtest=SignatureVerifierTest -DskipTests=false
```

Expected: All 5 tests PASS

### Task 4: 创建 K8sAgentAuthInterceptor

**Files:**
- Create: `datasophon-k8s-agent/src/main/java/com/datasophon/k8sagent/auth/K8sAgentAuthInterceptor.java`
- Create: `datasophon-k8s-agent/src/test/java/com/datasophon/k8sagent/auth/K8sAgentAuthInterceptorTest.java`

- [ ] **Step 1: 创建鉴权拦截器**

```java
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.k8sagent.auth;

import com.datasophon.common.utils.K8sAgentAuthConstants;
import com.datasophon.common.utils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * K8s Agent authentication interceptor
 */
@Component
public class K8sAgentAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(K8sAgentAuthInterceptor.class);

    private final boolean authEnabled;
    private final SignatureVerifier signatureVerifier;
    private final long replayWindowMillis;

    public K8sAgentAuthInterceptor() {
        this.authEnabled = PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED, false);

        int replayWindowSeconds = PropertyUtils.getInt(
                K8sAgentAuthConstants.AUTH_REPLAY_WINDOW,
                K8sAgentAuthConstants.DEFAULT_REPLAY_WINDOW_SECONDS);
        this.replayWindowMillis = replayWindowSeconds * 1000L;

        if (authEnabled) {
            String publicKeyPem = PropertyUtils.getString(K8sAgentAuthConstants.AUTH_PUBLIC_KEY);
            if (StringUtils.isBlank(publicKeyPem)) {
                throw new IllegalStateException(
                        "k8s.agent.auth.enabled=true but k8s.agent.auth.public.key is not configured");
            }
            this.signatureVerifier = new SignatureVerifier(publicKeyPem);
        } else {
            this.signatureVerifier = null;
        }

        logger.info("K8s Agent Auth enabled: {}", authEnabled);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!authEnabled) {
            return true;
        }

        String timestampStr = request.getHeader(K8sAgentAuthConstants.HEADER_TIMESTAMP);
        String nonce = request.getHeader(K8sAgentAuthConstants.HEADER_NONCE);
        String signature = request.getHeader(K8sAgentAuthConstants.HEADER_SIGNATURE);

        // Check all three headers are present
        if (StringUtils.isAnyBlank(timestampStr, nonce, signature)) {
            setAuthFailure(response, K8sAgentAuthConstants.ERR_MISSING_HEADERS);
            return false;
        }

        // Validate timestamp format
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            setAuthFailure(response, K8sAgentAuthConstants.ERR_INVALID_TIMESTAMP);
            return false;
        }

        // Check timestamp is within replay window
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > replayWindowMillis) {
            setAuthFailure(response, K8sAgentAuthConstants.ERR_TIMESTAMP_EXPIRED);
            return false;
        }

        // Verify signature
        if (!signatureVerifier.verify(timestampStr, nonce, signature)) {
            setAuthFailure(response, K8sAgentAuthConstants.ERR_SIGNATURE_FAILED);
            return false;
        }

        logger.debug("Auth passed for request: {} from {}", request.getRequestURI(), request.getRemoteAddr());
        return true;
    }

    private void setAuthFailure(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        try {
            String body = String.format("{\"error\":\"%s\"}", message);
            response.getWriter().write(body);
        } catch (Exception e) {
            logger.error("Failed to write auth failure response", e);
        }
    }
}
```

- [ ] **Step 2: 创建单元测试**

```java
package com.datasophon.k8sagent.auth;

import com.datasophon.common.utils.K8sAgentAuthConstants;
import com.datasophon.common.utils.PropertyUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class K8sAgentAuthInterceptorTest {

    private static KeyPair keyPair;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            keyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getPublicKeyPem() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private String sign(String timestamp, String nonce) throws Exception {
        String data = timestamp + nonce;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    @Test
    void testAuthDisabled() {
        try (MockedStatic<PropertyUtils> mocked = mockStatic(PropertyUtils.class)) {
            mocked.when(() -> PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED, false))
                    .thenReturn(false);
            K8sAgentAuthInterceptor interceptor = new K8sAgentAuthInterceptor();

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }
    }

    @Test
    void testMissingHeaders() {
        try (MockedStatic<PropertyUtils> mocked = mockStatic(PropertyUtils.class)) {
            mocked.when(() -> PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED, false))
                    .thenReturn(true);
            mocked.when(() -> PropertyUtils.getString(K8sAgentAuthConstants.AUTH_PUBLIC_KEY))
                    .thenReturn(getPublicKeyPem());
            mocked.when(() -> PropertyUtils.getInt(K8sAgentAuthConstants.AUTH_REPLAY_WINDOW, 300))
                    .thenReturn(300);

            K8sAgentAuthInterceptor interceptor = new K8sAgentAuthInterceptor();

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void testExpiredTimestamp() {
        try (MockedStatic<PropertyUtils> mocked = mockStatic(PropertyUtils.class)) {
            mocked.when(() -> PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED, false))
                    .thenReturn(true);
            mocked.when(() -> PropertyUtils.getString(K8sAgentAuthConstants.AUTH_PUBLIC_KEY))
                    .thenReturn(getPublicKeyPem());
            mocked.when(() -> PropertyUtils.getInt(K8sAgentAuthConstants.AUTH_REPLAY_WINDOW, 300))
                    .thenReturn(300);

            K8sAgentAuthInterceptor interceptor = new K8sAgentAuthInterceptor();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(K8sAgentAuthConstants.HEADER_TIMESTAMP, "1000000000000");
            request.addHeader(K8sAgentAuthConstants.HEADER_NONCE, "test-nonce");
            request.addHeader(K8sAgentAuthConstants.HEADER_SIGNATURE, "some-signature");

            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void testValidRequest() throws Exception {
        try (MockedStatic<PropertyUtils> mocked = mockStatic(PropertyUtils.class)) {
            mocked.when(() -> PropertyUtils.getBoolean(K8sAgentAuthConstants.AUTH_ENABLED, false))
                    .thenReturn(true);
            mocked.when(() -> PropertyUtils.getString(K8sAgentAuthConstants.AUTH_PUBLIC_KEY))
                    .thenReturn(getPublicKeyPem());
            mocked.when(() -> PropertyUtils.getInt(K8sAgentAuthConstants.AUTH_REPLAY_WINDOW, 300))
                    .thenReturn(300);

            K8sAgentAuthInterceptor interceptor = new K8sAgentAuthInterceptor();

            long now = System.currentTimeMillis();
            String sig = sign(String.valueOf(now), "test-nonce");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(K8sAgentAuthConstants.HEADER_TIMESTAMP, String.valueOf(now));
            request.addHeader(K8sAgentAuthConstants.HEADER_NONCE, "test-nonce");
            request.addHeader(K8sAgentAuthConstants.HEADER_SIGNATURE, sig);

            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request, response, new Object()));
        }
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl datasophon-k8s-agent -Dtest=K8sAgentAuthInterceptorTest -DskipTests=false
```

Expected: All 4 tests PASS

### Task 5: 创建 K8sAgentWebConfiguration

**Files:**
- Create: `datasophon-k8s-agent/src/main/java/com/datasophon/k8sagent/configuration/K8sAgentWebConfiguration.java`

- [ ] **Step 1: 创建 Web 配置类**

```java
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.k8sagent.config;

import com.datasophon.k8sagent.auth.K8sAgentAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * K8s Agent web configuration
 */
@Configuration
public class K8sAgentWebConfiguration implements WebMvcConfigurer {

    private final K8sAgentAuthInterceptor authInterceptor;

    public K8sAgentWebConfiguration(K8sAgentAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/health", "/api/v1/ready", "/error");
    }
}
```

### Task 6: 运行全部测试并提交

- [ ] **Step 1: 运行 k8s-agent 模块全部测试**

```bash
mvn test -pl datasophon-k8s-agent -DskipTests=false
```

Expected: All tests PASS (including existing DatasophonK8SAgentApplicationTest)

- [ ] **Step 2: 格式化代码**

```bash
mvn spotless:apply -pl datasophon-k8s-agent,datasophon-common
```

- [ ] **Step 3: 编译确认**

```bash
mvn clean package -pl datasophon-k8s-agent -am -DskipTests
```

Expected: BUILD SUCCESS

---

## 验证方式

1. **单元测试**: 运行 `mvn test -pl datasophon-k8s-agent` 确认所有测试通过
2. **手动测试**:
   - 设置 `k8s.agent.auth.enabled=true` 并配置有效的 RSA 公钥
   - 启动 k8s-agent，不带鉴权 header 请求 `/api/v1/health` 应返回 200（健康检查已排除）
   - 不带鉴权 header 请求其他 `/api/v1/*` 接口应返回 401
   - 携带正确鉴权 header 请求应返回 200
