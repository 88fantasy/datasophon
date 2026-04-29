package com.datasophon.api.master;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSONArray;
import com.datasophon.api.master.handler.k8s.K8sAgentUninstallHandler;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ClusterCommand;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
public class ClusterDeleteActor extends TypedActor<ClusterCommand> {

    private static final Logger logger = LoggerFactory.getLogger(ClusterStatusActor.class);

    private static final String DEPRECATED = "Deprecated";


    @Override
    protected void doOnReceive(ClusterCommand message) throws Throwable {
        execDeleteCmd(message);
    }

    private void execDeleteCmd(ClusterCommand clusterCommand) {
        ClusterInfoService clusterInfoService = getBean(ClusterInfoService.class);

        Integer clusterId = clusterCommand.getClusterId();
        if (clusterId == null) {
            return;
        }
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        if (clusterInfo == null) {
            return;
        }

        if (ClusterArchType.physical.equals(clusterInfo.getArchType())) {
            boolean success = backupServiceConfigFiles(clusterInfo);
            if (!success) {
                return;
            }
            deletePhysicalClusterComponents(clusterInfo.getId());
        } else {
            boolean success = deleteK8sAgent(clusterId);
            if (!success) {
                return;
            }
            deleteK8sClusterComponents(clusterInfo.getId());
        }

        clusterInfoService.removeById(clusterId);
    }

    private boolean deleteK8sAgent(Integer clusterId) {
        K8sClusterConfigService k8sClusterConfigService = getBean(K8sClusterConfigService.class);
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            return true;
        }
        K8sService k8sService = getBean(K8sService.class);
        K8sConnectionResult result = k8sService.testConnection(config);
//        cant not connect, we don't delete it
        if (!result.isSuccess()) {
            return true;
        }

        new K8sAgentUninstallHandler().execute(config);
        return true;
    }

    private void deleteK8sClusterComponents(Integer clusterId) {
        K8sClusterConfigService k8sClusterConfigService = getBean(K8sClusterConfigService.class);
        K8sServiceInstanceService k8sServiceInstanceService = getBean(K8sServiceInstanceService.class);
        K8sServiceInstanceValuesService k8sServiceInstanceValuesService = getBean(K8sServiceInstanceValuesService.class);
        K8sClusterNamespaceService k8sClusterNamespaceService = getBean(K8sClusterNamespaceService.class);


        k8sServiceInstanceService.removeByClusterId(clusterId);
        k8sServiceInstanceValuesService.removeByClusterId(clusterId);
        k8sClusterNamespaceService.removeByClusterId(clusterId);
        k8sClusterConfigService.removeByClusterId(clusterId);
    }

    private boolean backupServiceConfigFiles(ClusterInfoEntity clusterInfo) {
        ClusterServiceRoleInstanceService clusterServiceRoleInstanceService = getBean(ClusterServiceRoleInstanceService.class);

        // 检查服务实例配置与目录
        List<ClusterServiceRoleInstanceEntity> roleInstanceList = clusterServiceRoleInstanceService.getServiceRoleInstanceListByClusterId(clusterInfo.getId());
        if (roleInstanceList.isEmpty()) {
            return true;
        }

        for (ClusterServiceRoleInstanceEntity roleInstance : roleInstanceList) {
            boolean goon = doMoveRoleConfigPath(clusterInfo, roleInstance);
            if (!goon) {
                return false;
            }
        }

        return true;
    }

    private boolean doMoveRoleConfigPath(ClusterInfoEntity clusterInfo, ClusterServiceRoleInstanceEntity roleInstance) {
        Map<Generators, List<ServiceConfig>> tempConfigMap = new ConcurrentHashMap<>();
        ClusterServiceRoleGroupConfig config = getBean(ClusterServiceRoleGroupConfigService.class).getConfigByRoleGroupId(roleInstance.getRoleGroupId());
        ProcessUtils.generateConfigFileMap(tempConfigMap, config, clusterInfo.getId());

        Map<Generators, List<ServiceConfig>> configFileMap = new ConcurrentHashMap<>();
        tempConfigMap.forEach(((generators, configs) -> {
            List<ServiceConfig> serviceConfigs = configs.stream()
                    .filter(c -> Constants.PATH.equals(c.getConfigType()))
                    .peek(c -> {
                        if (Constants.INPUT.equals(c.getType())) {
                            String oldPath = (String) c.getValue();
                            String newPath = getPathNewName(oldPath, clusterInfo.getId());
                            if (newPath != null) {
                                c.setValue(newPath);
                                c.setConfigType(Constants.MV_PATH);
                            }
                            return;
                        }
                        if (Constants.MULTIPLE.equals(c.getType())) {
                            JSONArray value = (JSONArray) c.getValue();
                            List<String> oldPaths = value.toJavaList(String.class);
                            List<String> newPaths = oldPaths.stream()
                                    .map(path -> getPathNewName(path, clusterInfo.getId()))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                            if (!newPaths.isEmpty()) {
                                c.setValue(newPaths);
                                c.setConfigType(Constants.MV_PATH);
                            }
                        }
                    })
                    .filter(c -> Constants.MV_PATH.equals(c.getConfigType()))
                    .collect(Collectors.toList());
            if (!serviceConfigs.isEmpty()) {
                configFileMap.put(generators, serviceConfigs);
            }
        }));

        boolean success = true;
        if (!configFileMap.isEmpty()) {
            String roleName = roleInstance.getServiceRoleName();
            String hostname = roleInstance.getHostname();
            try {
                logger.info("start to uninstall {} in host {}", roleName, hostname);
                ExecResult execResult = ProcessUtils.configServiceRoleInstance(clusterInfo, configFileMap, roleInstance);

                success = Objects.nonNull(execResult) && execResult.getExecResult();
                if (success) {
                    logger.info("{} uninstall success in {}", roleName, hostname);
                } else {
                    logger.info("{} uninstall failed in {}", roleName, hostname);
                }
            } catch (Exception e) {
                logger.error("{} uninstall failed in {}, ", roleName, hostname, e);
                success = false;
            }
        }

        return success;
    }


    private String getPathNewName(String path, Integer clusterId) {
        if (!path.contains(DEPRECATED)) {
            return String.format("%s_%s_%s_%s", path, DEPRECATED, clusterId, DateUtil.today());
        } else {
            return null;
        }
    }

    private boolean deletePhysicalClusterComponents(Integer clusterId) {
        ClusterHostService clusterHostService = getBean(ClusterHostService.class);
        ClusterServiceInstanceService clusterServiceInstanceService = getBean(ClusterServiceInstanceService.class);

        List<ClusterServiceInstanceEntity> serviceInstanceList = clusterServiceInstanceService.listAll(clusterId);

        boolean success = true;
        for (ClusterServiceInstanceEntity instance : serviceInstanceList) {
            Result result = clusterServiceInstanceService.delServiceInstance(instance.getId());
            success = success && result.isSuccess();
        }

        if (success) {
            clusterHostService.removeHostByClusterId(clusterId);
        }

        return success;
    }
}
