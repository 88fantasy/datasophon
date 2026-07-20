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
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * 改过并重启后的最新值，参数需在 {@code service_ddl.json} 里显式标注 {@code "port": true}）；② 读不到实时值
 * 时退回 ddl 声明的 {@code defaultValue}（内存态 {@link ServiceInfoMap}，无需查库）。这一双层推导与
 * {@code OtelScrapeConfigBuilder} 的 jmxPortParam 单端口推导同源，区别是这里按 {@code isPort} 标志枚举
 * 一个角色的全部端口，而不是只匹配一个固定参数名。
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
     * <p>先按集群实例表精确反查（addr 命中集群某台主机时，逐个角色实例比对端口）；
     * 未命中再查静态知名端口表（平台自身/外部基础设施）。
     */
    public String resolveServiceType(Integer clusterId, String addr, String portValue) {
        Integer port = parsePort(portValue);
        if (port == null || StringUtils.isBlank(addr)) {
            return null;
        }
        String byInstance = resolveByClusterInstance(clusterId, addr, port);
        if (StringUtils.isNotBlank(byInstance)) {
            return byInstance;
        }
        return WELL_KNOWN_PORTS.get(port);
    }

    private String resolveByClusterInstance(Integer clusterId, String addr, int port) {
        if (clusterId == null) {
            return null;
        }
        ClusterHostDO host = hostService.getClusterHostByIp(addr);
        if (host == null) {
            host = hostService.getClusterHostByHostname(addr);
        }
        if (host == null) {
            return null;
        }
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        String clusterFrame = cluster == null ? null : cluster.getClusterFrame();
        if (StringUtils.isBlank(clusterFrame)) {
            return null;
        }
        List<ClusterServiceRoleInstanceEntity> roles =
                roleService.getServiceRoleListByHostnameAndClusterId(host.getHostname(), clusterId);
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
        List<RolePort> live = livePortsFromConfig(role);
        if (!live.isEmpty()) {
            return live;
        }
        return defaultPortsFromDdl(clusterFrame, role.getServiceName());
    }

    private List<RolePort> livePortsFromConfig(ClusterServiceRoleInstanceEntity role) {
        Integer roleGroupId = role.getRoleGroupId();
        if (roleGroupId == null) {
            return List.of();
        }
        ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        if (config == null || StringUtils.isBlank(config.getConfigJson())) {
            return List.of();
        }
        if (NeedRestart.YES.equals(role.getNeedRestart())) {
            // 与 OtelScrapeConfigBuilder#livePortFromConfig 同样的已知局限：needRestart 是角色组级别的
            // 粗粒度标记，无法精确到"待生效的改动是不是端口参数"，退回上一版本配置是当前数据模型下
            // 能做到的最接近实际监听端口的近似。
            Integer previousVersion = config.getConfigVersion() == null ? null : config.getConfigVersion() - 1;
            if (previousVersion == null || previousVersion < 1) {
                return List.of();
            }
            config = roleGroupConfigService.getConfigByRoleGroupIdAndVersion(roleGroupId, previousVersion);
            if (config == null || StringUtils.isBlank(config.getConfigJson())) {
                return List.of();
            }
        }
        return extractPorts(role.getServiceRoleName(), config.getConfigJson());
    }

    private List<RolePort> extractPorts(String serviceRoleName, String configJson) {
        List<RolePort> ports = new ArrayList<>();
        try {
            List<ServiceConfig> configs = JSON.parseArray(configJson, ServiceConfig.class);
            for (ServiceConfig config : configs) {
                if (!config.isPort() || config.getValue() == null) {
                    continue;
                }
                Integer port = parsePort(String.valueOf(config.getValue()));
                if (port != null) {
                    ports.add(new RolePort(config.getName(), config.getLabel(), port));
                }
            }
        } catch (Exception e) {
            logger.warn("解析角色 {} 的实时配置端口失败，退回 ddl 参数默认值", serviceRoleName, e);
        }
        return ports;
    }

    /**
     * configJson 里没有可用端口时的兜底：直接读 ddl 声明的 defaultValue（内存态 ServiceInfoMap，无需查库）。
     *
     * <p>已知局限：{@code ServiceInfo.parameters} 是服务级别的扁平列表，不区分角色（如 DORIS 的
     * http_port/query_port 属于 DorisFE、be_port/webserver_port 属于 DorisBE，但都在同一个数组里）。
     * 该兜底只在角色组配置缺失时触发（老集群升级未回填 / roleGroupId 为空等防御性场景），届时会把
     * 同服务下其它角色的端口也一并列出——对拓扑反查而言仍能定位到正确的服务（只是角色类型不精确），
     * 对实例列表展示则可能多列出几个不属于该角色的端口，属已知且可接受的降级行为。
     */
    private List<RolePort> defaultPortsFromDdl(String clusterFrame, String serviceName) {
        ServiceInfo serviceInfo = ServiceInfoMap.get(clusterFrame + Constants.UNDERLINE + serviceName);
        if (serviceInfo == null || serviceInfo.getParameters() == null) {
            return List.of();
        }
        List<RolePort> ports = new ArrayList<>();
        for (ServiceConfig param : serviceInfo.getParameters()) {
            if (!param.isPort() || param.getDefaultValue() == null) {
                continue;
            }
            Integer port = parsePort(String.valueOf(param.getDefaultValue()));
            if (port != null) {
                ports.add(new RolePort(param.getName(), param.getLabel(), port));
            }
        }
        return ports;
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
