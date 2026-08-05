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

import com.datasophon.api.dto.v2.ApisixGatewayPushResult;
import com.datasophon.api.dto.v2.ApisixGatewayResponse;
import com.datasophon.api.dto.v2.ApisixGatewayRole;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.api.utils.ServiceLifecycleUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * APISIX(standalone) 网关配置 Tab 的读写主干：真相源是隐藏参数 {@code apisixGatewayYaml}，
 * 保存时复用 {@link ServiceInstallService#saveServiceConfig} 落库，再只对 {@code apisix.yaml}
 * 这一个 generator 调 {@link ServiceLifecycleUtils#configServiceRoleInstance} 下发（不重启）。
 */
@Service
public class ApisixGatewayConfigService {

    private static final Logger log = LoggerFactory.getLogger(ApisixGatewayConfigService.class);

    static final String SERVICE_NAME = "APISIX";
    static final String ROLE_NAME = "Apisix";
    static final String GATEWAY_YAML_PARAM = "apisixGatewayYaml";
    static final String ROUTE_URI_PARAM = "apisixRouteUri";
    static final String UPSTREAM_HOST_PARAM = "apisixUpstreamHost";
    static final String UPSTREAM_PORT_PARAM = "apisixUpstreamPort";
    static final String APISIX_YAML_FILENAME = "apisix.yaml";

    /**
     * plugin_metadata + #END 由 apisix-routes.ftl 模板固定输出（不交给 UI 编辑），
     * 这里必须与该模板的固定段保持一致，模板改动时需同步更新此常量。
     */
    static final String MANAGED_SUFFIX = "\n"
            + "# opentelemetry 插件运行时读取的是 plugin_metadata，不是 config.yaml 的 plugin_attr——\n"
            + "# 缺失时插件会静默跳过（access.log 报 \"plugin_metadata is required\"），不生成任何 span。\n"
            + "plugin_metadata:\n"
            + "  - id: opentelemetry\n"
            + "    resource:\n"
            + "      service.name: apisix\n"
            + "    collector:\n"
            + "      address: 127.0.0.1:4318\n"
            + "      request_timeout: 3\n"
            + "    batch_span_processor:\n"
            + "      drop_on_queue_full: false\n"
            + "      max_queue_size: 1024\n"
            + "      batch_timeout: 2\n"
            + "#END\n";

    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceInstanceService serviceInstanceService;
    private final ClusterServiceInstanceRoleGroupService roleGroupService;
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ServiceInstallService serviceInstallService;
    private final Executor masterExecutor;

    public ApisixGatewayConfigService(ClusterInfoService clusterInfoService,
                                      ClusterServiceInstanceService serviceInstanceService,
                                      ClusterServiceInstanceRoleGroupService roleGroupService,
                                      ClusterServiceRoleGroupConfigService roleGroupConfigService,
                                      ClusterServiceRoleInstanceService roleInstanceService,
                                      ServiceInstallService serviceInstallService,
                                      @Qualifier("masterExecutor") Executor masterExecutor) {
        this.clusterInfoService = clusterInfoService;
        this.serviceInstanceService = serviceInstanceService;
        this.roleGroupService = roleGroupService;
        this.roleGroupConfigService = roleGroupConfigService;
        this.roleInstanceService = roleInstanceService;
        this.serviceInstallService = serviceInstallService;
        this.masterExecutor = masterExecutor;
    }

    public ApisixGatewayResponse getGatewayConfig(Integer clusterId, Integer instanceId) {
        requireApisixInstance(instanceId);

        Map<String, ServiceConfig> currentConfigs = toConfigMap(loadEffectiveConfigs(clusterId));

        String gatewayYaml = stringValue(currentConfigs.get(GATEWAY_YAML_PARAM));
        if (gatewayYaml == null || gatewayYaml.isBlank()) {
            gatewayYaml = buildInitialGatewayYaml(
                    stringValue(currentConfigs.get(ROUTE_URI_PARAM)),
                    stringValue(currentConfigs.get(UPSTREAM_HOST_PARAM)),
                    stringValue(currentConfigs.get(UPSTREAM_PORT_PARAM)));
        }

        List<ApisixGatewayRole> roles = roleInstanceService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, ROLE_NAME)
                .stream()
                .map(e -> new ApisixGatewayRole(e.getHostname(), e.getServiceRoleState().getDesc()))
                .toList();

        return new ApisixGatewayResponse(gatewayYaml, MANAGED_SUFFIX, roles);
    }

    public List<ApisixGatewayPushResult> saveGatewayConfig(Integer clusterId, Integer instanceId, String gatewayYaml) {
        ClusterServiceInstanceEntity serviceInstance = requireApisixInstance(instanceId);
        validateGatewayYaml(gatewayYaml);

        NeedRestart beforeNeedRestart = serviceInstance.getNeedRestart();
        ClusterServiceInstanceRoleGroup defaultGroup =
                roleGroupService.getDefaultRoleGroupByServiceInstanceId(instanceId);

        List<ServiceConfig> configs = loadEffectiveConfigs(clusterId);
        for (ServiceConfig config : configs) {
            if (GATEWAY_YAML_PARAM.equals(config.getName())) {
                config.setValue(gatewayYaml);
            }
        }

        // roleGroupId=-1：saveServiceConfig 内部据此取默认角色组（Apisix 只有一个角色，无需前端选组）
        serviceInstallService.saveServiceConfig(clusterId, SERVICE_NAME, configs, -1);

        if (beforeNeedRestart == NeedRestart.NO) {
            resetNeedRestart(instanceId, defaultGroup.getId());
        }

        return pushToRunningRoles(clusterId, defaultGroup.getId());
    }

    /**
     * {@link ServiceInstallService#getServiceConfigOption} 对已安装实例只回放上次持久化的
     * configJson，不会带上 DDL 新增但从未保存过的参数。已安装的 APISIX 实例可能早于
     * {@code apisixGatewayYaml} 这个参数存在，这里补一次兜底合并，否则该实例永远无法写入网关配置。
     */
    private List<ServiceConfig> loadEffectiveConfigs(Integer clusterId) {
        List<ServiceConfig> configs = new ArrayList<>(serviceInstallService.getServiceConfigOption(clusterId, SERVICE_NAME));
        List<ServiceConfig> ddlConfigs = serviceInstallService.getServiceConfigFromDdl(clusterId, SERVICE_NAME);
        return ServiceConfigUtils.addAll(configs, ddlConfigs);
    }

    private List<ApisixGatewayPushResult> pushToRunningRoles(Integer clusterId, Integer roleGroupId) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        ClusterServiceRoleGroupConfig savedConfig = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);

        Map<Generators, List<ServiceConfig>> fullConfigFileMap = new HashMap<>();
        ServiceConfigUtils.generateConfigFileMap(fullConfigFileMap, savedConfig, clusterId);

        Map<Generators, List<ServiceConfig>> apisixYamlOnly = new HashMap<>();
        fullConfigFileMap.forEach((generator, configs) -> {
            if (APISIX_YAML_FILENAME.equals(generator.getFilename())) {
                apisixYamlOnly.put(generator, configs);
            }
        });

        List<ClusterServiceRoleInstanceEntity> runningRoles = roleInstanceService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, ROLE_NAME)
                .stream()
                .filter(e -> e.getServiceRoleState() == ServiceRoleState.RUNNING)
                .toList();

        // 每台网关节点的下发是独立、阻塞的 gRPC 调用（最长 180s 超时），按主机 fan-out 到
        // masterExecutor，避免多台网关节点时总耗时随节点数线性叠加（同款写法见 HostCheckService）。
        List<CompletableFuture<ApisixGatewayPushResult>> futures = new ArrayList<>(runningRoles.size());
        for (ClusterServiceRoleInstanceEntity role : runningRoles) {
            Supplier<ApisixGatewayPushResult> task = () -> pushToRole(clusterInfo, apisixYamlOnly, role);
            try {
                futures.add(CompletableFuture.supplyAsync(task, masterExecutor));
            } catch (RejectedExecutionException e) {
                // 池满时退化为调用线程串行执行，等价旧行为
                futures.add(CompletableFuture.completedFuture(task.get()));
            }
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private ApisixGatewayPushResult pushToRole(ClusterInfoEntity clusterInfo,
                                               Map<Generators, List<ServiceConfig>> configFileMap,
                                               ClusterServiceRoleInstanceEntity role) {
        try {
            ExecResult result = ServiceLifecycleUtils.configServiceRoleInstance(clusterInfo, configFileMap, role);
            return new ApisixGatewayPushResult(role.getHostname(), result != null && result.isSuccess(),
                    result != null ? result.getExecOut() : "下发结果为空");
        } catch (Exception e) {
            log.error("push apisix gateway config to {} failed", role.getHostname(), e);
            return new ApisixGatewayPushResult(role.getHostname(), false, e.getMessage());
        }
    }

    private void resetNeedRestart(Integer instanceId, Integer roleGroupId) {
        ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(instanceId);
        serviceInstance.setNeedRestart(NeedRestart.NO);
        serviceInstanceService.updateById(serviceInstance);

        ClusterServiceInstanceRoleGroup roleGroup = roleGroupService.getById(roleGroupId);
        roleGroup.setNeedRestart(NeedRestart.NO);
        roleGroupService.updateById(roleGroup);

        List<ClusterServiceRoleInstanceEntity> roleInstances = roleInstanceService.list(
                new QueryWrapper<ClusterServiceRoleInstanceEntity>().eq(Constants.ROLE_GROUP_ID, roleGroupId));
        roleInstances.forEach(e -> e.setNeedRestart(NeedRestart.NO));
        roleInstanceService.updateBatchById(roleInstances);
    }

    private ClusterServiceInstanceEntity requireApisixInstance(Integer instanceId) {
        ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(instanceId);
        if (serviceInstance == null || !SERVICE_NAME.equals(serviceInstance.getServiceName())) {
            throw new BusinessHintException("服务实例不是 APISIX");
        }
        return serviceInstance;
    }

    private void validateGatewayYaml(String gatewayYaml) {
        if (gatewayYaml.contains("#END")) {
            throw new BusinessHintException("网关配置中不能包含 #END（由平台模板统一追加）");
        }
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(gatewayYaml);
        } catch (Exception e) {
            throw new BusinessHintException("YAML 解析失败：" + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw new BusinessHintException("网关配置顶层必须是一个 map");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) parsed;
        if (doc.containsKey("plugin_metadata")) {
            throw new BusinessHintException("plugin_metadata 由平台模板托管，不能出现在网关配置中");
        }
        if (!hasGlobalRulePlugin(doc, "prometheus") || !hasGlobalRulePlugin(doc, "opentelemetry")) {
            throw new BusinessHintException("global_rules 必须同时包含 prometheus 与 opentelemetry 两条规则");
        }
    }

    private boolean hasGlobalRulePlugin(Map<String, Object> doc, String pluginName) {
        if (!(doc.get("global_rules") instanceof List<?> rules)) {
            return false;
        }
        for (Object rule : rules) {
            if (rule instanceof Map<?, ?> ruleMap && ruleMap.get("plugins") instanceof Map<?, ?> plugins
                    && plugins.containsKey(pluginName)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, ServiceConfig> toConfigMap(List<ServiceConfig> configs) {
        Map<String, ServiceConfig> map = new HashMap<>();
        for (ServiceConfig config : configs) {
            map.put(config.getName(), config);
        }
        return map;
    }

    private static String stringValue(ServiceConfig config) {
        if (config == null || config.getValue() == null) {
            return null;
        }
        return String.valueOf(config.getValue());
    }

    private static String buildInitialGatewayYaml(String uri, String host, String port) {
        return "upstreams:\n"
                + "  - id: 1\n"
                + "    type: roundrobin\n"
                + "    nodes:\n"
                + "      '" + escapeSingleQuote(host) + ":" + port + "': 1\n"
                + "\n"
                + "routes:\n"
                + "  - id: 1\n"
                + "    uri: '" + escapeSingleQuote(uri) + "'\n"
                + "    upstream_id: 1\n"
                + "\n"
                + "global_rules:\n"
                + "  - id: 1\n"
                + "    plugins:\n"
                + "      prometheus:\n"
                + "        prefer_name: true\n"
                + "  - id: 2\n"
                + "    plugins:\n"
                + "      opentelemetry:\n"
                + "        sampler:\n"
                + "          name: always_on\n";
    }

    private static String escapeSingleQuote(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
