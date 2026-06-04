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

package com.datasophon.common;

/**
 * K8s Agent Auth constants
 */
public final class K8sAgentAuthConstants {
    
    private K8sAgentAuthConstants() {
        throw new UnsupportedOperationException("Construct K8sAgentAuthConstants");
    }
    
    // Property keys (read from common.properties via PropertyUtils)
    public static final String AGENT_NODE_PORT = "k8s.agent.nodePort";
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
