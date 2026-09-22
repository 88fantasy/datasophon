package com.datasophon.worker.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FreemakerUtilsTest {

    @Test
    void redactsSecretsInPropertiesAndYaml() {
        String rendered = "secret=top-secret\nfs.s3a.secret.key: top-secret\nkept: visible";

        assertEquals("secret=<redacted>\nfs.s3a.secret.key: <redacted>\nkept: visible",
                FreemakerUtils.redactSecrets(rendered));
    }
}
