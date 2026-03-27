package com.datasophon.api.service.instance;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sServiceInstanceService extends IService<K8sServiceInstance> {

    List<K8sServiceInstanceVO> queryInstanceList(K8sNamespaceIdentityDTO query);

    List<String> listResourceType(K8sServiceInstanceQueryDTO query);

    Object listResource(K8sServiceInstanceQueryDTO query);



    K8sServiceInstance createIfAbsent(Integer clusterId, Integer namespaceId, Integer serviceId);
}
