package com.datasophon.api.master.handler.k8s;

import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

/**
 * @author zhanghuangbin
 */
public abstract class K8sAgentHandler {
    
    public static final String RELEASE_NAME = "datasophon-k8s-agent";
    public static final String HELM_CHART = "datasophon-k8s-agent";
    public static final String NAMESPACE = "vos";
    protected K8sClusterNamespaceService k8sClusterNamespaceService;
    
    protected K8sService k8sService;
    
    public K8sAgentHandler() {
        k8sClusterNamespaceService = getBean(K8sClusterNamespaceService.class);
        k8sService = getBean(K8sService.class);
    }
    
    public abstract void execute(K8sClusterConfig config);
    
    /**
     * 根据 K8sClusterConfig 构建 Helm Client 配置
     */
    protected HelmClient buildHelmClient(K8sClusterConfig config) {
        ClientOptions options = new ClientOptions();
        options.setKubeConfig(config.getKubeConfig());
        options.setToken(config.getToken());
        options.setUsername(config.getUsername());
        options.setPassword(config.getPassword());
        options.setServerCert(config.getServerCert());
        options.setServerName(config.getServerHost());
        return new HelmClient(options);
    }
    
    protected <E> E getBean(Class<E> clazz) {
        return SpringTool.getApplicationContext().getBean(clazz);
    }
}
