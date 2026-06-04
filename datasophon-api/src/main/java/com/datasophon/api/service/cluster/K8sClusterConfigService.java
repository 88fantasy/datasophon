package com.datasophon.api.service.cluster;

import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author zhanghuangbin
 */
public interface K8sClusterConfigService extends IService<K8sClusterConfig> {
    
    K8sClusterConfig saveOrUpdateConfig(K8sClusterConfig config);
    
    K8sClusterConfig getByClusterId(Integer clusterId);
    
    K8sClusterConfig getInitConfig(Integer clusterId);
    
    void removeByClusterId(Integer clusterId);
}
