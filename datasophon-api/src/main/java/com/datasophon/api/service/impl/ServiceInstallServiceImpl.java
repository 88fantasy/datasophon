/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.datasophon.api.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.load.GlobalVariables;
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
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.strategy.ServiceRoleStrategy;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.IOUtils;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceState;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("serviceInstallService")
@Transactional
public class ServiceInstallServiceImpl implements ServiceInstallService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstallServiceImpl.class);

    private static final List<String> MUST_AT_SAME_NODE_BASIC_SERVICE =
            Arrays.asList("Grafana", "AlertManager", "Prometheus");

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    FrameInfoService frameInfoService;

    @Autowired
    FrameServiceService frameService;

    @Autowired
    FrameServiceRoleService frameServiceRoleService;

    @Autowired
    ClusterServiceCommandService commandService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterVariableService variableService;

    @Autowired
    private ClusterHostService hostService;

    @Autowired
    private ClusterServiceInstanceRoleGroupService roleGroupService;

    @Autowired
    private ClusterServiceRoleGroupConfigService groupConfigService;

    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;

    public static final String PROMETHEUS = "prometheus";

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
            String serviceConfig = frameService.getServiceConfig();
            serviceConfig =
                    PlaceholderUtils.replacePlaceholders(
                            serviceConfig, globalVariables, Constants.REGEX_VARIABLE);

            list = JSONArray.parseArray(serviceConfig, ServiceConfig.class);
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
        String serviceConfig = PlaceholderUtils.replacePlaceholders(frameService.getServiceConfig(), globalVariables, Constants.REGEX_VARIABLE);
        List<ServiceConfig> list = JSONArray.parseArray(serviceConfig, ServiceConfig.class);

        ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(serviceName);
        if (Objects.nonNull(serviceRoleHandler)) {
            serviceRoleHandler.getConfig(clusterId, list);
        }

        return list;
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
            String variableName = serviceConfig.getName();
            String variableValue = String.valueOf(serviceConfig.getValue());
            // add to global variable
            if (Boolean.TRUE.equals(serviceConfig.getRegister())) {
                ProcessUtils.generateClusterVariable(clusterId, serviceName, variableName, variableValue);
            }
        }


