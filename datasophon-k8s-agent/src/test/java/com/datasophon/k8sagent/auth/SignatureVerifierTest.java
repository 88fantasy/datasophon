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

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


    @Test
    public void test() {
        System.out.println("----public-------");
        System.out.println(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        System.out.println("----private-------");
        System.out.println(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
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
