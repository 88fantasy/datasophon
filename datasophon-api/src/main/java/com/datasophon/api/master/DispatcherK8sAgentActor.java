package com.datasophon.api.master;

import cn.hutool.core.collection.CollectionUtil;
import com.datasophon.api.master.handler.k8s.K8sAgentInstallHandler;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.common.command.DispatcherK8sAgentCommand;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterState;
import lombok.extern.slf4j.Slf4j;

/**
 * 安装 k8s agent
 * @author zhanghuangbin
 */
@Slf4j
public class DispatcherK8sAgentActor extends TypedActor<DispatcherK8sAgentCommand> {


    @Override
    protected void doOnReceive(DispatcherK8sAgentCommand message) throws Throwable {
        ClusterInfoService clusterInfoService = getBean(ClusterInfoService.class);
        K8sClusterConfigService k8sClusterConfigService = getBean(K8sClusterConfigService.class);


        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(message.getClusterId());
        ClusterInfoEntity cluster = clusterInfoService.getById(config.getClusterId());
        if (updateClusterState(cluster, config)) {
            doInstallAgent(config);
        } else {
            log.warn("cluster {} is not running, ignore install agent", cluster.getClusterName());
        }

    }

    private boolean updateClusterState(ClusterInfoEntity cluster, K8sClusterConfig config) {
        ClusterInfoService clusterInfoService = getBean(ClusterInfoService.class);
        try {
            K8sService k8sService = getBean(K8sService.class);
            K8sClusterStatus status = k8sService.getState(config);

            ClusterState state = ClusterState.STOP;
            if (CollectionUtil.isNotEmpty(status.getNodes())) {
                state = status.getNodes().stream().anyMatch(node -> "True".equals(node.getStatus())) ? ClusterState.RUNNING : ClusterState.STOP;
            }

            clusterInfoService.updateClusterState(config.getClusterId(), state.getValue());

            return ClusterState.RUNNING.equals(state);
        } catch (Exception e) {
            log.error("check cluster:{} status fail, {}", cluster.getClusterName(), e.getMessage(), e);
            clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());

            return false;
        }
    }

    private void doInstallAgent(K8sClusterConfig config) {
        new K8sAgentInstallHandler().execute(config);
    }
}
