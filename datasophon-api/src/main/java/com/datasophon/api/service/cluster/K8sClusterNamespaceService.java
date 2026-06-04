package com.datasophon.api.service.cluster;

import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author zhanghuangbin
 */
public interface K8sClusterNamespaceService extends IService<K8sClusterNamespace> {
    
    List<K8sClusterNamespace> listAndUpdateNamespaceByClusterId(Integer clusterId);
    
    K8sClusterNamespace getNamespace(K8sNamespaceIdentityDTO query);
    
    default K8sClusterNamespace createIfAbsent(K8sNamespaceIdentityDTO namespace) {
        return createIfAbsent(namespace, null);
    }
    
    K8sClusterNamespace createIfAbsent(K8sNamespaceIdentityDTO namespace, Integer state);
    
    void removeByClusterId(Integer clusterId);
}
