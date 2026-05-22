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

import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.command.GenerateRackPropCommand;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Rack 属性文件生成 Spring Service，业务逻辑来自 {@link RackActor}。
 */
@Service
public class RackService {

    private static final Logger logger = LoggerFactory.getLogger(RackService.class);

    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterHostService hostService;
    private final ClusterInfoService clusterInfoService;

    public RackService(ClusterServiceRoleInstanceService roleInstanceService,
                       ClusterHostService hostService,
                       ClusterInfoService clusterInfoService) {
        this.roleInstanceService = roleInstanceService;
        this.hostService = hostService;
        this.clusterInfoService = clusterInfoService;
    }

    /**
     * 异步生成 rack.properties 并推送到所有 NameNode（替代 RackActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void generateRackProp(GenerateRackPropCommand command) {
        List<ClusterServiceRoleInstanceEntity> roleList = roleInstanceService
                .getServiceRoleInstanceListByClusterIdAndRoleName(command.getClusterId(), "NameNode");
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(command.getClusterId());

        Generators generators = new Generators();
        generators.setFilename("rack.properties");
        generators.setOutputDirectory("etc/hadoop");
        generators.setConfigFormat("properties2");

        ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
        List<ClusterHostDO> hostList = hostService.list();
        for (ClusterHostDO host : hostList) {
            ServiceConfig sc = ProcessUtils.createServiceConfig(
                    host.getIp(), Constants.SLASH + host.getRack(), "input");
            serviceConfigs.add(sc);
        }

        HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        configFileMap.put(generators, serviceConfigs);

        for (ClusterServiceRoleInstanceEntity roleInstance : roleList) {
            ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
            serviceRoleInfo.setName("NameNode");
            serviceRoleInfo.setParentName("HDFS");
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setDecompressPackageName(
                    PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "HDFS"));
            serviceRoleInfo.setHostname(roleInstance.getHostname());
            try {
                ExecResult result = new ServiceConfigureHandler().handlerRequest(serviceRoleInfo);
                if (!result.getExecResult()) {
                    logger.error("generate rack.properties failed for host {}", roleInstance.getHostname());
                }
            } catch (Exception e) {
                logger.error("generate rack.properties failed for host {}: {}", roleInstance.getHostname(), e.getMessage(), e);
            }
        }
    }
}
