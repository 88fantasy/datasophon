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

package com.datasophon.api.service.cmd.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.mapper.cmd.ClusterK8sServiceCommandMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service("clusterK8sServiceCommandService")
public class ClusterK8sServiceCommandServiceImpl extends ServiceImpl<ClusterK8sServiceCommandMapper, ClusterK8sServiceCommandEntity>
        implements ClusterK8sServiceCommandService {

    @Override
    public ClusterK8sServiceCommandEntity getCommandById(String commandId) {
        return lambdaQuery().eq(ClusterK8sServiceCommandEntity::getCommandId, commandId).one();
    }

    @Override
    public IPage<ClusterK8sServiceCommandEntity> findCommandByPage(Integer clusterId, String serviceName, Integer page, Integer pageSize) {
        return lambdaQuery().eq(clusterId != null, ClusterK8sServiceCommandEntity::getClusterId, clusterId)
                .eq(StringUtils.isNotBlank(serviceName), ClusterK8sServiceCommandEntity::getServiceName, serviceName)
                .orderByDesc(ClusterK8sServiceCommandEntity::getCreateTime)
                .page(new Page<>(page, pageSize));
    }


}
