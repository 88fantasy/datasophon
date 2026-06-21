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

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.observability.ExporterMode;
import com.datasophon.api.observability.OtelCollectorConfigService;
import com.datasophon.api.observability.OtelExporterSwitchService;
import com.datasophon.api.observability.OtelSchemaOrchestrator;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/observability/otelcol")
public class OtelCollectorController extends ApiController {
    
    private static final Logger log = LoggerFactory.getLogger(OtelCollectorController.class);
    
    private final OtelCollectorConfigService configService;
    private final ServiceInstallService installService;
    private final OtelExporterSwitchService switchService;
    private final OtelSchemaOrchestrator schemaOrchestrator;
    
    public OtelCollectorController(OtelCollectorConfigService configService,
                                   ServiceInstallService installService,
                                   OtelExporterSwitchService switchService,
                                   OtelSchemaOrchestrator schemaOrchestrator) {
        this.configService = configService;
        this.installService = installService;
        this.switchService = switchService;
        this.schemaOrchestrator = schemaOrchestrator;
    }
    
    @PostMapping("push")
    public Result push(@RequestParam Integer clusterId, @RequestParam String hostname,
                       @RequestBody(required = false) Map<String, String> params) {
        Map<String, String> effectiveParams =
                params == null ? new HashMap<>() : new HashMap<>(params);
        ExecResult r;
        String exporterMode = effectiveParams.get("exporterMode");
        if (exporterMode == null) {
            r = configService.pushNodeConfig(clusterId, hostname, effectiveParams);
        } else {
            ExporterMode mode;
            try {
                mode = ExporterMode.fromConfigValue(exporterMode);
            } catch (IllegalArgumentException e) {
                return Result.error("无效的 exporterMode: " + exporterMode);
            }
            if (mode == ExporterMode.DORIS) {
                schemaOrchestrator.applyIfReady(clusterId);
            }
            r = switchService.switchNode(clusterId, hostname, mode, effectiveParams);
        }
        return Boolean.TRUE.equals(r.getExecResult())
                ? Result.success()
                : Result.error("otelcol 配置下发失败");
    }
    
    @GetMapping("config")
    public Result config(@RequestParam Integer clusterId) {
        try {
            return Result.success(installService.getServiceConfigOption(clusterId, "OTELCOLLECTOR"));
        } catch (RuntimeException e) {
            log.warn("Failed to load OTELCOLLECTOR config for cluster {}: {}", clusterId, e.getMessage());
            return Result.error("OTELCOLLECTOR 配置读取失败，服务可能未安装");
        }
    }
}
