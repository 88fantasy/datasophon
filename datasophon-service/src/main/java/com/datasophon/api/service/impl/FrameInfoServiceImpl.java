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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.k8s.FrameK8sServiceService;
import com.datasophon.api.vo.frameinfo.FrameInfoVO;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.k8s.FrameK8sServiceEntity;
import com.datasophon.dao.mapper.FrameInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("frameInfoService")
public class FrameInfoServiceImpl extends ServiceImpl<FrameInfoMapper, FrameInfoEntity> implements FrameInfoService {

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private FrameK8sServiceService frameK8sServiceService;

    @Override
    public List<FrameInfoVO> getAllClusterFrame() {
        List<FrameInfoEntity> entities = this.list();
        if (CollectionUtils.isEmpty(entities)) {
            return new ArrayList<>(0);
        }

        List<FrameInfoVO> result = BeanUtil.copyToList(entities, FrameInfoVO.class);

        Set<Integer> frameInfoIds = entities.stream().map(FrameInfoEntity::getId).collect(Collectors.toSet());
        Map<Integer, List<FrameServiceEntity>> frameServiceGroupBys = frameServiceService.lambdaQuery()
                .select(FrameServiceEntity::getId, FrameServiceEntity::getFrameId, FrameServiceEntity::getFrameCode,
                        FrameServiceEntity::getServiceName, FrameServiceEntity::getServiceVersion,
                        FrameServiceEntity::getServiceDesc)
                .in(FrameServiceEntity::getFrameId, frameInfoIds)
                .orderByAsc(FrameServiceEntity::getServiceName)
                .list()
                .stream()
                .collect(Collectors.groupingBy(FrameServiceEntity::getFrameId));

        result.forEach(f -> f.setFrameServiceList(frameServiceGroupBys.get(f.getId())));


        Map<Integer, List<FrameK8sServiceEntity>> k8sServiceGroupBys = frameK8sServiceService.lambdaQuery()
                .select(FrameK8sServiceEntity::getId, FrameK8sServiceEntity::getFrameId,
                        FrameK8sServiceEntity::getServiceName, FrameK8sServiceEntity::getServiceVersion,
                        FrameK8sServiceEntity::getServiceDesc, FrameK8sServiceEntity::getSupportArtifacts)
                .in(FrameK8sServiceEntity::getFrameId, frameInfoIds)
                .orderByAsc(FrameK8sServiceEntity::getServiceName)
                .list()
                .stream()
                .collect(Collectors.groupingBy(FrameK8sServiceEntity::getFrameId));
        result.forEach(f -> f.setFrameK8sServiceList(k8sServiceGroupBys.get(f.getId())));

        return result;
    }

    @Override
    public FrameInfoEntity saveFrameIfAbsent(String frameCode) {
        FrameInfoEntity frameInfo = lambdaQuery().eq(FrameInfoEntity::getFrameCode, frameCode).one();
        if (Objects.isNull(frameInfo)) {
            frameInfo = new FrameInfoEntity();
            frameInfo.setFrameCode(frameCode);
            save(frameInfo);
        }
        return frameInfo;
    }

    @Override
    public FrameInfoEntity getByFrameCode(String frameCode) {
        return lambdaQuery().eq(FrameInfoEntity::getFrameCode, frameCode).one();
    }
}
