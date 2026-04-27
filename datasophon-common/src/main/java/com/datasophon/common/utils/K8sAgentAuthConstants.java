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
    public static final String AUTH_PRIVATE_KEY = "k8s.agent.auth.private.key";
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
