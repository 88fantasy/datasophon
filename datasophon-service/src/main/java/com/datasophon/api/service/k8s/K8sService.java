package com.datasophon.api.service.k8s;

import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.dao.entity.k8s.K8sClusterConfig;

/**
 * @author zhanghuangbin
 */
public interface K8sService {

    String READY = "Ready";

    K8sClusterStatus getState(K8sClusterConfig config);

    K8sConnectionResult testConnection(K8sClusterConfig config);
}
