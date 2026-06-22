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

package com.datasophon.api.observability;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OtelExporterSwitchService {
    
    private static final Logger log = LoggerFactory.getLogger(OtelExporterSwitchService.class);
    
    private static final String OTEL_SERVICE = "OTELCOLLECTOR";
    private static final String DORIS_FE = "DorisFE";
    private static final String DORIS_BE = "DorisBE";
    
    private final OtelCollectorConfigService configService;
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;
    private final ServiceInstallService installService;
    private final OtelCredentialService credentialService;
    
    public OtelExporterSwitchService(OtelCollectorConfigService configService,
                                     ClusterServiceRoleInstanceService roleService,
                                     ClusterVariableService variableService,
                                     ServiceInstallService installService,
                                     OtelCredentialService credentialService) {
        this.configService = configService;
        this.roleService = roleService;
        this.variableService = variableService;
        this.installService = installService;
        this.credentialService = credentialService;
    }
    
    public ExecResult switchNode(Integer clusterId, String hostname, ExporterMode mode) {
        return switchNode(clusterId, hostname, mode, Map.of());
    }
    
    public ExecResult switchNode(Integer clusterId, String hostname, ExporterMode mode,
                                 Map<String, String> overrides) {
        if (mode == ExporterMode.DORIS && !isDorisReady(clusterId)) {
            return ExecResult.error("Doris is not ready");
        }
        Map<String, String> params = serviceParams(clusterId);
        params.putAll(overrides);
        params.put("exporterMode", mode.configValue());
        if (mode == ExporterMode.DORIS) {
            List<ClusterServiceRoleInstanceEntity> frontends = roles(clusterId, DORIS_FE);
            List<ClusterServiceRoleInstanceEntity> backends = roles(clusterId, DORIS_BE);
            Map<String, String> dorisVariables = variables(clusterId, "DORIS");
            OtelCredentials credentials = credentialService.getOrCreate(clusterId);
            String feHttpPort = dorisVariables.getOrDefault("http_port", "8030");
            String beHttpPort = dorisVariables.getOrDefault("webserver_port", "8040");
            params.put("dorisEndpoint", "http://" + frontends.get(0).getHostname() + ":"
                    + feHttpPort);
            params.put("dorisDatabase", "otel");
            params.put("dorisUser", "otel_collector");
            params.put("dorisPassword", credentials.collectorPassword());
            // Doris metrics scrape variables — FE :http_port/metrics, BE :webserver_port/metrics
            // Labels mirror the existing prometheus.ftl configs/doris.json dimensions
            // so OtelMetricsQueryService SQL filters on group/job/instance work correctly.
            params.put("scrapeDoris", "true");
            params.put("dorisClusterJobName", "doris");
            String feScrapeTargets = frontends.stream()
                    .map(fe -> fe.getHostname() + ":" + feHttpPort)
                    .collect(Collectors.joining(";"));
            params.put("dorisFeScrapeTargets", feScrapeTargets);
            String beScrapeTargets = backends.stream()
                    .map(be -> be.getHostname() + ":" + beHttpPort)
                    .collect(Collectors.joining(";"));
            params.put("dorisBeScrapeTargets", beScrapeTargets);
        }
        return configService.pushNodeConfig(clusterId, hostname, params);
    }
    
    public boolean isDorisReady(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> frontends = roles(clusterId, DORIS_FE);
        List<ClusterServiceRoleInstanceEntity> backends = roles(clusterId, DORIS_BE);
        return !frontends.isEmpty() && !backends.isEmpty()
                && frontends.stream().allMatch(this::isRunning)
                && backends.stream().allMatch(this::isRunning);
    }
    
    private List<ClusterServiceRoleInstanceEntity> roles(Integer clusterId, String roleName) {
        return roleService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, roleName);
    }
    
    private boolean isRunning(ClusterServiceRoleInstanceEntity role) {
        return ServiceRoleState.RUNNING.equals(role.getServiceRoleState());
    }
    
    private Map<String, String> serviceParams(Integer clusterId) {
        Map<String, String> params = new HashMap<>();
        try {
            List<ServiceConfig> configs = installService.getServiceConfigOption(clusterId, OTEL_SERVICE);
            for (ServiceConfig config : configs) {
                if (config.getName() != null && config.getValue() != null) {
                    params.put(config.getName(), String.valueOf(config.getValue()));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Failed to load {} service config for cluster {}: {}", OTEL_SERVICE, clusterId, e.getMessage());
        }
        return params;
    }
    
    private Map<String, String> variables(Integer clusterId, String serviceName) {
        Map<String, String> result = new HashMap<>();
        for (ClusterVariable variable : variableService.getVariables(clusterId, serviceName)) {
            result.put(variable.getVariableName(), variable.getVariableValue());
        }
        return result;
    }
}
