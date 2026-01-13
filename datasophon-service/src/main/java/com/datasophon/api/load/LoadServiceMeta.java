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

package com.datasophon.api.load;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ConfigWriter;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.datasophon.common.Constants.FRAMEWORK_TPL;
import static com.datasophon.common.Constants.META_PATH;

@Component
public class LoadServiceMeta implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LoadServiceMeta.class);

    @Autowired
    private PropertyResolver propertyResolver;

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private FrameInfoService frameInfoService;

    @Autowired
    private FrameServiceRoleService roleService;

    @Autowired
    private ClusterVariableService variableService;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterServiceInstanceRoleGroupService roleGroupService;

    @Autowired
    private ClusterServiceRoleGroupConfigService roleGroupConfigService;

    private static final String HDFS = "HDFS";

    private static final String HADOOP = "HADOOP";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        File[] ddps = FileUtil.ls(META_PATH);
        // load global variable, 加载 frame
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        loadGlobalVariables(clusters);

        for (File path : ddps) {
            List<File> files = FileUtil.loopFiles(path);
            String frameCode = path.getName();
            FrameInfoEntity frameInfo = frameInfoService.saveFrameIfAbsent(frameCode);
            // analysis file
            for (File file : files) {
                if (file.getName().endsWith(Constants.JSON)) {
                    String serviceName = file.getParentFile().getName();
                    String serviceDdl = FileReader.create(file).readString();
                    try {
                        parseServiceDdl(frameCode, clusters, frameInfo, serviceName, serviceDdl);
                    } catch (Exception e) {
                        logger.error("invalid service ddl file: " + serviceName, e);
                    }
                }
            }
        }
    }


    public void initFramework(FrameInfoEntity entity) {
        Objects.requireNonNull(entity);

        String frameCode = entity.getFrameCode();
        File[] ddps = FileUtil.ls(META_PATH);
        File target = null;
        if (ddps != null) {
            for (File ddp : ddps) {
                if (ddp.getName().equals(frameCode)) {
                    target = ddp;
                    break;
                }
            }
        }
        if (target == null) {
            target = new File(FileUtil.file(META_PATH), frameCode);
            FileUtil.mkdir(target);
        }

        logger.info("使用框架模板{}初始化框架{}", FileUtil.file(FRAMEWORK_TPL).getAbsolutePath(), frameCode);

        File[] srvCmps = FileUtil.ls(FRAMEWORK_TPL);
        List<String> installingCmp = new ArrayList<>();
        for (File srvCmp : srvCmps) {
            File targetDdp = new File(target, srvCmp.getName());
            if (targetDdp.exists()) {
                continue;
            }
            logger.info("add service component {} to framework {}", srvCmp.getName(), frameCode);
            FileUtil.copy(srvCmp, target, false);
            installingCmp.add(srvCmp.getName());
        }
        if (!installingCmp.isEmpty()) {
            List<ClusterInfoEntity> clusters = clusterInfoService.list();
            for (String cmp : installingCmp) {
                List<File> files = FileUtil.loopFiles(new File(target, cmp));
                // analysis file
                for (File file : files) {
                    if (file.getName().endsWith(Constants.JSON)) {
                        String serviceName = file.getParentFile().getName();
                        String serviceDdl = FileReader.create(file).readString();
                        try {
                            parseServiceDdl(frameCode, clusters, entity, serviceName, serviceDdl);
                        } catch (Exception e) {
                            logger.error("invalid service ddl file: {}/{}", frameCode, serviceName, e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析 DDL 并存储到 frame 库
     *
     * @param frameCode
     * @param clusters
     * @param frameInfo
     * @param serviceName
     * @param serviceDdl
     */
    public void parseServiceDdl(final String frameCode,
                                List<ClusterInfoEntity> clusters,
                                FrameInfoEntity frameInfo,
                                final String serviceName,
                                final String serviceDdl) {
        ServiceInfo serviceInfo = JSONObject.parseObject(serviceDdl, ServiceInfo.class);
        String packageName = serviceInfo.getPackageName();
        String decompressPackageName = serviceInfo.getDecompressPackageName();
        // 新增架构判断, 兼容旧版本
        Map<String, ArchInfo> arch = serviceInfo.getArch();
        if (CollUtil.isEmpty(arch)) {
            serviceInfo.setArch(getArchInfo(packageName, decompressPackageName));
        }

        String serviceInfoMd5 = SecureUtil.md5(serviceDdl);

        // save service config
        List<ServiceConfig> allParameters = serviceInfo.getParameters();
        Map<String, ServiceConfig> map = allParameters.stream().collect(
                Collectors.toMap(
                        ServiceConfig::getName,
                        serviceConfig -> serviceConfig,
                        (v1, v2) -> v1));
        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();

        buildConfigFileMap(serviceInfo, map, configFileMap);

        PackageUtils.putServicePackageName(frameCode, serviceName, decompressPackageName);

        putServiceHomeToVariable(frameCode, clusters, serviceName, serviceInfo.getDecompressPackageName());
        // save service and service config
        FrameServiceEntity serviceEntity =
                saveFrameService(
                        frameCode,
                        frameInfo,
                        serviceName,
                        serviceDdl,
                        serviceInfo,
                        serviceInfoMd5,
                        allParameters,
                        configFileMap);
        // save frame service role
        saveFrameServiceRole(frameCode, serviceName, serviceInfo, serviceEntity);
    }

    /**
     * @deprecated
     * 解决完HADOOP_HOME后，可以去掉该方法的调用
     */
    @Deprecated
    private void putServiceHomeToVariable(String frameCode,
                                          List<ClusterInfoEntity> clusters, String serviceName,
                                          String decompressPackageName) {
        for (ClusterInfoEntity cluster : clusters) {
            Integer clusterId = cluster.getId();
            if (cluster.getClusterFrame().equals(frameCode)) {
                if (HDFS.equals(serviceName)) {
                    serviceName = HADOOP;
                    GlobalVariables.putValue(clusterId, serviceName + "_HOME", Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
                }
            }
        }
    }

    private void saveFrameServiceRole(
            String frameCode,
            String serviceName,
            ServiceInfo serviceInfo,
            FrameServiceEntity serviceEntity) {
        List<ServiceRoleInfo> serviceRoles = serviceInfo.getRoles();

        for (ServiceRoleInfo serviceRole : serviceRoles) {
            serviceRole.setParentName(serviceName);
            String key =
                    frameCode
                    + Constants.UNDERLINE
                    + serviceInfo.getName()
                    + Constants.UNDERLINE
                    + serviceRole.getName();
            logger.info(
                    "put {} {} {} service role info into cache",
                    frameCode,
                    serviceName,
                    serviceRole.getName());
            if (StringUtils.isNotBlank(serviceRole.getJmxPort())) {
                logger.info(
                        "{} jmx port is :{} and the jmx key is: {}",
                        serviceRole.getName(),
                        serviceRole.getJmxPort(),
                        key);
                ServiceRoleJmxMap.put(key, serviceRole.getJmxPort());
            }
            ServiceRoleMap.put(key, serviceRole);
            String serviceRoleJson = JSONObject.toJSONString(serviceRole);
            String serviceRoleJsonMd5 = SecureUtil.md5(serviceRoleJson);
            // 持久化服务角色元信息至数据库
            FrameServiceRoleEntity role =
                    roleService.getServiceRoleByServiceIdAndServiceRoleName(
                            serviceEntity.getId(), serviceRole.getName());
            if (Objects.isNull(role)) {
                role = new FrameServiceRoleEntity();
                buildFrameServiceRole(
                        frameCode,
                        serviceEntity,
                        serviceRole,
                        serviceRoleJson,
                        serviceRoleJsonMd5,
                        role);
                roleService.save(role);
            } else if (!role.getServiceRoleJsonMd5().equals(serviceRoleJsonMd5)) {
                buildFrameServiceRole(
                        frameCode,
                        serviceEntity,
                        serviceRole,
                        serviceRoleJson,
                        serviceRoleJsonMd5,
                        role);
                roleService.updateById(role);
            }
        }
        logger.info("put {} {} service info into cache", frameCode, serviceName);
        ServiceInfoMap.put(frameCode + Constants.UNDERLINE + serviceName, serviceInfo);
    }

    private FrameServiceEntity saveFrameService(
            String frameCode,
            FrameInfoEntity frameInfo,
            String serviceName,
            String serviceDdl,
            ServiceInfo serviceInfo,
            String serviceInfoMd5,
            List<ServiceConfig> allParameters,
            Map<Generators, List<ServiceConfig>> configFileMap) {
        FrameServiceEntity serviceEntity =
                frameServiceService.getServiceByFrameIdAndServiceName(
                        frameInfo.getId(), serviceName);
        if (Objects.isNull(serviceEntity)) {
            serviceEntity = new FrameServiceEntity();
            buildServiceEntity(
                    frameCode,
                    frameInfo.getId(),
                    serviceName,
                    serviceDdl,
                    serviceInfo,
                    serviceInfoMd5,
                    serviceEntity,
                    configFileMap,
                    serviceInfo.getDecompressPackageName());

            frameServiceService.save(serviceEntity);
        } else if (!serviceEntity.getServiceJsonMd5().equals(serviceInfoMd5)) {
            String configMapStr = JSONObject.toJSONString(configFileMap);
            String configFileMapStrMd5 = SecureUtil.md5(configMapStr);
            if (!configFileMapStrMd5.equals(serviceEntity.getConfigFileJsonMd5())) {
                // update config
                updateServiceInstanceConfig(
                        frameCode, serviceInfo.getName(), serviceInfo.getParameters());
            }
            buildServiceEntity(
                    frameCode,
                    frameInfo.getId(),
                    serviceName,
                    serviceDdl,
                    serviceInfo,
                    serviceInfoMd5,
                    serviceEntity,
                    configFileMap,
                    serviceInfo.getDecompressPackageName());
            frameServiceService.updateById(serviceEntity);
        }

        ServiceConfigMap.put(
                frameCode + Constants.UNDERLINE + serviceInfo.getName() + Constants.CONFIG,
                allParameters);
        ServiceConfigFileMap.put(
                frameCode + Constants.UNDERLINE + serviceInfo.getName() + Constants.CONFIG_FILE,
                configFileMap);

        return serviceEntity;
    }

    private void buildConfigFileMap(
            ServiceInfo serviceInfo,
            Map<String, ServiceConfig> map,
            Map<Generators, List<ServiceConfig>> configFileMap) {
        ConfigWriter configWriter = serviceInfo.getConfigWriter();
        List<Generators> generators = configWriter.getGenerators().stream().filter(g -> {
            if (StringUtils.isNotEmpty(g.getConditionalOnProperty())) {
                return propertyResolver.getProperty(g.getConditionalOnProperty(), boolean.class, false);
            }
            return true;
        }).collect(Collectors.toList());
        for (Generators generator : generators) {
            List<ServiceConfig> list = new ArrayList<>();
            List<String> includeParams = generator.getIncludeParams();
            for (String includeParam : includeParams) {
                if (map.containsKey(includeParam)) {
                    ServiceConfig serviceConfig = map.get(includeParam);
                    ServiceConfig newConfig = new ServiceConfig();
                    BeanUtils.copyProperties(serviceConfig, newConfig);
                    list.add(newConfig);
                }
            }
            if (configFileMap.containsKey(generator)) {
                configFileMap.get(generator).addAll(list);
            } else {
                configFileMap.put(generator, list);
            }
        }
    }

    public void loadGlobalVariables(List<ClusterInfoEntity> clusters) {
        if (CollUtil.isNotEmpty(clusters)) {
            for (ClusterInfoEntity cluster : clusters) {
                ConcurrentHashMap<String, String> globalVariables = GlobalVariables.genDefaultGlobalVariables();
                List<ClusterVariable> variables = variableService.list(Wrappers.<ClusterVariable>lambdaQuery()
                        .eq(ClusterVariable::getClusterId, cluster.getId()));
                for (ClusterVariable variable : variables) {
                    globalVariables.put(GlobalVariables.surroundKey(variable.getServiceName() + "." + variable.getVariableName()), variable.getVariableValue());
                }
                globalVariables.put(GlobalVariables.surroundKey(GlobalVariables.CLUSTER_CODE), cluster.getClusterFrame());
                GlobalVariables.put(cluster.getId(), globalVariables);
                ProcessUtils.createServiceActor(cluster);
            }
        }
    }

    private void updateServiceInstanceConfig(
            String frameCode, String serviceName, List<ServiceConfig> parameters) {
        // 查询frameCode相同的集群
        List<ClusterInfoEntity> clusters = clusterInfoService.getClusterByFrameCode(frameCode);
        // 查询集群的服务实例
        for (ClusterInfoEntity cluster : clusters) {
            ClusterServiceInstanceEntity serviceInstance =
                    serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(
                            cluster.getId(), serviceName);
            if (Objects.nonNull(serviceInstance)) {
                ClusterServiceRoleGroupConfig config =
                        roleGroupService.getRoleGroupConfigByServiceId(serviceInstance.getId());
                String configJson = config.getConfigJson();
                List<ServiceConfig> serviceConfigs =
                        JSONArray.parseArray(configJson, ServiceConfig.class);
                ProcessUtils.addAll(serviceConfigs, parameters);
                // 更新服务实例的配置
                config.setConfigJson(JSONObject.toJSONString(serviceConfigs));
                roleGroupConfigService.updateById(config);
            }
        }
    }

    private void buildFrameServiceRole(
            String frameCode,
            FrameServiceEntity serviceEntity,
            ServiceRoleInfo serviceRole,
            String serviceRoleJson,
            String serviceRoleJsonMd5,
            FrameServiceRoleEntity role) {
        role.setServiceId(serviceEntity.getId());
        role.setServiceRoleName(serviceRole.getName());
        role.setCardinality(serviceRole.getCardinality());
        role.setFrameCode(frameCode);
        role.setServiceRoleJson(serviceRoleJson);
        role.setServiceRoleType(CommonUtils.convertRoleType(serviceRole.getRoleType().getName()));
        role.setJmxPort(serviceRole.getJmxPort());
        role.setServiceRoleJsonMd5(serviceRoleJsonMd5);
        role.setLogFile(serviceRole.getLogFile());
    }

    private void buildServiceEntity(
            String frameCode,
            Integer frameInfoId,
            String serviceName,
            String serviceDdl,
            ServiceInfo serviceInfo,
            String serviceInfoMd5,
            FrameServiceEntity serviceEntity,
            Map<Generators, List<ServiceConfig>> configFileMap,
            String decompressPackageName) {
        serviceEntity.setServiceName(serviceName);
        serviceEntity.setLabel(serviceInfo.getLabel());
        serviceEntity.setFrameId(frameInfoId);
        serviceEntity.setServiceDesc(serviceInfo.getDescription());
        serviceEntity.setServiceVersion(serviceInfo.getVersion());
        serviceEntity.setPackageName(serviceInfo.getPackageName());
        serviceEntity.setArch(JSON.toJSONString(serviceInfo.getArch()));
        serviceEntity.setDependencies(StringUtils.join(serviceInfo.getDependencies(), ","));
        serviceEntity.setFrameCode(frameCode);
        serviceEntity.setServiceConfig(JSON.toJSONString(serviceInfo.getParameters()));
        serviceEntity.setServiceJson(serviceDdl);
        serviceEntity.setServiceJsonMd5(serviceInfoMd5);
        serviceEntity.setDecompressPackageName(decompressPackageName);
        serviceEntity.setConfigFileJson(JSONObject.toJSONString(configFileMap));
        serviceEntity.setConfigFileJsonMd5(SecureUtil.md5(serviceEntity.getConfigFileJson()));
        serviceEntity.setSortNum(serviceInfo.getSortNum());
    }

    public static Map<String, ArchInfo> getArchInfo(String packageName, String decompressPackageName) {
        Map<String, ArchInfo> arch = new ConcurrentHashMap<>();
        ArchInfo x86 = new ArchInfo();
        x86.setPackageName(packageName);
        arch.put(ArchType.X86_64.getArch(), x86);

        ArchInfo arm = new ArchInfo();
        arm.setPackageName(decompressPackageName + "-arm.tar.gz");
        arch.put(ArchType.AARCH64.getArch(), arm);
        return arch;
    }

    public static Map<String, ArchInfo> getArchInfo(FrameServiceEntity srv) {
        if (StringUtils.isNotEmpty(srv.getArch())) {
            return JSONObject.parseObject(srv.getArch(), new TypeReference<Map<String, ArchInfo>>() {
            });
        } else {
            return getArchInfo(srv.getPackageName(), srv.getDecompressPackageName());
        }
    }
}