//        构建configFileMap
        Map<String, ServiceConfig> map = new HashMap<>();
        for (ServiceConfig serviceConfig : list) {
            map.put(serviceConfig.getName(), serviceConfig);
        }
        Map<Generators, List<ServiceConfig>> configFileMap = buildConfigFileMap(serviceName, clusterInfo, map);
        if (PROMETHEUS.equalsIgnoreCase(serviceName)) {
            logger.info("add worker and node to prometheus");
            // add host node to prometheus
            addHostNodeToPrometheus(clusterId, configFileMap);
        }


        ClusterServiceInstanceEntity serviceInstanceEntity = serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterId, serviceName);
        ClusterServiceInstanceRoleGroup serviceInstanceRoleGroup = null;
        FrameServiceEntity frameServiceEntity = frameService.getServiceByFrameCodeAndServiceName(clusterInfo.getClusterFrame(), serviceName);
        if (serviceInstanceEntity == null) {
            serviceInstanceEntity = saveServiceInstance(clusterId, serviceName, frameServiceEntity);
            serviceInstanceRoleGroup = saveDefaultServiceInstanceRoleGroup(clusterId, serviceName, serviceInstanceEntity);
        } else if (roleGroupId == null){
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


    private String getServiceName(String frameCode, String serviceRoleName) {
       return frameServiceRoleService.getServiceName(frameCode, serviceRoleName);
    }

    @Override
    public void saveServiceRoleHostMapping(Integer clusterId, List<ServiceRoleHostMapping> list) {
        checkOnSameNode(clusterId, list);

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
                ProcessUtils.generateClusterVariable(clusterId, serviceName,
                        String.format("%s.%s", serviceRole, GlobalVariables.HOST), String.join(",", hosts));
                ProcessUtils.generateClusterVariable(clusterId, serviceName,
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
        HashMap<String, List<String>> map =
                (HashMap<String, List<String>>) CacheUtils.get(
                        clusterInfo.getClusterCode()
                        + Constants.UNDERLINE
                        + Constants.SERVICE_ROLE_HOST_MAPPING);
        return Result.success(map);
    }



    @Override
    public void downloadResource(String frameCode, String serviceRoleName, String resource,
                                 HttpServletResponse response) throws Exception {
        FrameServiceRoleEntity entity = frameServiceRoleService.getServiceRoleByFrameCodeAndServiceRoleName(frameCode, serviceRoleName);
        ServiceRoleInfo roleInfo = JSONObject.parseObject(entity.getServiceRoleJson(), ServiceRoleInfo.class);

        ServiceMetaItem item = new ServiceMetaItem();
        item.setServiceName(roleInfo.getServiceName());
        item.setType(MetaStorage.VOS_DDL);
        item.setFramework(frameCode);
        MetaStorage metaStorage = StorageUtils.getMetaStorage();

        int idx = resource.lastIndexOf("/");
        String fileName = idx == -1 ? resource : resource.substring(idx + 1);
        try {
            metaStorage.downResource(item, resource, ()-> {
                response.reset();
                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
                return response.getOutputStream();
            });
        } catch (FileNotFoundException ex) {
            response.setStatus(404);
        }
    }

    @Override
    public void downloadTemplate(String templateName, HttpServletResponse response) throws IOException {
        try {
            String url = NexusFileUtils.getNexusRawObjectUrl(String.format("/template/%s", templateName));
            response.reset();
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment;filename=" + templateName.replaceAll("/", "_"));
            NexusFileUtils.downStream(url, response.getOutputStream());
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
                                        (v1, v2) -> v1)
                        );

        List<FrameServiceEntity> list = frameService.listServices(serviceIds);
        Map<String, FrameServiceEntity> serviceMap =
                list.stream()
                        .collect(
                                Collectors.toMap(
                                        FrameServiceEntity::getServiceName,
                                        e -> e,
                                        (v1, v2) -> v1));
        if (!instanceMap.containsKey("ALERTMANAGER") && !serviceMap.containsKey("ALERTMANAGER")) {
            return Result.error(
                    "service install depends on alertmanager ,please make sure you have selected it or that alertmanager is normal and running");
        }
        if (!instanceMap.containsKey("GRAFANA") && !serviceMap.containsKey("GRAFANA")) {
            return Result.error(
                    "service install depends on grafana ,please make sure you have selected it or that grafana is normal and running");
        }
        if (!instanceMap.containsKey("PROMETHEUS") && !serviceMap.containsKey("PROMETHEUS")) {
            return Result.error(
                    "service install depends on prometheus ,please make sure you have selected it or that prometheus is normal and running");
        }

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

    private void addHostNodeToPrometheus(
            Integer clusterId, Map<Generators, List<ServiceConfig>> configFileMap) {
        List<ClusterHostDO> hostList = hostService.list(new QueryWrapper<ClusterHostDO>().eq(Constants.MANAGED, 1).eq(Constants.CLUSTER_ID, clusterId));
        Generators workerGenerators = new Generators();
        workerGenerators.setFilename("worker.json");
        workerGenerators.setOutputDirectory("configs");
        workerGenerators.setConfigFormat("custom");
        workerGenerators.setTemplateName("scrape.ftl");

        Generators nodeGenerators = new Generators();
        nodeGenerators.setFilename("linux.json");
        nodeGenerators.setOutputDirectory("configs");
        nodeGenerators.setConfigFormat("custom");
        nodeGenerators.setTemplateName("scrape.ftl");
        ArrayList<ServiceConfig> workerServiceConfigs = new ArrayList<>();
        ArrayList<ServiceConfig> nodeServiceConfigs = new ArrayList<>();
        for (ClusterHostDO clusterHostDO : hostList) {
            ServiceConfig serviceConfig = new ServiceConfig();
            serviceConfig.setName("worker_" + clusterHostDO.getHostname());
            serviceConfig.setValue(clusterHostDO.getHostname() + ":8585");
            serviceConfig.setRequired(true);
            serviceConfig.setEnabled(true);
            workerServiceConfigs.add(serviceConfig);

            ServiceConfig nodeServiceConfig = new ServiceConfig();
            nodeServiceConfig.setName("node_" + clusterHostDO.getHostname());
            nodeServiceConfig.setValue(clusterHostDO.getHostname() + ":9100");
            nodeServiceConfig.setRequired(true);
            nodeServiceConfig.setEnabled(true);
            nodeServiceConfigs.add(nodeServiceConfig);
        }
        configFileMap.put(workerGenerators, workerServiceConfigs);
        configFileMap.put(nodeGenerators, nodeServiceConfigs);
    }

    private Map<Generators, List<ServiceConfig>> buildConfigFileMap(
            String serviceName,
            ClusterInfoEntity clusterInfo,
            Map<String, ServiceConfig> map) {

        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        FrameServiceEntity frameService = this.frameService.getServiceByFrameCodeAndServiceName(clusterInfo.getClusterFrame(), serviceName);
        if (StringUtils.isBlank(frameService.getConfigFileJson())) {
            return configFileMap;
        }

        Map<JSONObject, JSONArray> configMap = JSONObject.parseObject(frameService.getConfigFileJson(), Map.class);
        configMap.forEach((fileJson, configJson)-> {
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


    private void buildConfig(
            List<ServiceConfig> list,
            Map<Generators, List<ServiceConfig>> configFileMap,
            ClusterServiceRoleGroupConfig roleGroupConfig) {
        String configJson = JSONObject.toJSONString(list);
        String configFileJson = JSONObject.toJSONString(configFileMap, SerializerFeature.DisableCircularReferenceDetect);
        roleGroupConfig.setConfigJson(configJson);
        roleGroupConfig.setConfigJsonMd5(SecureUtil.md5(configJson));
        roleGroupConfig.setConfigFileJson(configFileJson);
        roleGroupConfig.setConfigFileJsonMd5(SecureUtil.md5(configFileJson));
    }

    private void checkOnSameNode(Integer clusterId, List<ServiceRoleHostMapping> list) {
        Set<String> hostnameSet =
                list.stream()
                        .filter(s -> MUST_AT_SAME_NODE_BASIC_SERVICE.contains(s.getServiceRole()))
                        .map(ServiceRoleHostMapping::getHosts)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(hostnameSet)) {
            return;
        }

        Set<String> installedHostnameSet =
                roleInstanceService.lambdaQuery()
                        .eq(ClusterServiceRoleInstanceEntity::getClusterId, clusterId)
                        .in(
                                ClusterServiceRoleInstanceEntity::getServiceName,
                                MUST_AT_SAME_NODE_BASIC_SERVICE)
                        .list().stream()
                        .map(ClusterServiceRoleInstanceEntity::getHostname)
                        .collect(Collectors.toSet());
        hostnameSet.addAll(installedHostnameSet);

        if (hostnameSet.size() > 1) {
            throw new ServiceException(Status.BASIC_SERVICE_SELECT_MOST_ONE_HOST.getMsg());
        }
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
