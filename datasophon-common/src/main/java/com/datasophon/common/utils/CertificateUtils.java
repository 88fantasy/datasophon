package com.datasophon.common.utils;

import java.security.cert.X509Certificate;

public class CertificateUtils {

    /**
     * 判断给定的 X.509 证书是否为自签名证书
     *
     * @param cert X509Certificate 对象
     * @return true 表示自签名，false 表示非自签名
     */
    public static boolean isSelfSigned(X509Certificate cert) {
        try {
            // 1. 快速检查：颁发者和主体 DN 是否相同
            if (!cert.getIssuerDN().equals(cert.getSubjectDN())) {
                return false; // 不同则肯定不是自签名
            }

            // 2. 严格验证：用证书的公钥验证证书自身的签名
            cert.verify(cert.getPublicKey());
            return true; // 验证成功，是自签名

        } catch (Exception e) {
            // 签名验证失败（抛出 SignatureException 或 InvalidKeyException 等）
            return false;
        }
    }




}