/*
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
 */

package com.datasophon.api.master;

import cn.hutool.core.collection.CollectionUtil;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.common.command.ClusterCommand;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.ServiceRoleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点状态监测
 */
public class ClusterStatusActor extends TypedActor<ClusterCommand> {

    private static final Logger logger = LoggerFactory.getLogger(ClusterStatusActor.class);



    @Override
    protected void doOnReceive(ClusterCommand clusterCommand) throws Throwable {
        execCheckCmd(clusterCommand);
    }


    protected void execCheckCmd(ClusterCommand cmd) {
        ClusterServiceRoleInstanceService roleInstanceService = getBean(ClusterServiceRoleInstanceService.class);
        ClusterInfoService clusterInfoService = getBean(ClusterInfoService.class);
        K8sService k8sService = getBean(K8sService.class);
        K8sClusterConfigService k8sClusterConfigService = getBean(K8sClusterConfigService.class);
        // 获取所有集群
        List<ClusterInfoEntity> clusterList = new ArrayList<>();
        if (cmd.getClusterId() != null) {
            ClusterInfoEntity clusterInfo = clusterInfoService.getById(cmd.getClusterId());
            if (clusterInfo != null) {
                clusterList.add(clusterInfo);
            }
        } else {
            clusterList.addAll(clusterInfoService.getReadyClusterList());
        }
        for (ClusterInfoEntity cluster : clusterList) {
            if (ClusterArchType.physical.equals(cluster.getArchType())) {
                // 获取集群上正在运行的服务
                List<ClusterServiceRoleInstanceEntity> roleInstanceList = roleInstanceService.getServiceRoleInstanceListByClusterId(cluster.getId());
                if (roleInstanceList.isEmpty()) {
                    continue;
                }

                if (roleInstanceList.stream().allMatch(roleInstance -> ServiceRoleState.STOP.equals(roleInstance.getServiceRoleState()))) {
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
                } else {
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.RUNNING.getValue());
                }
            } else {
                K8sClusterConfig config = k8sClusterConfigService.getByClusterId(cluster.getId());
                try {
                    K8sClusterStatus status = k8sService.getState(config);

                    ClusterState state = ClusterState.STOP;
                    if (CollectionUtil.isNotEmpty(status.getNodes())) {
                        state = status.getNodes().stream().anyMatch(node -> "True".equals(node.getStatus())) ? ClusterState.RUNNING : ClusterState.STOP;
                    }

                    clusterInfoService.updateClusterState(cluster.getId(), state.getValue());
                } catch (Exception e) {
                    logger.error("check cluster:{} status fail, {}", cluster.getClusterName(), e.getMessage(), e);
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
                }
            }
        }
    }


}
