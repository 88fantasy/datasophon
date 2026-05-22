/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import cn.hutool.core.collection.CollectionUtil;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.ServiceRoleState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 集群整体状态巡检 Spring Service（替代 ClusterStatusActor）。
 *
 * <p>原 ClusterStatusActor.doOnReceive/execCheckCmd() 逻辑迁移至此；
 * 该 Actor 本身无 Pekka 依赖，转为 @Service 零逻辑改动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterStatusService {

    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final K8sService k8sService;
    private final K8sClusterConfigService k8sClusterConfigService;

    /**
     * 检查所有集群（或指定集群）的运行状态。
     *
     * @param clusterId 若非 null，则只检查该集群；为 null 时检查全部就绪集群
     */
    public void checkClusterStatus(Integer clusterId) {
        List<ClusterInfoEntity> clusterList = new ArrayList<>();
        if (clusterId != null) {
            ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
            if (clusterInfo != null) {
                clusterList.add(clusterInfo);
            }
        } else {
            clusterList.addAll(clusterInfoService.getReadyClusterList());
        }

        for (ClusterInfoEntity cluster : clusterList) {
            if (ClusterArchType.physical.equals(cluster.getArchType())) {
                List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                        roleInstanceService.getServiceRoleInstanceListByClusterId(cluster.getId());
                if (roleInstanceList.isEmpty()) {
                    continue;
                }
                if (roleInstanceList.stream().allMatch(r -> ServiceRoleState.STOP.equals(r.getServiceRoleState()))) {
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
                        state = status.getNodes().stream().anyMatch(node -> "True".equals(node.getStatus()))
                                ? ClusterState.RUNNING : ClusterState.STOP;
                    }
                    clusterInfoService.updateClusterState(cluster.getId(), state.getValue());
                } catch (Exception e) {
                    log.error("check cluster:{} status fail, {}", cluster.getClusterName(), e.getMessage(), e);
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
                }
            }
        }
    }
}
