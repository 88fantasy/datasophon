package com.datasophon.api.service.frame.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.mapper.frame.FrameK8sServiceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Service("frameK8sServiceService")
@Transactional(rollbackFor = Exception.class)
public class FrameK8sServiceServiceImpl extends ServiceImpl<FrameK8sServiceMapper, FrameK8sServiceEntity>
        implements FrameK8sServiceService {

    @Autowired
    private FrameInfoService frameInfoService;

    @Override
    public List<FrameK8sServiceEntity> listSimpleService(List<Integer> frameIds) {
        if (CollectionUtil.isEmpty(frameIds)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .in(FrameK8sServiceEntity::getFrameId, frameIds)
                .select(FrameK8sServiceEntity::getServiceName, FrameK8sServiceEntity::getServiceVersion, FrameK8sServiceEntity::getFrameId)
                .list();
    }

    @Override
    public List<FrameK8sServiceEntity> getByFrameCode(String clusterFrame) {
        FrameInfoEntity frameInfo =  frameInfoService.getByFrameCode(clusterFrame);
        if (frameInfo == null) {
            return new ArrayList<>(0);
        }
        return lambdaQuery()
                .eq(FrameK8sServiceEntity::getFrameId, frameInfo.getId())
                .list();
    }


}
