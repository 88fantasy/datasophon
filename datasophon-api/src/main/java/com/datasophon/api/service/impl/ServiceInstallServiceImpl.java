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

import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigFileMap;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.strategy.ServiceRoleStrategy;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.IOUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceState;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service("serviceInstallService")
@Transactional
@RequiredArgsConstructor
public class ServiceInstallServiceImpl implements ServiceInstallService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstallServiceImpl.class);

    private final ClusterInfoService clusterInfoService;

    private final FrameInfoService frameInfoService;

    private final FrameServiceService frameService;

    private final FrameServiceRoleService frameServiceRoleService;

    private final ClusterServiceCommandService commandService;

    private final ClusterServiceInstanceService serviceInstanceService;

    private final ClusterVariableService variableService;

    private final ClusterServiceInstanceRoleGroupService roleGroupService;

    private final ClusterServiceRoleGroupConfigService groupConfigService;

    private final ClusterServiceRoleInstanceService roleInstanceService;

    @Override
    public List<ServiceConfig> getServiceConfigOption(Integer clusterId, String serviceName) {
        List<ServiceConfig> list = null;
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);

        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);

        ClusterServiceInstanceEntity serviceInstance =
                serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(
                        clusterId, serviceName);
        if (Objects.nonNull(serviceInstance)) {
            list = listServiceConfigByServiceInstance(serviceInstance);
        } else {
            FrameServiceEntity frameService =
                    this.frameService.getServiceByFrameCodeAndServiceName(
                            clusterInfo.getClusterFrame(), serviceName);
            list = resolveFrameServiceConfigs(frameService, globalVariables);
        }

        ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(serviceName);
        if (Objects.nonNull(serviceRoleHandler)) {
            serviceRoleHandler.getConfig(clusterId, list);
        }

        return list;
    }

    @Override
    public List<ServiceConfig> getServiceConfigFromDdl(Integer clusterId, String serviceName) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        FrameServiceEntity frameService = this.frameService.getServiceByFrameCodeAndServiceName(clusterInfo.getClusterFrame(), serviceName);
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        List<ServiceConfig> list = resolveFrameServiceConfigs(frameService, globalVariables);

        ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(serviceName);
        if (Objects.nonNull(serviceRoleHandler)) {
            serviceRoleHandler.getConfig(clusterId, list);
        }

        return list;
    }

    static List<ServiceConfig> resolveFrameServiceConfigs(
                                                          FrameServiceEntity frameService, Map<String, String> globalVariables) {
        String serviceConfig = frameService.getServiceConfig();
        if (StringUtils.isNotBlank(serviceConfig) && serviceConfig.stripLeading().startsWith("[")) {
            String resolved = PlaceholderUtils.replacePlaceholders(
                    serviceConfig, globalVariables, Constants.REGEX_VARIABLE);
            return applyDefaultValues(JSONArray.parseArray(resolved, ServiceConfig.class));
        }

        JSONObject serviceDdl = JSON.parseObject(frameService.getServiceJson());
        JSONArray parameters = serviceDdl.getJSONArray("parameters");
        if (parameters == null) {
            throw new ServiceException(500, "服务 DDL 缺少 parameters 配置");
        }
        String resolved = PlaceholderUtils.replacePlaceholders(
                parameters.toJSONString(), globalVariables, Constants.REGEX_VARIABLE);
        return applyDefaultValues(JSONArray.parseArray(resolved, ServiceConfig.class));
    }

    static List<ServiceConfig> applyDefaultValues(List<ServiceConfig> configs) {
        for (ServiceConfig config : configs) {
            if (config.getValue() == null && config.getDefaultValue() != null) {
                config.setValue(config.getDefaultValue());
            }
        }
        return configs;
    }

    @Override
    public void saveServiceConfig(Integer clusterId, String serviceName, List<ServiceConfig> list, Integer roleGroupId) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        ServiceConfigMap.put(clusterInfo.getClusterCode() + Constants.UNDERLINE + serviceName + Constants.CONFIG, list);

        // handler config
        ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(serviceName);
        if (Objects.nonNull(serviceRoleHandler)) {
            serviceRoleHandler.handlerConfig(clusterId, list, getServiceName(clusterInfo.getClusterFrame(), serviceName));
        }

        // 添加配置数据到全局变量
        for (ServiceConfig serviceConfig : list) {
            if (Boolean.TRUE.equals(serviceConfig.getRegister())) {
                ServiceConfigUtils.generateClusterVariable(clusterId, serviceName,
                        serviceConfig.getName(), registeredVariableValue(serviceConfig));
            }
        }

        // 构建configFileMap
        Map<String, ServiceConfig> map = new HashMap<>();
        for (ServiceConfig serviceConfig : list) {
            map.put(serviceConfig.getName(), serviceConfig);
        }
        Map<Generators, List<ServiceConfig>> configFileMap = buildConfigFileMap(serviceName, clusterInfo, map);

        ClusterServiceInstanceEntity serviceInstanceEntity = serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterId, serviceName);
        ClusterServiceInstanceRoleGroup serviceInstanceRoleGroup = null;
        FrameServiceEntity frameServiceEntity = frameService.getServiceByFrameCodeAndServiceName(clusterInfo.getClusterFrame(), serviceName);
        if (serviceInstanceEntity == null) {
            serviceInstanceEntity = saveServiceInstance(clusterId, serviceName, frameServiceEntity);
            serviceInstanceRoleGroup = saveDefaultServiceInstanceRoleGroup(clusterId, serviceName, serviceInstanceEntity);
        } else if (roleGroupId == null) {
            serviceInstanceRoleGroup = saveNewRoleGroup(serviceInstanceEntity);
        } else if (roleGroupId < 0) {
            serviceInstanceRoleGroup = roleGroupService.getDefaultRoleGroupByServiceInstanceId(serviceInstanceEntity.getId());
        } else {
            serviceInstanceRoleGroup = roleGroupService.getById(roleGroupId);
        }
        Integer usedRoleGroupId = serviceInstanceRoleGroup.getId();
        CacheUtils.put("UseRoleGroup_" + serviceInstanceEntity.getId(), usedRoleGroupId);

        ClusterServiceRoleGroupConfig config = groupConfigService.getConfigByRoleGroupId(serviceInstanceRoleGroup.getId());
        ClusterServiceRoleGroupConfig newConfig = new ClusterServiceRoleGroupConfig();
        newConfig.setRoleGroupId(serviceInstanceRoleGroup.getId());
        newConfig.setClusterId(clusterId);
        newConfig.setCreateTime(new Date());
        newConfig.setUpdateTime(new Date());
        newConfig.setServiceName(serviceName);
        buildConfig(list, configFileMap, newConfig);
        if (config == null) {
            newConfig.setConfigVersion(1);
        } else {
            newConfig.setConfigVersion(config.getConfigVersion() + 1);
            roleInstanceService.updateToNeedRestart(usedRoleGroupId);
            roleGroupService.updateToNeedRestart(usedRoleGroupId);
            serviceInstanceEntity.setNeedRestart(NeedRestart.YES);
        }
        groupConfigService.save(newConfig);
        // update service instance
        serviceInstanceEntity.setUpdateTime(new Date());
        serviceInstanceEntity.setLabel(frameServiceEntity.getLabel());
        serviceInstanceService.updateById(serviceInstanceEntity);
    }

    static String registeredVariableValue(ServiceConfig config) {
        if (config.getValue() == null) {
            throw new ServiceException(500, "注册配置项缺少有效值: " + config.getName());
        }
        return String.valueOf(config.getValue());
    }

    private String getServiceName(String frameCode, String serviceRoleName) {
        return frameServiceRoleService.getServiceName(frameCode, serviceRoleName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void saveServiceRoleHostMapping(Integer clusterId, List<ServiceRoleHostMapping> list) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        String hostMapKey = clusterInfo.getClusterCode() + Constants.UNDERLINE + Constants.SERVICE_ROLE_HOST_MAPPING;
        Map<String, List<String>> map = new HashMap<>();
        if (CacheUtils.containsKey(hostMapKey)) {
            map = (Map<String, List<String>>) CacheUtils.get(hostMapKey);
        }

        for (ServiceRoleHostMapping serviceRoleHostMapping : list) {
            serviceValidation(serviceRoleHostMapping);

            List<String> hosts = serviceRoleHostMapping.getHosts();

            map.put(serviceRoleHostMapping.getServiceRole(), hosts);

            ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(serviceRoleHostMapping.getServiceRole());
            String serviceName = getServiceName(clusterInfo.getClusterFrame(), serviceRoleHostMapping.getServiceRole());

            if (!hosts.isEmpty()) {
                String serviceRole = serviceRoleHostMapping.getServiceRole();
                ServiceConfigUtils.generateClusterVariable(clusterId, serviceName,
                        String.format("%s.%s", serviceRole, GlobalVariables.HOST), String.join(",", hosts));
                ServiceConfigUtils.generateClusterVariable(clusterId, serviceName,
                        String.format("%s.%s", serviceRole, GlobalVariables.HOST_IP),
                        hosts.stream().map(HostUtils::getIp).collect(Collectors.joining(",")));
            }

            if (Objects.nonNull(serviceRoleHandler)) {
                serviceRoleHandler.handler(clusterId, hosts, serviceName);
            }
        }

        CacheUtils.put(
                clusterInfo.getClusterCode()
                        + Constants.UNDERLINE
                        + Constants.SERVICE_ROLE_HOST_MAPPING,
                map);
    }

    @Override
    public Result getServiceRoleDeployOverview(Integer clusterId) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        @SuppressWarnings("unchecked")
        HashMap<String, List<String>> map =
                (HashMap<String, List<String>>) CacheUtils.get(
                        clusterInfo.getClusterCode()
                                + Constants.UNDERLINE
                                + Constants.SERVICE_ROLE_HOST_MAPPING);
        return Result.success(map);
    }

    @Override
    public void downloadTemplate(String templateName, HttpServletResponse response) throws IOException {
        try {
            String url = NexusFacade.getRawRepoClient().getNexusRawObjectUrl(String.format("/template/%s", templateName));
            response.reset();
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment;filename=" + templateName.replace("/", "_"));
            NexusFacade.getCommonClient().download(url, response.getOutputStream());
        } finally {
            IOUtils.closeQuietly(response.getOutputStream());
        }

    }

    @Override
    public Result getServiceRoleHostMapping(Integer clusterId) {
        return null;
    }

    @Override
    public Result checkServiceDependency(Integer clusterId, List<Integer> serviceIds) {
        List<ClusterServiceInstanceEntity> serviceInstanceList = serviceInstanceService.listRunningServiceInstance(clusterId);
        Map<String, ClusterServiceInstanceEntity> instanceMap = serviceInstanceList.stream()
                .collect(
                        Collectors.toMap(
                                ClusterServiceInstanceEntity::getServiceName,
                                e -> e,
                                (v1, v2) -> v1));

        List<FrameServiceEntity> list = frameService.listServices(serviceIds);
        Map<String, FrameServiceEntity> serviceMap =
                list.stream()
                        .collect(
                                Collectors.toMap(
                                        FrameServiceEntity::getServiceName,
                                        e -> e,
                                        (v1, v2) -> v1));
        for (FrameServiceEntity frameServiceEntity : list) {
            for (String dependService : frameServiceEntity.getDependencies().split(",")) {
                if (StringUtils.isNotBlank(dependService)
                        && !instanceMap.containsKey(dependService)
                        && !serviceMap.containsKey(dependService)) {
                    return Result.error(
                            frameServiceEntity.getServiceName()
                                    + " install depends on "
                                    + dependService
                                    + ",please make sure that you have selected it or that "
                                    + dependService
                                    + " is normal and running");
                }
            }
        }
        return Result.success();
    }

    private ClusterServiceInstanceRoleGroup saveNewRoleGroup(
                                                             ClusterServiceInstanceEntity serviceInstanceEntity) {
        long count =
                roleGroupService.count(
                        new QueryWrapper<ClusterServiceInstanceRoleGroup>()
                                .eq(Constants.ROLE_GROUP_TYPE, "auto")
                                .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceEntity.getId()));
        ClusterServiceInstanceRoleGroup roleGroup = new ClusterServiceInstanceRoleGroup();
        long num = count + 1;
        roleGroup.setRoleGroupName("RoleGroup" + num);
        roleGroup.setServiceInstanceId(serviceInstanceEntity.getId());
        roleGroup.setServiceName(serviceInstanceEntity.getServiceName());
        roleGroup.setClusterId(serviceInstanceEntity.getClusterId());
        roleGroup.setRoleGroupType("auto");
        roleGroupService.save(roleGroup);
        return roleGroup;
    }

    private ClusterServiceInstanceRoleGroup saveDefaultServiceInstanceRoleGroup(
                                                                                Integer clusterId,
                                                                                String serviceName,
                                                                                ClusterServiceInstanceEntity serviceInstanceEntity) {
        ClusterServiceInstanceRoleGroup clusterServiceInstanceRoleGroup =
                new ClusterServiceInstanceRoleGroup();
        clusterServiceInstanceRoleGroup.setServiceInstanceId(serviceInstanceEntity.getId());
        clusterServiceInstanceRoleGroup.setClusterId(clusterId);
        clusterServiceInstanceRoleGroup.setRoleGroupName("默认角色组");
        clusterServiceInstanceRoleGroup.setServiceName(serviceName);
        clusterServiceInstanceRoleGroup.setRoleGroupType("default");
        roleGroupService.save(clusterServiceInstanceRoleGroup);
        return clusterServiceInstanceRoleGroup;
    }

    private ClusterServiceInstanceEntity saveServiceInstance(
                                                             Integer clusterId, String serviceName,
                                                             FrameServiceEntity frameServiceEntity) {
        ClusterServiceInstanceEntity serviceInstanceEntity;
        serviceInstanceEntity = new ClusterServiceInstanceEntity();
        serviceInstanceEntity.setClusterId(clusterId);
        serviceInstanceEntity.setServiceState(ServiceState.WAIT_INSTALL);
        serviceInstanceEntity.setServiceName(serviceName);
        serviceInstanceEntity.setLabel(frameServiceEntity.getLabel());
        serviceInstanceEntity.setCreateTime(new Date());
        serviceInstanceEntity.setUpdateTime(new Date());
        serviceInstanceEntity.setNeedRestart(NeedRestart.NO);
        serviceInstanceEntity.setFrameServiceId(frameServiceEntity.getId());
        serviceInstanceEntity.setSortNum(frameServiceEntity.getSortNum());
        serviceInstanceService.save(serviceInstanceEntity);
        return serviceInstanceEntity;
    }

    private Map<Generators, List<ServiceConfig>> buildConfigFileMap(
                                                                    String serviceName,
                                                                    ClusterInfoEntity clusterInfo,
                                                                    Map<String, ServiceConfig> map) {
        FrameServiceEntity frameService = this.frameService.getServiceByFrameCodeAndServiceName(clusterInfo.getClusterFrame(), serviceName);
        String cacheKey = clusterInfo.getClusterFrame()
                + Constants.UNDERLINE
                + serviceName
                + Constants.CONFIG_FILE;
        if (ServiceConfigFileMap.exists(cacheKey)) {
            return mergeConfigFileMap(ServiceConfigFileMap.get(cacheKey), map);
        }

        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        if (StringUtils.isBlank(frameService.getConfigFileJson())) {
            return configFileMap;
        }

        Map<JSONObject, JSONArray> configMap = JSONObject.parseObject(frameService.getConfigFileJson(), Map.class);
        configMap.forEach((fileJson, configJson) -> {
            Generators generators = fileJson.toJavaObject(Generators.class);
            List<ServiceConfig> serviceConfigs = configJson.toJavaList(ServiceConfig.class);
            for (ServiceConfig config : serviceConfigs) {
                if (map.containsKey(config.getName())) {
                    ServiceConfig newConfig = map.get(config.getName());
                    config.setValue(map.get(config.getName()).getValue());
                    config.setHidden(newConfig.isHidden());
                    config.setRequired(newConfig.isRequired());
                    config.setEnabled(newConfig.isEnabled());
                }
            }
            configFileMap.put(generators, serviceConfigs);
        });
        return configFileMap;
    }

    static Map<Generators, List<ServiceConfig>> mergeConfigFileMap(
                                                                   Map<Generators, List<ServiceConfig>> template,
                                                                   Map<String, ServiceConfig> currentConfigs) {
        Map<Generators, List<ServiceConfig>> result = new HashMap<>();
        template.forEach((generator, templateConfigs) -> {
            List<ServiceConfig> configs = templateConfigs.stream()
                    .map(templateConfig -> currentConfigs.getOrDefault(templateConfig.getName(), templateConfig))
                    .toList();
            result.put(generator, configs);
        });
        return result;
    }

    private void buildConfig(
                             List<ServiceConfig> list,
                             Map<Generators, List<ServiceConfig>> configFileMap,
                             ClusterServiceRoleGroupConfig roleGroupConfig) {
        String configJson = JSONObject.toJSONString(list);
        String configFileJson = ServiceConfigUtils.serializeConfigFileMap(configFileMap);
        roleGroupConfig.setConfigJson(configJson);
        roleGroupConfig.setConfigJsonMd5(SecureUtil.md5(configJson));
        roleGroupConfig.setConfigFileJson(configFileJson);
        roleGroupConfig.setConfigFileJsonMd5(SecureUtil.md5(configFileJson));
    }

    private void serviceValidation(ServiceRoleHostMapping serviceRoleHostMapping) {
        String serviceRole = serviceRoleHostMapping.getServiceRole();
        List<String> hosts = serviceRoleHostMapping.getHosts();

        if ("JournalNode".equals(serviceRole) && hosts.size() != 3) {
            throw new ServiceException(Status.THREE_JOURNALNODE_DEPLOYMENTS_REQUIRED.getMsg());
        }
        if ("NameNode".equals(serviceRole) && hosts.size() != 2) {
            throw new ServiceException(Status.TWO_NAMENODES_NEED_TO_BE_DEPLOYED.getMsg());
        }
        if ("ZKFC".equals(serviceRole) && hosts.size() != 2) {
            throw new ServiceException(Status.TWO_ZKFC_DEVICES_ARE_REQUIRED.getMsg());
        }
        if ("ResourceManager".equals(serviceRole) && hosts.size() != 2) {
            throw new ServiceException(Status.TWO_RESOURCEMANAGER_ARE_DEPLOYED.getMsg());
        }
        if ("ZkServer".equals(serviceRole) && (hosts.size() & 1) == 0) {
            throw new ServiceException(Status.ODD_NUMBER_ARE_REQUIRED_FOR_ZKSERVER.getMsg());
        }
        if ("DorisFE".equals(serviceRole) && (hosts.size() & 1) == 0) {
            throw new ServiceException(Status.ODD_NUMBER_ARE_REQUIRED_FOR_DORISFE.getMsg());
        }
        if ("KyuubiServer".equals(serviceRole) && hosts.size() != 2) {
            throw new ServiceException(Status.TWO_KYUUBISERVERS_NEED_TO_BE_DEPLOYED.getMsg());
        }
        if ("Etcd".equals(serviceRole) && hosts.size() != 3) {
            throw new ServiceException(Status.THREE_ETCD_NEED_TO_BE_DEPLOYED.getMsg());
        }
    }

    private List<ServiceConfig> listServiceConfigByServiceInstance(
                                                                   ClusterServiceInstanceEntity serviceInstance) {
        ClusterServiceInstanceRoleGroup roleGroup =
                roleGroupService.getDefaultRoleGroupByServiceInstanceId(serviceInstance.getId());
        ClusterServiceRoleGroupConfig config =
                groupConfigService.getConfigByRoleGroupId(roleGroup.getId());
        return JSONArray.parseArray(config.getConfigJson(), ServiceConfig.class);
    }
}
