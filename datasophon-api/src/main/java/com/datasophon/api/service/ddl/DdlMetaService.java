package com.datasophon.api.service.ddl;

import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DdlMetaService {
    
    FrameInfoEntity initFramework(String frameCode);
    
    FrameServiceEntity loadServicePhysicalDdl(List<ClusterInfoEntity> clusters, FrameInfoEntity frameInfo, String serviceName, String serviceDdl);
    
    FrameK8sServiceEntity loadServiceK8sDdl(FrameInfoEntity frameInfo, String serviceName, String serviceDdl);
    
    void updateServiceVosDdl(Integer serviceId, String serviceDdl);
    
    String getServiceVosDdl(Integer serviceId);
}
