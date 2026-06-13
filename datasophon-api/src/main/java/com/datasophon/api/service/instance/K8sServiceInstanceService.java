package com.datasophon.api.service.instance;

import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author zhanghuangbin
 */
public interface K8sServiceInstanceService extends IService<K8sServiceInstance> {
    
    List<K8sServiceInstanceVO> queryInstanceList(K8sNamespaceIdentityDTO query);
    
    List<K8sServiceInstanceVO> listByIds(List<Integer> instanceIds);
    
    default Optional<String> getServiceName(Integer instanceId) {
        return getVoById(instanceId).map(K8sServiceInstanceVO::getServiceName);
    }
    
    default Optional<K8sServiceInstanceVO> getVoById(Integer instanceId) {
        List<K8sServiceInstanceVO> result = listByIds(Collections.singletonList(instanceId));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
    
    List<String> listResourceType(K8sServiceInstanceQueryDTO query);
    
    Object listResource(K8sServiceInstanceQueryDTO query);
    
    K8sServiceInstance createIfAbsent(Integer clusterId, Integer namespaceId, Integer serviceId);
    
    boolean removeInstanceId(Integer instanceId);
    
    void removeByClusterId(Integer clusterId);
    
    boolean hasRunningInstance(Integer clusterId);
}
