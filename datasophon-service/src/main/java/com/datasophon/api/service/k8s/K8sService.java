package com.datasophon.api.service.k8s;

import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.api.vo.k8s.K8sNamespace;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sService {

    String READY = "Ready";

    K8sClusterStatus getState(K8sClusterConfig config);

    K8sConnectionResult testConnection(K8sClusterConfig config);


    /**
     * 获取 K8s 集群的命名空间及其状态
     *
     * @param config K8s 集群配置
     * @return 命名空间名称到状态的映射 (active/inactive)
     */
    List<K8sNamespace> listNamespaces(K8sClusterConfig config);
}
