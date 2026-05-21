package com.datasophon.api.service.ddl.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigFileMap;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.load.ServiceRoleJmxMap;
import com.datasophon.api.load.ServiceRoleMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.ServicePkgNameUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.model.ConfigWriter;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.model.k8s.K8sServiceInfo;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.enums.NeedRestart;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.datasophon.common.Constants.FRAMEWORK_TPL;

/**
 * @author zhanghuangbin
 */
@Slf4j
@Service("ddlMetaService")
public class DdlMetaServiceImpl implements DdlMetaService {

    @Autowired
    private PropertyResolver propertyResolver;

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private FrameServiceRoleService roleService;

    @Autowired
    private FrameInfoService frameInfoService;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterServiceRoleInstanceService clusterServiceRoleInstanceService;

    @Autowired
    private ClusterServiceInstanceRoleGroupService roleGroupService;

    @Autowired
    private ClusterServiceRoleGroupConfigService roleGroupConfigService;

    @Autowired
    private Validator validator;

    @Autowired
    private FrameK8sServiceService frameK8sServiceService;


    private static final String HDFS = "HDFS";

    private static final String HADOOP = "HADOOP";


    /**
     * 内存数据无法回滚，在新的事务提交，防止外部事务回滚
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FrameInfoEntity initFramework(String frameCode) {
        FrameInfoEntity exists = frameInfoService.getByFrameCode(frameCode);
        if (exists != null) {
           return exists;
        }
        FrameInfoEntity entity = frameInfoService.saveFrameIfAbsent(frameCode);
        log.info("使用框架模板{}初始化框架{}", FileUtil.file(FRAMEWORK_TPL).getAbsolutePath(), entity.getFrameCode());

        File tplDir = FileUtil.file(FRAMEWORK_TPL);
        MetaStorage metaStorage = StorageUtils.getMetaStorage();
        try {
            metaStorage.moveToStorage(tplDir, relative-> "meta/" + entity.getFrameCode() + "/" + MetaStorage.VOS_DDL + "/" + relative);
        } catch (IOException e) {
            throw new BusinessException(String.format("初始化框架失败，%s", e.getMessage()), e);
        }
        List<String> installingCmp = Arrays.stream(Objects.requireNonNull(tplDir.listFiles(File::isDirectory)))
                .map(File::getName)
                .collect(Collectors.toList());
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        for (String cmp : installingCmp) {
            ServiceMetaItem item = new ServiceMetaItem();
            item.setServiceName(cmp);
            item.setType(MetaStorage.VOS_DDL);
            item.setFramework(entity.getFrameCode());
            try {
                loadServiceVosDdl(clusters, entity, cmp, metaStorage.getServiceDdL(item));
            } catch (Exception e) {
                log.error("invalid service ddl file: {}/{}", entity.getFrameCode(), cmp, e);
            }
        }
        return entity;
    }


    @Override
    public FrameServiceEntity loadServiceVosDdl(List<ClusterInfoEntity> clusters, FrameInfoEntity frameInfo, String serviceName, String serviceDdl) {
        ServiceInfo serviceInfo = JSONObject.parseObject(serviceDdl, new TypeReference<ServiceInfo>() {
        });

        if (StrUtil.isNotBlank(serviceName)) {
            if (!serviceName.equals(serviceInfo.getName())) {
                throw new IllegalStateException(String.format("服务名称%s与ddl定义的不一致", serviceName));
            }
        } else {
            serviceName = serviceInfo.getName();
        }
        Set<ConstraintViolation<ServiceInfo>>  errors = validator.validate(serviceInfo);
        if (!errors.isEmpty()) {
            List<String> errorList = new ArrayList<>();
            errors.forEach(e-> errorList.add(e.getMessage()));
            throw new IllegalStateException(String.format("服务%s的ddl文件不规范，存在错误:\n%s", serviceName, StrUtil.join(";", errorList)));
        }
//        @see ServiceInstallHandler#createLink
        if (StringUtils.lowerCase(serviceName).equals(serviceInfo.getDecompressPackageName())) {
            throw new IllegalStateException(String.format("服务名称%s不能和解压文件名%s一致(忽略大小写)。", serviceName, serviceInfo.getDecompressPackageName()));
        }

        if (Objects.isNull(serviceInfo.getArch())) {
            // 新增架构判断, 兼容旧版本
            serviceInfo.setArch(ServicePkgNameUtils.getDefaultArchInfo(serviceInfo.getPackageName(), serviceInfo.getDecompressPackageName()));
        }
        log.info("arch:{}", serviceInfo.getArch());


        // save service config
        List<ServiceConfig> allParameters = serviceInfo.getParameters();
        Map<String, ServiceConfig> map = allParameters.stream().collect(
                Collectors.toMap(
                        ServiceConfig::getName,
                        serviceConfig -> serviceConfig,
                        (v1, v2) -> v1));
        Map<Generators, List<ServiceConfig>> configFileMap = buildConfigFileMap(serviceInfo, map);
        PackageUtils.putServicePackageName(frameInfo.getFrameCode(), serviceName, serviceInfo.getDecompressPackageName());
        putServiceHomeToVariable(frameInfo.getFrameCode(), clusters, serviceName, serviceInfo.getDecompressPackageName());


        // save service and service config
        FrameServiceEntity serviceEntity = saveFrameService(frameInfo, serviceName, serviceDdl, serviceInfo, configFileMap);
        // save frame service role
        saveFrameServiceRole(frameInfo.getFrameCode(), serviceName, serviceInfo, serviceEntity);
        return serviceEntity;
    }




    /**
     * @deprecated 解决完HADOOP_HOME后，可以去掉该方法的调用
     */
    @Deprecated
    private void putServiceHomeToVariable(String frameCode, List<ClusterInfoEntity> clusters, String serviceName, String decompressPackageName) {
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


    private void saveFrameServiceRole(String frameCode, String serviceName, ServiceInfo serviceInfo, FrameServiceEntity serviceEntity) {
        List<ServiceRoleInfo> serviceRoles = serviceInfo.getRoles();

        for (int i = 0; i < serviceRoles.size(); i++) {
            ServiceRoleInfo serviceRole = serviceRoles.get(i);
            serviceRole.setParentName(serviceName);
            String key = String.format("%s_%s_%s", frameCode, serviceName, serviceRole.getName());
            log.info("put {} {} {} service role info into cache", frameCode, serviceName, serviceRole.getName());
            if (StringUtils.isNotBlank(serviceRole.getJmxPort())) {
                log.info("{} jmx port is :{} and the jmx key is: {}", serviceRole.getName(), serviceRole.getJmxPort(), key);
                ServiceRoleJmxMap.put(key, serviceRole.getJmxPort());
            }
            ServiceRoleMap.put(key, serviceRole);
            String serviceRoleJson = JSONObject.toJSONString(serviceRole);
            String serviceRoleJsonMd5 = SecureUtil.md5(serviceRoleJson);
            // 持久化服务角色元信息至数据库
            FrameServiceRoleEntity role = roleService.getServiceRoleByServiceIdAndServiceRoleName(serviceEntity.getId(), serviceRole.getName());
            if (role == null) {
                role = new FrameServiceRoleEntity();
            }
            role.setServiceId(serviceEntity.getId());
            role.setServiceRoleName(serviceRole.getName());
            role.setCardinality(serviceRole.getCardinality());
            role.setFrameCode(frameCode);
            role.setServiceRoleJson(serviceRoleJson);
            role.setServiceRoleType(CommonUtils.convertRoleType(serviceRole.getRoleType().getName()));
            role.setJmxPort(serviceRole.getJmxPort());
            role.setServiceRoleJsonMd5(serviceRoleJsonMd5);
            role.setLogFile(serviceRole.getLogFile());
            role.setSortNum(serviceRole.getSortNum());
            roleService.saveOrUpdate(role);
        }
        log.info("put {} {} service info into cache", frameCode, serviceName);
        ServiceInfoMap.put(frameCode + Constants.UNDERLINE + serviceName, serviceInfo);
    }

    private FrameServiceEntity saveFrameService(FrameInfoEntity frameInfo, String serviceName, String serviceDdl,
                                                ServiceInfo serviceInfo, Map<Generators, List<ServiceConfig>> configFileMap) {
        List<ServiceConfig> allParameters = serviceInfo.getParameters();
        String serviceInfoMd5 = SecureUtil.md5(serviceDdl);
        FrameServiceEntity serviceEntity = frameServiceService.getServiceByFrameIdAndServiceName(frameInfo.getId(), serviceName);
        if (Objects.isNull(serviceEntity)) {
            serviceEntity = new FrameServiceEntity();
            buildServiceEntity(frameInfo, serviceName, serviceDdl, serviceInfo, serviceInfoMd5, serviceEntity, configFileMap);
            frameServiceService.save(serviceEntity);
        } else {
            String configMapStr = JSONObject.toJSONString(configFileMap);
            String configFileMapStrMd5 = SecureUtil.md5(configMapStr);
            boolean configChange = !configFileMapStrMd5.equals(serviceEntity.getConfigFileJsonMd5());
            boolean needUpdate = configChange || !serviceEntity.getServiceJsonMd5().equals(serviceInfoMd5);
            if (needUpdate) {
                if (configChange) {
                    // update config
                    updateServiceInstanceConfig(frameInfo.getFrameCode(), serviceInfo.getName(), serviceInfo.getParameters());
                }
                buildServiceEntity(frameInfo, serviceName, serviceDdl, serviceInfo, serviceInfoMd5, serviceEntity, configFileMap);
                frameServiceService.updateById(serviceEntity);
            }
        }

        ServiceConfigMap.put(frameInfo.getFrameCode() + Constants.UNDERLINE + serviceInfo.getName() + Constants.CONFIG, allParameters);
        ServiceConfigFileMap.put(frameInfo.getFrameCode() + Constants.UNDERLINE + serviceInfo.getName() + Constants.CONFIG_FILE, configFileMap);

        return serviceEntity;
    }

    private Map<Generators, List<ServiceConfig>> buildConfigFileMap(ServiceInfo serviceInfo, Map<String, ServiceConfig> map) {
        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
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

        return configFileMap;
    }


    private void updateServiceInstanceConfig(String frameCode, String serviceName, List<ServiceConfig> parameters) {
        // 查询frameCode相同的集群
        List<ClusterInfoEntity> clusters = clusterInfoService.getClusterByFrameCode(frameCode);
        // 查询集群的服务实例
        for (ClusterInfoEntity cluster : clusters) {
            ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(cluster.getId(), serviceName);
            if (serviceInstance == null) {
                continue;
            }


            ClusterServiceRoleGroupConfig config = roleGroupService.getRoleGroupConfigByServiceId(serviceInstance.getId());
            updateServiceRoleGroupConfig(config, parameters);

            Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + serviceInstance.getId());
            if (roleGroupId != null) {
                ClusterServiceRoleGroupConfig cacheConfig = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
                if (cacheConfig != null && !config.getId().equals(cacheConfig.getId())) {
                    updateServiceRoleGroupConfig(config, parameters);
                }
            }

            clusterServiceRoleInstanceService.lambdaUpdate()
                    .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstance.getId())
                    .eq(ClusterServiceRoleInstanceEntity::getRoleGroupId, config.getRoleGroupId())
                    .set(ClusterServiceRoleInstanceEntity::getNeedRestart, NeedRestart.YES)
                    .update();
        }
    }


    private void updateServiceRoleGroupConfig(ClusterServiceRoleGroupConfig config, List<ServiceConfig> parameters) {
        String configJson = config.getConfigJson();
        List<ServiceConfig> serviceConfigs = JSONArray.parseArray(configJson, ServiceConfig.class);
        ProcessUtils.addAll(serviceConfigs, parameters);
        // 更新服务实例的配置
        config.setConfigJson(JSONObject.toJSONString(serviceConfigs));
        roleGroupConfigService.updateById(config);
    }

    private void buildServiceEntity(FrameInfoEntity frameInfo, String serviceName, String serviceDdl,
                                    ServiceInfo serviceInfo, String serviceInfoMd5, FrameServiceEntity serviceEntity,
                                    Map<Generators, List<ServiceConfig>> configFileMap) {
        serviceEntity.setServiceName(serviceName);
        serviceEntity.setLabel(serviceInfo.getLabel());
        serviceEntity.setFrameId(frameInfo.getId());
        serviceEntity.setServiceDesc(serviceInfo.getDescription());
        serviceEntity.setServiceVersion(serviceInfo.getVersion());
        serviceEntity.setPackageName(serviceInfo.getPackageName());
        serviceEntity.setArch(JSON.toJSONString(serviceInfo.getArch()));
        serviceEntity.setDependencies(StringUtils.join(serviceInfo.getDependencies(), ","));
        serviceEntity.setFrameCode(frameInfo.getFrameCode());
        serviceEntity.setServiceConfig(JSON.toJSONString(serviceInfo.getParameters()));
        serviceEntity.setServiceJson(serviceDdl);
        serviceEntity.setServiceJsonMd5(serviceInfoMd5);
        serviceEntity.setDecompressPackageName(serviceInfo.getDecompressPackageName());
        serviceEntity.setConfigFileJson(JSONObject.toJSONString(configFileMap));
        serviceEntity.setConfigFileJsonMd5(SecureUtil.md5(serviceEntity.getConfigFileJson()));
        serviceEntity.setSortNum(serviceInfo.getSortNum());
        serviceEntity.setType(serviceInfo.getType());
    }


    @Override
    public FrameK8sServiceEntity loadServiceK8sDdl(FrameInfoEntity frameInfo, String serviceName, String serviceDdl) {
        K8sServiceInfo serviceInfo = YamlUtils.parseYaml(serviceDdl, K8sServiceInfo.class);

        if (StrUtil.isNotBlank(serviceName)) {
            if (!serviceName.equals(serviceInfo.getName())) {
                throw new BusinessHintException(String.format("服务名称%s与ddl定义的不一致", serviceName));
            }
        } else {
            serviceName = serviceInfo.getName();
        }
        List<String> supportArtifacts = new ArrayList<>();
        if (serviceInfo.getArtifact() != null) {
            if (StrUtil.isNotBlank(serviceInfo.getArtifact().getHelm())) {
                supportArtifacts.add("helm");
            }
            if (StrUtil.isNotBlank(serviceInfo.getArtifact().getYaml())) {
                supportArtifacts.add("yaml");
            }
        }
        if (supportArtifacts.isEmpty()) {
            throw new BusinessHintException("服务%s的manifest文件不规范，artifact字段，至少支持一种部署方式");
        }

        Set<ConstraintViolation<K8sServiceInfo>>  errors = validator.validate(serviceInfo);
        if (!errors.isEmpty()) {
            List<String> errorList = new ArrayList<>();
            errors.forEach(e-> errorList.add(e.getMessage()));
            throw new BusinessHintException(String.format("服务%s的manifest文件不规范，存在错误:\n%s", serviceName, StrUtil.join(";", errorList)));
        }

        FrameK8sServiceEntity entity = frameK8sServiceService.lambdaQuery()
                .eq(FrameK8sServiceEntity::getFrameId, frameInfo.getId())
                .eq(FrameK8sServiceEntity::getServiceName, serviceName)
                .one();
        if (entity == null) {
            entity = new FrameK8sServiceEntity();
            entity.setServiceName(serviceName);
            entity.setFrameId(frameInfo.getId());
        }
        entity.setType(serviceInfo.getType());
        entity.setRuntime(serviceInfo.getRuntime());
        entity.setServiceDesc(serviceInfo.getDescription());
        entity.setDependencies(serviceInfo.getDependencies());
        entity.setArtifact(JSONObject.toJSONString(serviceInfo.getArtifact()));
        entity.setManifestJson(JSONObject.toJSONString(serviceInfo));
        entity.setSupportArtifacts(supportArtifacts);
        entity.setServiceVersion(serviceInfo.getVersion());
        frameK8sServiceService.saveOrUpdate(entity);

        return entity;
    }

    @Override
    public void updateServiceVosDdl(Integer serviceId, String serviceDdl) {
        FrameServiceEntity service = frameServiceService.getById(serviceId);
        Objects.requireNonNull(service);
        FrameInfoEntity frameInfo = frameInfoService.getById(service.getFrameId());
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        loadServiceVosDdl(clusters, frameInfo, service.getServiceName(), serviceDdl);

        ServiceMetaItem item = new ServiceMetaItem();
        item.setServiceName(service.getServiceName());
        item.setType(MetaStorage.VOS_DDL);
        item.setFramework(frameInfo.getFrameCode());
        MetaStorage metaStorage =  StorageUtils.getMetaStorage();
        try {
            metaStorage.saveServiceDdl(item, serviceDdl);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }

    @Override
    public String getServiceVosDdl(Integer serviceId) {
        FrameServiceEntity service = frameServiceService.getById(serviceId);
        Objects.requireNonNull(service);
        FrameInfoEntity frameInfo = frameInfoService.getById(service.getFrameId());

        ServiceMetaItem item = new ServiceMetaItem();
        item.setServiceName(service.getServiceName());
        item.setType(MetaStorage.VOS_DDL);
        item.setFramework(frameInfo.getFrameCode());
        MetaStorage metaStorage =  StorageUtils.getMetaStorage();
        try {
            return metaStorage.getServiceDdL(item);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(String.format("服务%s的定义不存在", service.getServiceName()));
        }
    }

}
