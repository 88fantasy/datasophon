package com.datasophon.api.master;

import com.datasophon.api.service.agent.K8sAgentDeployService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.common.command.DispatcherK8sAgentCommand;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class DispatchK8sAgentActor extends TypedActor<DispatcherK8sAgentCommand> {


    @Override
    protected void doOnReceive(DispatcherK8sAgentCommand message) throws Throwable {
        K8sClusterConfigService k8sClusterConfigService = getBean(K8sClusterConfigService.class);
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(message.getClusterId());

        doInstallAgent(config);
    }

    private void doInstallAgent(K8sClusterConfig config) {
        K8sAgentDeployService k8sAgentDeployService = getBean(K8sAgentDeployService.class);
        k8sAgentDeployService.deployAgent(config);
    }
}
