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
 * RSA signature verifier (immutable utility)
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
