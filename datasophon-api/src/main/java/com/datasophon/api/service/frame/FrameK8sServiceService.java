package com.datasophon.api.service.frame;

import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author zhanghuangbin
 */
public interface FrameK8sServiceService extends IService<FrameK8sServiceEntity> {
    
    List<FrameK8sServiceEntity> listSimpleService(List<Integer> frameIds);
    
    List<FrameK8sServiceEntity> getByFrameCode(String clusterFrame);
    
    List<FrameK8sServiceEntity> listNewest(Integer clusterId);
    
    ServiceMetaItem getServiceMetaItem(Integer serviceId);
    
    boolean removeById(Integer serviceId);
    
}
