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

import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.K8sAgentAuthConstants;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.Result;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

        logger.info("Auth passed for request: {} from {}", request.getRequestURI(), request.getRemoteAddr());
        return true;
    }

    private void setAuthFailure(HttpServletResponse response, String message) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        try {
            response.getWriter().write(JSONObject.toJSONString(Result.error(HttpStatus.UNAUTHORIZED.value(), message)));
        } catch (Exception e) {
            logger.error("Failed to write auth failure response", e);
        }
    }
}
