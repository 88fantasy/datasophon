package com.datasophon.api.service.ddl.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
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
import com.datasophon.api.service.extrepo.utils.MetaUtils;
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
import com.datasophon.common.utils.PathUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.NeedRestart;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.datasophon.common.Constants.FRAMEWORK_TPL;
import static com.datasophon.common.Constants.META_PATH;

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


    private static final String HDFS = "HDFS";

    private static final String HADOOP = "HADOOP";

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

        log.info("使用框架模板{}初始化框架{}", FileUtil.file(FRAMEWORK_TPL).getAbsolutePath(), frameCode);

        File[] srvCmps = FileUtil.ls(FRAMEWORK_TPL);
        List<String> installingCmp = new ArrayList<>();
        for (File srvCmp : srvCmps) {
            File targetDdp = new File(target, srvCmp.getName());
            if (targetDdp.exists()) {
                continue;
            }
            log.info("add service component {} to framework {}", srvCmp.getName(), frameCode);
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
                            loadServiceDdl(clusters, entity, serviceName, serviceDdl);
                        } catch (Exception e) {
                            log.error("invalid service ddl file: {}/{}", frameCode, serviceName, e);
                        }
                    }
                }
            }
        }
    }


    @Override
    public FrameServiceEntity loadServiceDdl(List<ClusterInfoEntity> clusters, FrameInfoEntity frameInfo, String serviceName, String serviceDdl) {
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
        } else if (!serviceEntity.getServiceJsonMd5().equals(serviceInfoMd5)) {
            String configMapStr = JSONObject.toJSONString(configFileMap);
            String configFileMapStrMd5 = SecureUtil.md5(configMapStr);
            if (!configFileMapStrMd5.equals(serviceEntity.getConfigFileJsonMd5())) {
                // update config
                updateServiceInstanceConfig(frameInfo.getFrameCode(), serviceInfo.getName(), serviceInfo.getParameters());
            }
            buildServiceEntity(frameInfo, serviceName, serviceDdl, serviceInfo, serviceInfoMd5, serviceEntity, configFileMap);
            frameServiceService.updateById(serviceEntity);
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
    public void updateServiceDdl(Integer serviceId, String serviceDdl) {
        FrameServiceEntity service = frameServiceService.getById(serviceId);
        Objects.requireNonNull(service);
        FrameInfoEntity frameInfo = frameInfoService.getById(service.getFrameId());
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        FrameServiceEntity entity = loadServiceDdl(clusters, frameInfo, service.getServiceName(), serviceDdl);
        File metaDir = FileUtil.file(Constants.META_PATH);
        File targetFile = PathUtils.join(metaDir.toPath(), frameInfo.getFrameCode(), entity.getServiceName(), MetaUtils.SERVICE_DDL).toFile();
        if (!targetFile.exists()) {
            FileUtil.newFile(targetFile.getPath());
        }
        FileUtil.writeString(serviceDdl, targetFile, StandardCharsets.UTF_8);
    }

    @Override
    public String getServiceDdl(Integer serviceId) {
        FrameServiceEntity service = frameServiceService.getById(serviceId);
        Objects.requireNonNull(service);
        FrameInfoEntity frameInfo = frameInfoService.getById(service.getFrameId());
        File metaDir = FileUtil.file(Constants.META_PATH);
        File targetFile = PathUtils.join(metaDir.toPath(), frameInfo.getFrameCode(), service.getServiceName(), MetaUtils.SERVICE_DDL).toFile();
        return FileUtil.readString(targetFile, StandardCharsets.UTF_8);
    }

}
