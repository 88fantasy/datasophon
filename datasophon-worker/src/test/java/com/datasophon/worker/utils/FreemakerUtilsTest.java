package com.datasophon.worker.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FreemakerUtilsTest {

    @Test
    void redactsSecretsInPropertiesAndYaml() {
        String rendered = "secret=top-secret\nfs.s3a.secret.key: top-secret\n"
                + "fs.s3a.access.key: top-access\nAWS_ACCESS_KEY_ID=top-access\n  auth_token: top-token\nkept: visible";

        assertEquals("secret=<redacted>\nfs.s3a.secret.key: <redacted>\n"
                + "fs.s3a.access.key: <redacted>\nAWS_ACCESS_KEY_ID=<redacted>\n  auth_token: <redacted>\nkept: visible",
                FreemakerUtils.redactSecrets(rendered));
    }
}
