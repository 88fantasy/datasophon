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

package com.datasophon.api.service;

import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;

/**
 * 集群服务角色实例的端口信息解析器。
 *
 * <p>两类消费场景共用同一套底层逻辑：① 拓扑图把外部依赖节点的 {@code ip:port} 反查成真实服务类型
 * （{@link #resolveServiceType(Integer, String, String)}）；② 服务实例列表展示某个角色实例当前监听的
 * 全部端口（{@link #portsOf(ClusterServiceRoleInstanceEntity)}）。
 *
 * <p>端口来源分两层：① 角色组的实时配置（{@code ClusterServiceRoleGroupConfig#configJson}，用户在 Web UI
 * 改过并重启后的最新值）；② 读不到实时值时退回 ddl 声明的 {@code defaultValue}（内存态
 * {@link ServiceInfoMap}，无需查库）。角色与端口参数的归属由 role 的 {@code portParams} 显式声明，避免
 * 同一服务下多个角色互相混入端口。
 */
@Component
public class ServiceInstancePortResolver {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancePortResolver.class);

    /**
     * 平台自身进程与外部基础设施组件的知名端口：这些端口不由任何 service_ddl.json 声明（它们不是
     * 被托管的服务角色），只能硬编码。数据源见 deploy/deployment-standalone-doris.md §6 端口清单。
     */
    private static final Map<Integer, String> WELL_KNOWN_PORTS = Map.of(
            8080, "datasophon-api",
            18081, "datasophon-master",
            18082, "datasophon-worker",
            3306, "mysql",
            8081, "nexus",
            9040, "rustfs",
            9041, "rustfs");

    private final ClusterHostService hostService;
    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService;

    public ServiceInstancePortResolver(ClusterHostService hostService,
                                       ClusterInfoService clusterInfoService,
                                       ClusterServiceRoleInstanceService roleService,
                                       ClusterServiceRoleGroupConfigService roleGroupConfigService) {
        this.hostService = hostService;
        this.clusterInfoService = clusterInfoService;
        this.roleService = roleService;
        this.roleGroupConfigService = roleGroupConfigService;
    }

    /**
     * 把 {@code addr:port}（trace 里解析出的 server.address/server.port）反查成服务类型（小写
     * serviceName，天然可命中前端 serviceIconFor 的关键字匹配），查不到时返回 {@code null}（由调用方
     * 保留原有兜底展示）。
     *
     * <p>只有 addr 属于当前集群主机时才继续反查：先逐个角色实例比对端口，未命中再查静态知名端口表。
     * 该约束避免把任意外部 {@code host:8080} 误识别为 datasophon-api。
     */
    public String resolveServiceType(Integer clusterId, String addr, String portValue) {
        Integer port = parsePort(portValue);
        if (port == null || StringUtils.isBlank(addr)) {
            return null;
        }
        ClusterHostDO host = findClusterHost(clusterId, addr);
        if (host == null) {
            return null;
        }
        String byInstance = resolveByClusterInstance(clusterId, host, port);
        if (StringUtils.isNotBlank(byInstance)) {
            return byInstance;
        }
        return WELL_KNOWN_PORTS.get(port);
    }

    private ClusterHostDO findClusterHost(Integer clusterId, String addr) {
        ClusterHostDO host = hostService.getClusterHostByIp(addr);
        if (host == null) {
            host = hostService.getClusterHostByHostname(addr);
        }
        if (host == null || !Objects.equals(clusterId, host.getClusterId())) {
            return null;
        }
        return host;
    }

    private String resolveByClusterInstance(Integer clusterId, ClusterHostDO host, int port) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        String clusterFrame = cluster == null ? null : cluster.getClusterFrame();
        if (StringUtils.isBlank(clusterFrame)) {
            return null;
        }
        List<ClusterServiceRoleInstanceEntity> roles =
                roleService.getServiceRoleListByHostnameAndClusterId(host.getHostname(), clusterId);
        if (roles == null) {
            return null;
        }
        for (ClusterServiceRoleInstanceEntity role : roles) {
            for (RolePort rolePort : portsOf(clusterFrame, role)) {
                if (rolePort.port() == port) {
                    return role.getServiceName().toLowerCase();
                }
            }
        }
        return null;
    }

    /**
     * 枚举一个角色实例当前监听的全部端口（一个角色可能有多个，如 Doris FE 的 http/rpc/query 三端口）。
     */
    public List<RolePort> portsOf(ClusterServiceRoleInstanceEntity role) {
        if (role == null || role.getClusterId() == null) {
            return List.of();
        }
        ClusterInfoEntity cluster = clusterInfoService.getById(role.getClusterId());
        String clusterFrame = cluster == null ? null : cluster.getClusterFrame();
        return portsOf(clusterFrame, role);
    }

    private List<RolePort> portsOf(String clusterFrame, ClusterServiceRoleInstanceEntity role) {
        if (StringUtils.isAnyBlank(clusterFrame, role.getServiceName())) {
            return List.of();
        }
        ServiceInfo serviceInfo = ServiceInfoMap.get(clusterFrame + Constants.UNDERLINE + role.getServiceName());
        if (serviceInfo == null || serviceInfo.getParameters() == null) {
            return List.of();
        }
        List<String> portParamNames = portParamNames(serviceInfo, role.getServiceRoleName());
        if (portParamNames.isEmpty()) {
            return List.of();
        }
        Map<String, ServiceConfig> ddlParams = new LinkedHashMap<>();
        for (ServiceConfig param : serviceInfo.getParameters()) {
            ddlParams.put(param.getName(), param);
        }
        Map<String, ServiceConfig> liveParams = liveParamsFromConfig(role);
        Map<String, RolePort> ports = new LinkedHashMap<>();
        for (String paramName : portParamNames) {
            ServiceConfig ddlParam = ddlParams.get(paramName);
            if (ddlParam == null) {
                logger.warn("角色 {} 声明的端口参数 {} 不存在", role.getServiceRoleName(), paramName);
                continue;
            }
            ServiceConfig liveParam = liveParams.get(paramName);
            Object value = liveParam == null ? null : liveParam.getValue();
            Integer port = value == null ? null : parsePort(String.valueOf(value));
            if (port == null && ddlParam.getDefaultValue() != null) {
                port = parsePort(String.valueOf(ddlParam.getDefaultValue()));
            }
            if (port != null) {
                ports.put(paramName, new RolePort(paramName, ddlParam.getLabel(), port));
            }
        }
        return List.copyOf(ports.values());
    }

    private List<String> portParamNames(ServiceInfo serviceInfo, String serviceRoleName) {
        if (serviceInfo.getRoles() == null) {
            return List.of();
        }
        for (ServiceRoleInfo roleInfo : serviceInfo.getRoles()) {
            if (Objects.equals(serviceRoleName, roleInfo.getName())) {
                if (roleInfo.getPortParams() != null) {
                    return roleInfo.getPortParams();
                }
                // 兼容尚未补充 portParams 的单角色自定义服务。
                if (serviceInfo.getRoles().size() == 1) {
                    return serviceInfo.getParameters().stream()
                            .filter(ServiceConfig::isPort)
                            .map(ServiceConfig::getName)
                            .toList();
                }
                return List.of();
            }
        }
        return List.of();
    }

    private Map<String, ServiceConfig> liveParamsFromConfig(ClusterServiceRoleInstanceEntity role) {
        Integer roleGroupId = role.getRoleGroupId();
        if (roleGroupId == null) {
            return Map.of();
        }
        ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        if (config == null || StringUtils.isBlank(config.getConfigJson())) {
            return Map.of();
        }
        if (NeedRestart.YES.equals(role.getNeedRestart())) {
            // 与 OtelScrapeConfigBuilder#livePortFromConfig 同样的已知局限：needRestart 是角色组级别的
            // 粗粒度标记，无法精确到"待生效的改动是不是端口参数"，退回上一版本配置是当前数据模型下
            // 能做到的最接近实际监听端口的近似。
            Integer previousVersion = config.getConfigVersion() == null ? null : config.getConfigVersion() - 1;
            if (previousVersion == null || previousVersion < 1) {
                return Map.of();
            }
            config = roleGroupConfigService.getConfigByRoleGroupIdAndVersion(roleGroupId, previousVersion);
            if (config == null || StringUtils.isBlank(config.getConfigJson())) {
                return Map.of();
            }
        }
        return extractParams(role.getServiceRoleName(), config.getConfigJson());
    }

    private Map<String, ServiceConfig> extractParams(String serviceRoleName, String configJson) {
        Map<String, ServiceConfig> params = new LinkedHashMap<>();
        try {
            List<ServiceConfig> configs = JSON.parseArray(configJson, ServiceConfig.class);
            for (ServiceConfig config : configs) {
                if (StringUtils.isNotBlank(config.getName())) {
                    params.put(config.getName(), config);
                }
            }
        } catch (Exception e) {
            logger.warn("解析角色 {} 的实时配置端口失败，退回 ddl 参数默认值", serviceRoleName, e);
        }
        return params;
    }

    private static Integer parsePort(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 一个端口参数的解析结果：{@code paramName} 对应 ddl 里的 key/name，{@code label} 供展示。 */
    public record RolePort(String paramName, String label, int port) {
    }
}
