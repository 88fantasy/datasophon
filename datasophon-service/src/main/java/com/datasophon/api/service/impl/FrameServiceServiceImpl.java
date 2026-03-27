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

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.FrameInfoMapper;
import com.datasophon.dao.mapper.FrameServiceMapper;
import org.apache.hadoop.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("frameServiceService")
public class FrameServiceServiceImpl extends ServiceImpl<FrameServiceMapper, FrameServiceEntity> implements FrameServiceService {

    @Autowired
    FrameInfoMapper frameInfoMapper;

    @Autowired
    ClusterInfoMapper clusterInfoMapper;

    @Autowired
    ClusterServiceInstanceService serviceInstanceService;


    @Autowired
    private FrameServiceRoleService frameServiceRoleService;

    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;
    @Autowired
    private DdlMetaService ddlMetaService;

    @Override
    public List<FrameServiceEntity> getFrameServiceList(Integer clusterId) {
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        FrameInfoEntity frameInfo = frameInfoMapper.getFrameInfoByFrameCode(clusterInfo.getClusterFrame());
        List<FrameServiceEntity> list = this.lambdaQuery()
                .eq(FrameServiceEntity::getFrameId, frameInfo.getId())
                .orderByAsc(FrameServiceEntity::getServiceName)
                .list();
        setInstalled(clusterId, list);
        return list;
    }

    @Override
    public List<FrameServiceEntity> getBasicFrameServiceList(Integer clusterId) {
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        FrameInfoEntity frameInfo = frameInfoMapper.getFrameInfoByFrameCode(clusterInfo.getClusterFrame());
        List<FrameServiceEntity> list = this.lambdaQuery()
                .eq(FrameServiceEntity::getFrameId, frameInfo.getId())
                .in(FrameServiceEntity::getServiceName, Arrays.asList("ALERTMANAGER", "GRAFANA", "PROMETHEUS"))
                .orderByAsc(FrameServiceEntity::getServiceName)
                .list();
        setInstalled(clusterId, list);
        return list;
    }

    @Override
    public List<FrameServiceEntity> listNewest(Integer clusterId, Boolean newest) {
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        FrameInfoEntity frameInfo = frameInfoMapper.getFrameInfoByFrameCode(clusterInfo.getClusterFrame());

        List<FrameServiceEntity> list = this.lambdaQuery()
                .eq(FrameServiceEntity::getFrameId, frameInfo.getId())
                .orderByAsc(FrameServiceEntity::getServiceName)
                .list();
        if (!Boolean.FALSE.equals(newest)) {
            Map<String, FrameServiceEntity> existEntity = new HashMap<>();
            list.forEach(newVal -> {
                FrameServiceEntity old = existEntity.get(newVal.getServiceName());
                if (old == null) {
                    existEntity.put(newVal.getServiceName(), newVal);
                } else {
                    if (VersionUtil.compareVersions(old.getServiceVersion(), newVal.getServiceVersion()) < 0) {
                        existEntity.put(newVal.getServiceName(), newVal);
                    }
                }
            });
            list = new ArrayList<>(existEntity.values());
            list.sort(Comparator.comparing(FrameServiceEntity::getServiceName));
        }
        setInstalled(clusterId, list);
        return list;
    }

    private void setInstalled(Integer clusterId, List<FrameServiceEntity> list) {
        List<ClusterServiceInstanceEntity> serviceInstances = serviceInstanceService.getServiceInstanceByClusterId(clusterId);
        Map<String, ClusterServiceInstanceEntity> map = serviceInstances.stream().collect(
                Collectors.toMap(ClusterServiceInstanceEntity::getServiceName, a -> a, (a, b) -> a.getUpdateTime().after(b.getUpdateTime()) ? a : b)
        );
        for (FrameServiceEntity serviceEntity : list) {
            ClusterServiceInstanceEntity serviceInstance = map.get(serviceEntity.getServiceName());
            serviceEntity.setInstalled(serviceInstance != null && !serviceInstance.getServiceState().equals(ServiceState.WAIT_INSTALL));
        }
    }


    @Override
    public Result getServiceListByServiceIds(List<Integer> serviceIds) {
        Collection<FrameServiceEntity> list = this.listByIds(serviceIds);
        return Result.success(list);
    }

    @Override
    public FrameServiceEntity getServiceByFrameIdAndServiceName(Integer frameId, String serviceName) {
        return this.lambdaQuery()
                .eq(FrameServiceEntity::getFrameId, frameId)
                .eq(FrameServiceEntity::getServiceName, serviceName)
                .one();
    }

    @Override
    public FrameServiceEntity getServiceByFrameCodeAndServiceName(String clusterFrame, String serviceName) {
        return this.getBaseMapper().getServiceByFrameCodeAndServiceName(clusterFrame, serviceName);
    }


    @Override
    public List<FrameServiceEntity> getAllFrameServiceByFrameCode(String clusterFrame) {
        return this.list(new QueryWrapper<FrameServiceEntity>().eq(Constants.FRAME_CODE_1, clusterFrame));
    }

    @Override
    public List<FrameServiceEntity> listServices(List<Integer> serviceIds) {
        if (serviceIds.isEmpty()) {
            return new ArrayList<>();
        }
        return lambdaQuery().in(FrameServiceEntity::getId, serviceIds).list();
    }


    @Override
    public List<FrameServiceEntity> listSimpleService(List<String> clusterFrames) {
        if (CollectionUtil.isEmpty(clusterFrames)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .in(FrameServiceEntity::getFrameCode, clusterFrames)
                .select(FrameServiceEntity::getServiceName, FrameServiceEntity::getServiceVersion,
                        FrameServiceEntity::getFrameId, FrameServiceEntity::getFrameCode)
                .list();
    }

    @Override
    public FrameServiceEntity getNewestDefByName(String frameCode, String serviceName) {
        List<FrameServiceEntity> services = this.lambdaQuery()
                .eq(FrameServiceEntity::getServiceName, serviceName)
                .eq(FrameServiceEntity::getFrameCode, frameCode)
                .list();
        if (services.isEmpty()) {
            return null;
        }
        FrameServiceEntity target = services.get(0);
        for (FrameServiceEntity service : services) {
            if (VersionUtil.compareVersions(service.getServiceVersion(), target.getServiceVersion()) > 0) {
                target = service;
            }
        }
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        final FrameServiceEntity serviceEntity = getById(id);
        if (serviceEntity == null) {
            throw new BusinessHintException("Service 组件不存在。");
        }
        final boolean exists = clusterServiceInstanceService.lambdaQuery()
                .eq(ClusterServiceInstanceEntity::getFrameServiceId, serviceEntity.getId())
                .exists();
        if (exists) {
            throw new BusinessHintException("Service 组件正在使用中。");
        }
        // 删除配置
        frameServiceRoleService.lambdaUpdate().eq(FrameServiceRoleEntity::getServiceId, id).remove();
        boolean success = super.removeById(id);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
               new Thread(()-> {
                   PackageStorage packageStorage = StorageUtils.getPackageStorage();
                   packageStorage.deletePackage(serviceEntity.getPackageName());

                   MetaStorage metaStorage = StorageUtils.getMetaStorage();
                   metaStorage.removeVosMeta(serviceEntity.getFrameCode(), serviceEntity.getServiceName());
               }).start();
            }
        });
        return success;
    }
}
