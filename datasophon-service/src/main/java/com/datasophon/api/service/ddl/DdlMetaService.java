package com.datasophon.api.service.ddl;

import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface DdlMetaService {


    void initFramework(FrameInfoEntity entity);


    FrameServiceEntity loadServiceDdl(List<ClusterInfoEntity> clusters, FrameInfoEntity frameInfo, String serviceName, String serviceDdl);


    void updateServiceDdl(Integer serviceId, String serviceDdl);


    String getServiceDdl(Integer serviceId);
}
