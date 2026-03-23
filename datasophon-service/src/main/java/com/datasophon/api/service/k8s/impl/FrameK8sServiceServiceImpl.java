package com.datasophon.api.service.k8s.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.k8s.FrameK8sServiceService;
import com.datasophon.dao.entity.k8s.FrameK8sServiceEntity;
import com.datasophon.dao.mapper.k8s.FrameK8sServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Service("frameK8sServiceService")
@Transactional(rollbackFor = Exception.class)
public class FrameK8sServiceServiceImpl extends ServiceImpl<FrameK8sServiceMapper, FrameK8sServiceEntity>
        implements FrameK8sServiceService {


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
}
