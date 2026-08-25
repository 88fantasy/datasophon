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

package com.datasophon.api.service.ddl.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datasophon.common.model.ServiceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DdlMetaServiceImplTest {

    @Test
    void refreshesDsManagedCredentialsWhenMetadataIsReloaded() {
        List<ServiceConfig> saved = new ArrayList<>(List.of(
                config("aws.s3.access.key.id", "stale-access"),
                config("aws.s3.access.key.secret", "stale-secret"),
                config("aws.s3.bucket.name", "custom-bucket")));
        List<ServiceConfig> metadata = List.of(
                config("aws.s3.access.key.id", "current-access"),
                config("aws.s3.access.key.secret", "current-secret"),
                config("aws.s3.bucket.name", "metadata-bucket"));

        DdlMetaServiceImpl.refreshPlatformManagedConfigValues("DS", saved, metadata, Map.of());

        assertEquals("current-access", value(saved, "aws.s3.access.key.id"));
        assertEquals("current-secret", value(saved, "aws.s3.access.key.secret"));
        assertEquals("custom-bucket", value(saved, "aws.s3.bucket.name"));
    }

    @Test
    void doesNotRewriteCredentialsForOtherServices() {
        List<ServiceConfig> saved = new ArrayList<>(List.of(config("aws.s3.access.key.id", "service-owned")));

        DdlMetaServiceImpl.refreshPlatformManagedConfigValues(
                "OTHER", saved, List.of(config("aws.s3.access.key.id", "platform-owned")), Map.of());

        assertEquals("service-owned", value(saved, "aws.s3.access.key.id"));
    }

    @Test
    void resolvesManagedCredentialDefaultsFromCurrentRootVariables() {
        List<ServiceConfig> saved = new ArrayList<>(List.of(config("aws.s3.access.key.secret", "stale")));
        ServiceConfig metadata = config("aws.s3.access.key.secret", null);
        metadata.setDefaultValue("${ROOT.Rustfs.secret_key}");

        DdlMetaServiceImpl.refreshPlatformManagedConfigValues(
                "DS", saved, List.of(metadata), Map.of("${ROOT.Rustfs.secret_key}", "current-secret"));

        assertEquals("current-secret", value(saved, "aws.s3.access.key.secret"));
    }

    private static ServiceConfig config(String name, String value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }

    private static Object value(List<ServiceConfig> configs, String name) {
        return configs.stream()
                .filter(config -> name.equals(config.getName()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
