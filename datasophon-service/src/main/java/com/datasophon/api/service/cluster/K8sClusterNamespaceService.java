package com.datasophon.api.service.cluster;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sClusterNamespaceService extends IService<K8sClusterNamespace> {

    List<K8sClusterNamespace> listAndUpdateNamespaceByClusterId(Integer clusterId);


}
