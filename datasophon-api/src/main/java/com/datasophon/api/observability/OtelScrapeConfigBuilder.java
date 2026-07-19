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

import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.load.ServiceRoleMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
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
    private static final String APISIX = "Apisix";

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
                    target(role.getServiceRoleName(), hostname, jmxPort), hostname + ":" + jmxPort,
                    group(role.getServiceRoleName()));
        }

        return yaml.toString();
    }

    /**
     * 监控端口一律来自角色元数据里声明的 jmxPortParam（指向某个 Web UI 可配置的业务参数），
     * 不再有独立的静态 jmxPort 字段。没有声明 jmxPortParam 的角色（如从未暴露过监控端口的角色）
     * 直接返回 null，不生成抓取任务。
     */
    private String port(String clusterFrame, ClusterServiceRoleInstanceEntity role) {
        if (StringUtils.isAnyBlank(clusterFrame, role.getServiceName(), role.getServiceRoleName())) {
            return null;
        }
        String key = clusterFrame + Constants.UNDERLINE + role.getServiceName()
                + Constants.UNDERLINE + role.getServiceRoleName();
        ServiceRoleInfo meta = ServiceRoleMap.get(key);
        if (meta == null || StringUtils.isBlank(meta.getJmxPortParam())) {
            return null;
        }
        String livePort = livePortFromConfig(key, meta, role);
        if (StringUtils.isNotBlank(livePort)) {
            return livePort;
        }
        return defaultPortFromDdl(clusterFrame, role.getServiceName(), meta.getJmxPortParam());
    }

    /**
     * 优先读该角色当前实际生效的配置值(用户在 Web UI 改过并重启后的最新值)；
     * 任何一步读不到都返回 null，由调用方退回 ddl 参数的 defaultValue。
     */
    private String livePortFromConfig(String key, ServiceRoleInfo meta, ClusterServiceRoleInstanceEntity role) {
        Integer roleGroupId = role.getRoleGroupId();
        if (roleGroupId == null) {
            return null;
        }
        ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        if (config == null || StringUtils.isBlank(config.getConfigJson())) {
            return null;
        }
        if (NeedRestart.YES.equals(role.getNeedRestart())) {
            // needRestart 是角色组级别的粗粒度标记：只要组内任意参数被改过就会置位，不代表这次
            // 待生效的改动就是监控端口本身。直接跳到 ddl 静态默认值会把"角色仍在用的自定义端口"
            // 误报成官方默认端口。改为退回上一个配置版本（假定它已通过前一次重启生效），比直接
            // 跳到 ddl 默认值更接近进程实际监听的端口；仍无法精确到"具体哪个参数"待生效，属于
            // 已知局限（需要按参数级别 diff 或记录"已生效版本号"才能做到，当前数据模型不支持）。
            Integer previousVersion = config.getConfigVersion() == null ? null : config.getConfigVersion() - 1;
            if (previousVersion == null || previousVersion < 1) {
                return null;
            }
            config = roleGroupConfigService.getConfigByRoleGroupIdAndVersion(roleGroupId, previousVersion);
            if (config == null || StringUtils.isBlank(config.getConfigJson())) {
                return null;
            }
        }
        return extractPort(key, meta, config.getConfigJson());
    }

    private String extractPort(String key, ServiceRoleInfo meta, String configJson) {
        try {
            List<ServiceConfig> configs = JSON.parseArray(configJson, ServiceConfig.class);
            for (ServiceConfig serviceConfig : configs) {
                if (meta.getJmxPortParam().equals(serviceConfig.getName()) && serviceConfig.getValue() != null) {
                    String value = String.valueOf(serviceConfig.getValue()).trim();
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析角色 {} 的实时配置端口失败，退回 ddl 参数默认值", key, e);
        }
        return null;
    }

    /**
     * configJson 里没有该参数时的兜底：直接读 ddl 声明的 defaultValue（内存态 ServiceInfoMap，无需查库）。
     * 覆盖两种场景：① 老集群升级后角色组 configJson 尚未回填新参数（正常情况下 DdlMetaServiceImpl 在 Master
     * 启动时会自动回填，这里是防御性兜底）；② 该角色从未走过配置向导、roleGroupId 为空。
     */
    private String defaultPortFromDdl(String clusterFrame, String serviceName, String jmxPortParam) {
        ServiceInfo serviceInfo = ServiceInfoMap.get(clusterFrame + Constants.UNDERLINE + serviceName);
        if (serviceInfo == null || serviceInfo.getParameters() == null) {
            return null;
        }
        for (ServiceConfig param : serviceInfo.getParameters()) {
            if (jmxPortParam.equals(param.getName()) && param.getDefaultValue() != null) {
                String value = String.valueOf(param.getDefaultValue()).trim();
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String path(String roleName) {
        return PATH_OVERRIDES.getOrDefault(roleName, DEFAULT_METRICS_PATH);
    }

    private static String target(String roleName, String hostname, String port) {
        return APISIX.equals(roleName) ? hostname + ":" + port : "127.0.0.1:" + port;
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
        // otelcol.ftl 把 ${localScrapeJobsYaml} 嵌在 "scrape_configs:"(6 空格缩进)下一行、不带任何
        // 前导空白，因此这里的列表项要按 prometheus/self 块同款的 8 空格起始自行携带完整缩进。
        yaml.append("        - job_name: '").append(quote(jobName)).append("'\n")
                .append("          scrape_interval: 15s\n")
                .append("          metrics_path: '").append(quote(metricsPath)).append("'\n")
                .append("          static_configs:\n")
                .append("            - targets: ['").append(quote(target)).append("']\n")
                .append("              labels: {job: '").append(quote(jobName))
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
