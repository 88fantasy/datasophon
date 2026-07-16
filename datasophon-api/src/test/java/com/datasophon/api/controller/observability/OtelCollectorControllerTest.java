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

package com.datasophon.api.controller.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.api.observability.OtelCollectorConfigService;
import com.datasophon.api.observability.OtelExporterSwitchService;
import com.datasophon.api.observability.OtelSchemaOrchestrator;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class OtelCollectorControllerTest {
    
    @Test
    void controllerMappingsDoNotIncludeApiPrefix() {
        assertThat(mappingValues(OtelCollectorController.class))
                .containsExactly("observability/otelcol");
        assertThat(mappingValues(OtelMonitorController.class))
                .containsExactly("observability/otelcol");
    }
    
    @Test
    void forwardsUiParametersToNodePush() {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        OtelCollectorConfigService configService =
                new OtelCollectorConfigService(null, null, null, null, null, null) {
            @Override
            public ExecResult pushNodeConfig(Integer clusterId, String hostname, Map<String, String> params) {
                captured.set(params);
                return ExecResult.success();
            }
        };
        OtelCollectorController controller = new OtelCollectorController(
                configService, installService(List.of()), null, null);
        
        Result result = controller.push(7, "worker-1", Map.of("batchSize", "4096"));
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(captured.get()).containsEntry("batchSize", "4096");
    }
    
    @Test
    void appliesSchemaAndUsesStagedSwitchForDorisMode() {
        AtomicBoolean schemaApplied = new AtomicBoolean();
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        OtelSchemaOrchestrator schema = new OtelSchemaOrchestrator(null, null, null, null) {
            @Override
            public void applyIfReady(Integer clusterId) {
                schemaApplied.set(true);
            }
        };
        OtelExporterSwitchService switchService = new OtelExporterSwitchService(null, null, null, null, null) {
            @Override
            public ExecResult switchNode(Integer clusterId, String hostname,
                                         com.datasophon.api.observability.ExporterMode mode,
                                         Map<String, String> overrides) {
                assertThat(schemaApplied).isTrue();
                captured.set(overrides);
                return ExecResult.success();
            }
        };
        OtelCollectorController controller = new OtelCollectorController(
                newConfigService(), installService(List.of()), switchService, schema);
        
        Result result = controller.push(7, "worker-1", Map.of(
                "exporterMode", "doris", "batchSize", "4096"));
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(captured.get()).containsEntry("batchSize", "4096");
    }
    
    @Test
    void exposesCollectorConfigurationMetadata() {
        ServiceConfig batchSize = new ServiceConfig();
        batchSize.setName("batchSize");
        batchSize.setValue("8192");
        OtelCollectorController controller = new OtelCollectorController(
                newConfigService(), installService(List.of(batchSize)), null, null);
        
        Result result = controller.config(7);
        
        assertThat(result.getData()).asList().singleElement().isSameAs(batchSize);
    }
    
    private static ServiceInstallService installService(List<ServiceConfig> configs) {
        return (ServiceInstallService) Proxy.newProxyInstance(
                ServiceInstallService.class.getClassLoader(),
                new Class<?>[]{ServiceInstallService.class},
                (proxy, method, args) -> "getServiceConfigOption".equals(method.getName()) ? configs : null);
    }
    
    private static OtelCollectorConfigService newConfigService() {
        return new OtelCollectorConfigService(null, null, null, null, null, null);
    }
    
    private static List<String> mappingValues(Class<?> controllerClass) {
        RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
        return Arrays.stream(mapping.value()).toList();
    }
}
