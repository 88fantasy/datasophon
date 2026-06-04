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
