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

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceCommandEntity;
import com.datasophon.dao.mapper.ClusterServiceCommandMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service("clusterServiceCommandService")
public class ClusterServiceCommandServiceImpl extends ServiceImpl<ClusterServiceCommandMapper, ClusterServiceCommandEntity>
        implements ClusterServiceCommandService {

    @Override
    public Result getServiceCommandlist(Integer clusterId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<ClusterServiceCommandEntity> list = this.list(new QueryWrapper<ClusterServiceCommandEntity>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .orderByDesc(Constants.CREATE_TIME).last("limit " + offset + "," + pageSize));
        Long total = this.count(new QueryWrapper<ClusterServiceCommandEntity>()
                .eq(Constants.CLUSTER_ID, clusterId));
        for (ClusterServiceCommandEntity commandEntity : list) {
            commandEntity.setCommandStateCode(commandEntity.getCommandState().getValue());
            Date createTime = commandEntity.getCreateTime();
            Date endTime = commandEntity.getEndTime();
            if (Objects.isNull(endTime)) {
                endTime = new Date();
            }
            long between = DateUtil.between(createTime, endTime, DateUnit.MS);
            String durationTime = DateUtil.formatBetween(between, BetweenFormatter.Level.SECOND);
            commandEntity.setDurationTime(durationTime);
        }
        return Result.success(total, list);
    }

    @Override
    public ClusterServiceCommandEntity getCommandById(String commandId) {
       return lambdaQuery().eq(ClusterServiceCommandEntity::getCommandId, commandId).one();
    }


}
