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
