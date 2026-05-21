package com.datasophon.api.service.cluster;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

/**
 * @author zhanghuangbin
 */
public interface K8sClusterConfigService extends IService<K8sClusterConfig> {


    K8sClusterConfig saveOrUpdateConfig(K8sClusterConfig config);

    K8sClusterConfig getByClusterId(Integer clusterId);

    K8sClusterConfig getInitConfig(Integer clusterId);

    void removeByClusterId(Integer clusterId);
}
