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

import com.datasophon.api.load.ServiceRoleJmxMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OtelScrapeConfigBuilder {
    
    private static final String DEFAULT_METRICS_PATH = "/metrics";
    private static final String DORIS_FE = "DorisFE";
    private static final String DORIS_BE = "DorisBE";
    
    private static final Map<String, String> PATH_OVERRIDES = new LinkedHashMap<>();
    
    static {
        PATH_OVERRIDES.put("ApiServer", "/dolphinscheduler/actuator/prometheus");
        PATH_OVERRIDES.put("MasterServer", "/actuator/prometheus");
        PATH_OVERRIDES.put("WorkerServer", "/actuator/prometheus");
        PATH_OVERRIDES.put("AlertServer", "/actuator/prometheus");
        PATH_OVERRIDES.put("NacosServer", "/nacos/actuator/prometheus");
        PATH_OVERRIDES.put("Apisix", "/apisix/prometheus/metrics");
        PATH_OVERRIDES.put("Minio", "/minio/v2/metrics/cluster");
    }
    
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterInfoService clusterInfoService;
    
    public OtelScrapeConfigBuilder(ClusterServiceRoleInstanceService roleService,
                                   ClusterInfoService clusterInfoService) {
        this.roleService = roleService;
        this.clusterInfoService = clusterInfoService;
    }
    
    public String build(Integer clusterId, String hostname) {
        StringBuilder yaml = new StringBuilder();
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        String clusterFrame = cluster == null ? null : cluster.getClusterFrame();
        
        List<ClusterServiceRoleInstanceEntity> roles =
                roleService.getServiceRoleListByHostnameAndClusterId(hostname, clusterId);
        for (ClusterServiceRoleInstanceEntity role : roles) {
            if (!ServiceRoleState.RUNNING.equals(role.getServiceRoleState())) {
                continue;
            }
            String jmxPort = port(clusterFrame, role);
            if (StringUtils.isBlank(jmxPort)) {
                continue;
            }
            appendJob(yaml, role.getServiceRoleName(), path(role.getServiceRoleName()),
                    "127.0.0.1:" + jmxPort, hostname + ":" + jmxPort, group(role.getServiceRoleName()));
        }
        
        return yaml.toString();
    }
    
    private static String port(String clusterFrame, ClusterServiceRoleInstanceEntity role) {
        if (StringUtils.isAnyBlank(clusterFrame, role.getServiceName(), role.getServiceRoleName())) {
            return null;
        }
        String key = clusterFrame + Constants.UNDERLINE + role.getServiceName()
                + Constants.UNDERLINE + role.getServiceRoleName();
        return ServiceRoleJmxMap.get(key);
    }
    
    private static String path(String roleName) {
        return PATH_OVERRIDES.getOrDefault(roleName, DEFAULT_METRICS_PATH);
    }
    
    private static String group(String roleName) {
        if (DORIS_FE.equals(roleName)) {
            return "fe";
        }
        if (DORIS_BE.equals(roleName)) {
            return "be";
        }
        return null;
    }
    
    private static void appendJob(StringBuilder yaml, String jobName, String metricsPath,
                                  String target, String instance, String group) {
        yaml.append("    - job_name: '").append(quote(jobName)).append("'\n")
                .append("      scrape_interval: 15s\n")
                .append("      metrics_path: '").append(quote(metricsPath)).append("'\n")
                .append("      static_configs:\n")
                .append("        - targets: ['").append(quote(target)).append("']\n")
                .append("          labels: {job: '").append(quote(jobName))
                .append("', instance: '").append(quote(instance)).append("'");
        if (StringUtils.isNotBlank(group)) {
            yaml.append(", group: '").append(quote(group)).append("'");
        }
        yaml.append("}\n");
    }
    
    private static String quote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
