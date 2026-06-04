package com.datasophon.api.service.frame.impl;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.common.storage.HelmStorage;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.frame.FrameK8sServiceMapper;

import org.apache.hadoop.util.VersionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.collection.CollectionUtil;

/**
 * @author zhanghuangbin
 */
@Service("frameK8sServiceService")
@Transactional(rollbackFor = Exception.class)
public class FrameK8sServiceServiceImpl extends ServiceImpl<FrameK8sServiceMapper, FrameK8sServiceEntity>
        implements
            FrameK8sServiceService {
    
    private static final Logger log = LoggerFactory.getLogger(FrameK8sServiceServiceImpl.class);
    
    @Autowired
    private FrameInfoService frameInfoService;
    
    @Autowired
    private ClusterInfoMapper clusterInfoMapper;
    
    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;
    
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
        FrameInfoEntity frameInfo = frameInfoService.getByFrameCode(clusterFrame);
        if (frameInfo == null) {
            return new ArrayList<>(0);
        }
        return lambdaQuery()
                .eq(FrameK8sServiceEntity::getFrameId, frameInfo.getId())
                .select(FrameK8sServiceEntity::getId,
                        FrameK8sServiceEntity::getFrameId,
                        FrameK8sServiceEntity::getServiceName,
                        FrameK8sServiceEntity::getServiceVersion,
                        FrameK8sServiceEntity::getServiceDesc,
                        FrameK8sServiceEntity::getDependencies,
                        FrameK8sServiceEntity::getType,
                        FrameK8sServiceEntity::getSupportArtifacts)
                .list();
    }
    
    @Override
    public List<FrameK8sServiceEntity> listNewest(Integer clusterId) {
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        if (clusterInfo == null) {
            return Collections.emptyList();
        }
        
        List<FrameK8sServiceEntity> list = getByFrameCode(clusterInfo.getClusterFrame());
        
        // 对于每一个服务，只保留最新版本
        Map<String, FrameK8sServiceEntity> existEntity = new HashMap<>();
        list.forEach(newVal -> {
            FrameK8sServiceEntity old = existEntity.get(newVal.getServiceName());
            if (old == null) {
                existEntity.put(newVal.getServiceName(), newVal);
            } else {
                if (VersionUtil.compareVersions(old.getServiceVersion(), newVal.getServiceVersion()) < 0) {
                    existEntity.put(newVal.getServiceName(), newVal);
                }
            }
        });
        
        list = new ArrayList<>(existEntity.values());
        list.sort(Comparator.comparing(FrameK8sServiceEntity::getServiceName));
        return list;
    }
    
    @Override
    public ServiceMetaItem getServiceMetaItem(Integer serviceId) {
        FrameK8sServiceEntity entity = getById(serviceId);
        if (entity == null) {
            throw new BusinessException("服务不存在");
        }
        FrameInfoEntity frameInfo = frameInfoService.getById(entity.getFrameId());
        ServiceMetaItem item = new ServiceMetaItem();
        item.setServiceName(entity.getServiceName());
        item.setFramework(frameInfo.getFrameCode());
        item.setType(MetaStorage.K8S);
        return item;
    }
    
    @Override
    public boolean removeById(Integer serviceId) {
        FrameK8sServiceEntity entity = getById(serviceId);
        if (entity == null) {
            throw new BusinessException("服务不存在");
        }
        // 判断是否存在实例 (K8sServiceInstance)，如果有报错
        Long count = k8sServiceInstanceService.lambdaQuery()
                .eq(K8sServiceInstance::getServiceId, serviceId)
                .count();
        if (count > 0) {
            throw new BusinessHintException(String.format("服务 %s 还存在%s个实例，无法删除", entity.getServiceName(), count));
        }
        
        // 删除 nexus 的对应的 meta 相关文件
        FrameInfoEntity frameInfo = frameInfoService.getById(entity.getFrameId());
        MetaStorage metaStorage = StorageUtils.getMetaStorage();
        metaStorage.removeK8sMeta(frameInfo.getFrameCode(), entity.getServiceName());
        
        // 删除 helm 相关文件 (如果有 chart 的话)
        if (entity.getSupportArtifacts() != null && entity.getSupportArtifacts().contains("helm")) {
            HelmStorage helmStorage = StorageUtils.getHelmStorage();
            if (entity.getArtifact() != null) {
                try {
                    helmStorage.removeHelm(String.format("%s-%s.tgz", entity.getServiceName(), entity.getServiceVersion()));
                } catch (Exception e) {
                    log.warn("删除 helm chart 失败：{}, 继续执行其他删除操作", e.getMessage());
                }
            }
        }
        // 最后删除自己
        return super.removeById(serviceId);
    }
}
