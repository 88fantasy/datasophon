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

package com.datasophon.api.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.FrameServiceEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;

class ServiceInstallServiceImplTest {

    @Test
    void readsLegacyConfigFromServiceDdlParameters() {
        FrameServiceEntity frameService = new FrameServiceEntity();
        frameService.setServiceConfig("{{legacy-generator}:[{}]}");
        frameService.setServiceJson("{\"parameters\":[{\"name\":\"s3Endpoint\",\"value\":\"http://192.168.10.131:9040\"}]}");

        assertThat(ServiceInstallServiceImpl.resolveFrameServiceConfigs(frameService, Map.of()))
                .singleElement()
                .extracting(ServiceConfig::getName, ServiceConfig::getValue)
                .containsExactly("s3Endpoint", "http://192.168.10.131:9040");
    }

    @Test
    void mergesCurrentValuesIntoLoadedConfigFileTemplate() {
        Generators generator = new Generators();
        generator.setFilename("otelcol.yaml");
        ServiceConfig template = config("s3Endpoint", "http://mw1:9040");
        ServiceConfig current = config("s3Endpoint", "http://192.168.10.131:9040");

        Map<Generators, List<ServiceConfig>> result = ServiceInstallServiceImpl.mergeConfigFileMap(
                Map.of(generator, List.of(template)), Map.of("s3Endpoint", current));

        assertThat(result.get(generator)).containsExactly(current);
        assertThat(template.getValue()).isEqualTo("http://mw1:9040");
    }

    @Test
    void serializesServiceRoleWithoutReadingArchitectureSpecificPackageGetters() {
        ServiceRoleInfo role = new ServiceRoleInfo();
        role.setName("OtelCollector");

        String json = JSON.toJSONString(role);

        assertThat(json).doesNotContain("packageName", "decompressPackageName");
    }

    @Test
    void roundTripsConfigFileMapWithoutComplexJsonKeys() {
        Generators generator = new Generators();
        generator.setFilename("otelcol.yaml");
        ServiceConfig endpoint = config("s3Endpoint", "http://192.168.10.131:9040");
        String json = ServiceConfigUtils.serializeConfigFileMap(Map.of(generator, List.of(endpoint)));
        ClusterServiceRoleGroupConfig roleGroupConfig = new ClusterServiceRoleGroupConfig();
        roleGroupConfig.setServiceName("OTELCOLLECTOR");
        roleGroupConfig.setConfigFileJson(json);
        Map<Generators, List<ServiceConfig>> result = new HashMap<>();

        ServiceConfigUtils.generateConfigFileMap(result, roleGroupConfig, 999);

        assertThat(json).startsWith("[");
        assertThat(result.get(generator)).singleElement().extracting(ServiceConfig::getValue)
                .isEqualTo("http://192.168.10.131:9040");
    }

    private static ServiceConfig config(String name, String value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }
}
