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
import com.datasophon.api.load.ServiceRoleMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

@Component
public class OtelScrapeConfigBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(OtelScrapeConfigBuilder.class);
    
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
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService;
    
    public OtelScrapeConfigBuilder(ClusterServiceRoleInstanceService roleService,
                                   ClusterInfoService clusterInfoService,
                                   ClusterServiceRoleGroupConfigService roleGroupConfigService) {
        this.roleService = roleService;
        this.clusterInfoService = clusterInfoService;
        this.roleGroupConfigService = roleGroupConfigService;
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
    
    private String port(String clusterFrame, ClusterServiceRoleInstanceEntity role) {
        if (StringUtils.isAnyBlank(clusterFrame, role.getServiceName(), role.getServiceRoleName())) {
            return null;
        }
        String key = clusterFrame + Constants.UNDERLINE + role.getServiceName()
                + Constants.UNDERLINE + role.getServiceRoleName();
        String livePort = livePortFromConfig(key, role);
        if (StringUtils.isNotBlank(livePort)) {
            return livePort;
        }
        return ServiceRoleJmxMap.get(key);
    }
    
    /**
     * 部分角色的监控端口(jmxPort)与某个用户可在 Web UI 配置的业务参数复用同一端口(如 Doris FE 的
     * http_port)。若用户改了该参数并重启角色，实际监听端口会变，但 ddl 里的 jmxPort 是启动时加载的静态值，
     * 不会跟着变。这里优先读该角色当前实际生效的配置值；任何一步读不到都返回 null，由调用方退回静态默认值，
     * 保证行为不会比现状更差。
     */
    private String livePortFromConfig(String key, ClusterServiceRoleInstanceEntity role) {
        ServiceRoleInfo meta = ServiceRoleMap.get(key);
        if (meta == null || StringUtils.isBlank(meta.getJmxPortParam())) {
            return null;
        }
        if (NeedRestart.YES.equals(role.getNeedRestart())) {
            // 配置已保存但角色尚未重启生效，此时进程仍监听旧端口，不能采纳"待生效"的新值。
            return null;
        }
        Integer roleGroupId = role.getRoleGroupId();
        if (roleGroupId == null) {
            return null;
        }
        ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        if (config == null || StringUtils.isBlank(config.getConfigJson())) {
            return null;
        }
        try {
            List<ServiceConfig> configs = JSON.parseArray(config.getConfigJson(), ServiceConfig.class);
            for (ServiceConfig serviceConfig : configs) {
                if (meta.getJmxPortParam().equals(serviceConfig.getName()) && serviceConfig.getValue() != null) {
                    String value = String.valueOf(serviceConfig.getValue()).trim();
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析角色 {} 的实时配置端口失败，退回静态默认值", key, e);
        }
        return null;
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
