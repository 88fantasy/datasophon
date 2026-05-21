package com.datasophon.api.service.agent;

/**
 * @author zhanghuangbin
 */
public interface K8sAgentClientService {

    <T> T call(Integer clusterId, String url, Object payload, Class<T> responseType);


}
