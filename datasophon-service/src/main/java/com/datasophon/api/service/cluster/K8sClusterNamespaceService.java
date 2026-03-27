package com.datasophon.api.service.cluster;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sClusterNamespaceService extends IService<K8sClusterNamespace> {

    List<K8sClusterNamespace> listAndUpdateNamespaceByClusterId(Integer clusterId);



    K8sClusterNamespace getNamespace(K8sNamespaceIdentityDTO query);

    K8sClusterNamespace createIfAbsent(K8sNamespaceIdentityDTO namespace);

}
