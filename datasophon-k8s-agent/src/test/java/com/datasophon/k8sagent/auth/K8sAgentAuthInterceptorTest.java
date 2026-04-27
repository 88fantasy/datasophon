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

import com.datasophon.common.K8sAgentAuthConstants;
import com.datasophon.common.utils.PropertyUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class K8sAgentAuthInterceptorTest {

    static {
        System.setProperty("commonPropertiesLocation",
                K8sAgentAuthInterceptorTest.class.getClassLoader().getResource("common.properties").getPath());
    }

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
    void testAuthDisabled() throws Exception {
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
    void testMissingHeaders() throws Exception {
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
    void testExpiredTimestamp() throws Exception {
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
