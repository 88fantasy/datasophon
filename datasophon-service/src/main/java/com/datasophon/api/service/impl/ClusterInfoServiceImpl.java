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

package com.datasophon.api.service.impl;

import akka.actor.ActorRef;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.ClusterActor;
import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.ClusterAlertGroupMapService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterNodeLabelService;
import com.datasophon.api.service.ClusterQueueCapacityService;
import com.datasophon.api.service.ClusterRackService;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterYarnSchedulerService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.utils.PackageUtils;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ClusterCommand;
import com.datasophon.common.enums.ClusterCommandType;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertGroupMap;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("clusterInfoService")
@Transactional
public class ClusterInfoServiceImpl extends ServiceImpl<ClusterInfoMapper, ClusterInfoEntity>
        implements
        ClusterInfoService {

    @Autowired
    private ClusterInfoMapper clusterInfoMapper;

    @Autowired
    private ClusterRoleUserService clusterUserService;

    @Autowired
    private AlertGroupService alertGroupService;

    @Autowired
    private ClusterAlertGroupMapService groupMapService;

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private ClusterHostService clusterHostService;

    @Autowired
    private ClusterYarnSchedulerService yarnSchedulerService;

    @Autowired
    private ClusterNodeLabelService nodeLabelService;

    @Autowired
    private ClusterQueueCapacityService queueCapacityService;

    @Autowired
    private ClusterRackService rackService;

    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Override
    public ClusterInfoEntity getClusterByClusterCode(String clusterCode) {
        return clusterInfoMapper.getClusterByClusterCode(clusterCode);
    }

    @Override
    public ClusterInfoEntity saveCluster(ClusterInfoEntity clusterInfo) {
        if (lambdaQuery().eq(ClusterInfoEntity::getClusterCode, clusterInfo.getClusterCode()).exists()) {
            throw new BusinessHintException(Status.CLUSTER_CODE_EXISTS.getMsg());
        }
        clusterInfo.setCreateTime(new Date());
        clusterInfo.setCreateBy(SecurityUtils.getAuthUser().getUsername());
        clusterInfo.setClusterState(ClusterState.NEED_CONFIG);
        save(clusterInfo);

        if (ClusterArchType.physical.equals(clusterInfo.getArchType())) {
            List<AlertGroupEntity> alertGroupList = alertGroupService.list();
            for (AlertGroupEntity alertGroupEntity : alertGroupList) {
                ClusterAlertGroupMap alertGroupMap = new ClusterAlertGroupMap();
                alertGroupMap.setAlertGroupId(alertGroupEntity.getId());
                alertGroupMap.setClusterId(clusterInfo.getId());
                groupMapService.save(alertGroupMap);
            }

            yarnSchedulerService.createDefaultYarnScheduler(clusterInfo.getId());

            nodeLabelService.createDefaultNodeLabel(clusterInfo.getId());

            queueCapacityService.createDefaultQueue(clusterInfo.getId());

            rackService.createDefaultRack(clusterInfo.getId());

            putClusterVariable(clusterInfo);
        }

        return clusterInfo;

    }

    private void putClusterVariable(ClusterInfoEntity clusterInfo) {
        ConcurrentHashMap<String, String> globalVariables = GlobalVariables.genDefaultGlobalVariables();
        globalVariables.put(GlobalVariables.surroundKey("HADOOP_HOME"),
                Constants.INSTALL_PATH + Constants.SLASH + PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "HDFS")
        );
        globalVariables.put(GlobalVariables.surroundKey(GlobalVariables.CLUSTER_CODE), clusterInfo.getClusterFrame());
        GlobalVariables.put(clusterInfo.getId(), globalVariables);
    }

    @Override
    public List<ClusterInfoEntity> getClusterList() {
        List<ClusterInfoEntity> list = this.list();
        for (ClusterInfoEntity clusterInfoEntity : list) {
            List<UserInfoEntity> userList = clusterUserService.getAllClusterManagerByClusterId(clusterInfoEntity.getId());
            clusterInfoEntity.setClusterManagerList(userList);
            clusterInfoEntity.setClusterStateCode(clusterInfoEntity.getClusterState().getValue());
        }
        return list;
    }

    @Override
    public List<ClusterInfoEntity> runningClusterList() {
        return lambdaQuery().eq(ClusterInfoEntity::getClusterState, ClusterState.RUNNING).list();
    }

    @Override
    public List<ClusterInfoEntity> getReadyClusterList() {
        return lambdaQuery()
                .ne(ClusterInfoEntity::getClusterState, ClusterState.NEED_CONFIG)
                .list();
    }

    @Override
    public void updateClusterState(Integer clusterId, Integer clusterState) {
        ClusterInfoEntity clusterInfo = this.getById(clusterId);
        ClusterState state = ClusterState.of(clusterState);
        if (state != null) {
            clusterInfo.setClusterState(state);
            this.updateById(clusterInfo);
        } else {
            throw new BusinessHintException("未知状态");
        }
    }

    @Override
    public List<ClusterInfoEntity> getClusterByFrameCode(String frameCode) {
        return this.list(new QueryWrapper<ClusterInfoEntity>().eq(Constants.CLUSTER_FRAME, frameCode));
    }

    @Override
    public void updateCluster(ClusterInfoEntity clusterInfo) {
        List<ClusterInfoEntity> list = this.list(new QueryWrapper<ClusterInfoEntity>().eq(Constants.CLUSTER_CODE, clusterInfo.getClusterCode()));
        if (Objects.nonNull(list) && !list.isEmpty()) {
            ClusterInfoEntity clusterInfoEntity = list.get(0);
            if (!clusterInfoEntity.getId().equals(clusterInfo.getId())) {
                throw new BusinessHintException(Status.CLUSTER_CODE_EXISTS.getMsg());
            }
        }
        this.updateById(clusterInfo);
    }

    @Override
    public void deleteCluster(Integer clusterId) {
        ClusterInfoEntity clusterInfo = this.getById(clusterId);
        if (clusterInfo == null) {
            return;
        }

        if (ClusterArchType.physical.equals(clusterInfo.getArchType())) {
            boolean canDelete = false;

            if (ClusterState.STOP.equals(clusterInfo.getClusterState())) {
                List<ClusterServiceInstanceEntity> serviceInstanceList = clusterServiceInstanceService.listAll(clusterId);
                canDelete = serviceInstanceList.stream().noneMatch(instance -> clusterServiceInstanceService.hasRunningRoleInstance(instance.getId()));
            }
            if (!canDelete) {
                throw new BusinessHintException(String.format("集群%s存在正在运行的实例，不能删除。请先停止所有的实例", clusterInfo.getClusterName()));
            }
        } else {
            boolean canDelete = !k8sServiceInstanceService.hasRunningInstance(clusterId);
            if (!canDelete) {
                throw new BusinessHintException(String.format("集群%s存在正在运行的实例，不能删除。请先停止所有的实例", clusterInfo.getClusterName()));
            }
        }
        ActorRef ref = ActorUtils.getLocalActor(ClusterActor.class, "clusterActor");
        ref.tell(new ClusterCommand(ClusterCommandType.DELETE, clusterId), ActorRef.noSender());
        updateClusterState(clusterId, ClusterState.DELETING.getValue());
    }

}
