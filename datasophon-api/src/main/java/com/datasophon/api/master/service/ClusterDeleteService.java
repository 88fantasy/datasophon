/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.master.service;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSONArray;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 集群删除 Spring Service，业务逻辑完全来自 {@link ClusterDeleteActor}。
 */
@Service
public class ClusterDeleteService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterDeleteService.class);
    private static final String DEPRECATED = "Deprecated";

    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService;
    private final ClusterServiceInstanceService serviceInstanceService;
    private final ClusterHostService hostService;
    private final K8sClusterConfigService k8sClusterConfigService;
    private final K8sServiceInstanceService k8sServiceInstanceService;
    private final K8sServiceInstanceValuesService k8sServiceInstanceValuesService;
    private final K8sClusterNamespaceService k8sClusterNamespaceService;
    private final K8sService k8sService;

    public ClusterDeleteService(ClusterInfoService clusterInfoService,
                                 ClusterServiceRoleInstanceService roleInstanceService,
                                 ClusterServiceRoleGroupConfigService roleGroupConfigService,
                                 ClusterServiceInstanceService serviceInstanceService,
                                 ClusterHostService hostService,
                                 K8sClusterConfigService k8sClusterConfigService,
                                 K8sServiceInstanceService k8sServiceInstanceService,
                                 K8sServiceInstanceValuesService k8sServiceInstanceValuesService,
                                 K8sClusterNamespaceService k8sClusterNamespaceService,
                                 K8sService k8sService) {
        this.clusterInfoService = clusterInfoService;
        this.roleInstanceService = roleInstanceService;
        this.roleGroupConfigService = roleGroupConfigService;
        this.serviceInstanceService = serviceInstanceService;
        this.hostService = hostService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sServiceInstanceService = k8sServiceInstanceService;
        this.k8sServiceInstanceValuesService = k8sServiceInstanceValuesService;
        this.k8sClusterNamespaceService = k8sClusterNamespaceService;
        this.k8sService = k8sService;
    }

    /**
     * 异步删除集群（替代 ClusterDeleteActor.tell(new ClusterCommand(DELETE, clusterId))）。
     */
    @Async("masterExecutor")
    public void deleteCluster(Integer clusterId) {
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
            deletePhysicalClusterComponents(clusterId);
        } else {
            boolean success = deleteK8sAgent(clusterId);
            if (!success) {
                return;
            }
            deleteK8sClusterComponents(clusterId);
        }
        clusterInfoService.removeById(clusterId);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private boolean deleteK8sAgent(Integer clusterId) {
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            return true;
        }
        K8sConnectionResult result = k8sService.testConnection(config);
        if (!result.isSuccess()) {
            return true;
        }
        new K8sAgentUninstallHandler().execute(config);
        return true;
    }

    private void deleteK8sClusterComponents(Integer clusterId) {
        k8sServiceInstanceService.removeByClusterId(clusterId);
        k8sServiceInstanceValuesService.removeByClusterId(clusterId);
        k8sClusterNamespaceService.removeByClusterId(clusterId);
        k8sClusterConfigService.removeByClusterId(clusterId);
    }

    private boolean backupServiceConfigFiles(ClusterInfoEntity clusterInfo) {
        List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                roleInstanceService.getServiceRoleInstanceListByClusterId(clusterInfo.getId());
        if (roleInstanceList.isEmpty()) {
            return true;
        }
        for (ClusterServiceRoleInstanceEntity roleInstance : roleInstanceList) {
            if (!doMoveRoleConfigPath(clusterInfo, roleInstance)) {
                return false;
            }
        }
        return true;
    }

    private boolean doMoveRoleConfigPath(ClusterInfoEntity clusterInfo,
                                          ClusterServiceRoleInstanceEntity roleInstance) {
        Map<Generators, List<ServiceConfig>> tempConfigMap = new ConcurrentHashMap<>();
        ClusterServiceRoleGroupConfig config =
                roleGroupConfigService.getConfigByRoleGroupId(roleInstance.getRoleGroupId());
        ProcessUtils.generateConfigFileMap(tempConfigMap, config, clusterInfo.getId());

        Map<Generators, List<ServiceConfig>> configFileMap = new ConcurrentHashMap<>();
        tempConfigMap.forEach((generators, configs) -> {
            List<ServiceConfig> serviceConfigs = configs.stream()
                    .filter(c -> Constants.PATH.equals(c.getConfigType()))
                    .peek(c -> {
                        if (Constants.INPUT.equals(c.getType())) {
                            String newPath = getPathNewName((String) c.getValue(), clusterInfo.getId());
                            if (newPath != null) {
                                c.setValue(newPath);
                                c.setConfigType(Constants.MV_PATH);
                            }
                        } else if (Constants.MULTIPLE.equals(c.getType())) {
                            JSONArray value = (JSONArray) c.getValue();
                            List<String> newPaths = value.toJavaList(String.class).stream()
                                    .map(p -> getPathNewName(p, clusterInfo.getId()))
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
        });

        if (configFileMap.isEmpty()) {
            return true;
        }
        String roleName = roleInstance.getServiceRoleName();
        String hostname = roleInstance.getHostname();
        try {
            logger.info("start to uninstall {} in host {}", roleName, hostname);
            ExecResult execResult = ProcessUtils.configServiceRoleInstance(clusterInfo, configFileMap, roleInstance);
            boolean success = Objects.nonNull(execResult) && execResult.getExecResult();
            if (success) {
                logger.info("{} uninstall success in {}", roleName, hostname);
            } else {
                logger.info("{} uninstall failed in {}", roleName, hostname);
            }
            return success;
        } catch (Exception e) {
            logger.error("{} uninstall failed in {}", roleName, hostname, e);
            return false;
        }
    }

    private String getPathNewName(String path, Integer clusterId) {
        if (!path.contains(DEPRECATED)) {
            return String.format("%s_%s_%s_%s", path, DEPRECATED, clusterId, DateUtil.today());
        }
        return null;
    }

    private void deletePhysicalClusterComponents(Integer clusterId) {
        List<ClusterServiceInstanceEntity> serviceInstanceList = serviceInstanceService.listAll(clusterId);
        boolean success = true;
        for (ClusterServiceInstanceEntity instance : serviceInstanceList) {
            Result result = serviceInstanceService.delServiceInstance(instance.getId());
            success = success && result.isSuccess();
        }
        if (success) {
            hostService.removeHostByClusterId(clusterId);
        }
    }
}
