package com.datasophon.api.master.handler.k8s;

import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class K8sAgentUninstallHandler extends K8sAgentHandler {
    
    @Override
    public void execute(K8sClusterConfig config) {
        log.info("开始卸载 K8s Agent: release={}, namespace={}", RELEASE_NAME, NAMESPACE);
        
        try (HelmClient client = buildHelmClient(config)) {
            // 执行 helm uninstall
            UninstallParams uninstallParams = new UninstallParams();
            uninstallParams.setReleaseName(RELEASE_NAME);
            uninstallParams.setNamespace(NAMESPACE);
            uninstallParams.setKeepHistory(false);
            
            client.uninstall(uninstallParams);
            
            log.info("集群{}， K8s Agent 卸载成功：{}", config.getClusterId(), RELEASE_NAME);
        } catch (Exception e) {
            log.error("集群{}， K8s Agent 卸载失败：{}", config.getClusterId(), RELEASE_NAME, e);
            throw new IllegalStateException("卸载 K8s Agent 失败：" + e.getMessage(), e);
        }
    }
    
}
