/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package com.datasophon.api.master.service;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * K8s Agent 安装 Spring Service，业务逻辑来自 {@link DispatcherK8sAgentActor}。
 */
@Slf4j
@Service
public class DispatcherK8sAgentService {

    private final ClusterInfoService clusterInfoService;
    private final K8sClusterConfigService k8sClusterConfigService;
    private final K8sService k8sService;

    public DispatcherK8sAgentService(ClusterInfoService clusterInfoService,
                                      K8sClusterConfigService k8sClusterConfigService,
                                      K8sService k8sService) {
        this.clusterInfoService = clusterInfoService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sService = k8sService;
    }

    /**
     * 异步安装 K8s Agent（替代 DispatcherK8sAgentActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void dispatchK8sAgent(DispatcherK8sAgentCommand command) {
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(command.getClusterId());
        ClusterInfoEntity cluster = clusterInfoService.getById(config.getClusterId());
        if (updateClusterState(cluster, config)) {
            new K8sAgentInstallHandler().execute(config);
        } else {
            log.warn("cluster {} is not running, ignore install agent", cluster.getClusterName());
        }
    }

    private boolean updateClusterState(ClusterInfoEntity cluster, K8sClusterConfig config) {
        try {
            K8sClusterStatus status = k8sService.getState(config);
            ClusterState state = ClusterState.STOP;
            if (CollectionUtil.isNotEmpty(status.getNodes())) {
                state = status.getNodes().stream()
                        .anyMatch(node -> "True".equals(node.getStatus()))
                        ? ClusterState.RUNNING : ClusterState.STOP;
            }
            clusterInfoService.updateClusterState(config.getClusterId(), state.getValue());
            return ClusterState.RUNNING.equals(state);
        } catch (Exception e) {
            log.error("check cluster:{} status fail, {}", cluster.getClusterName(), e.getMessage(), e);
            clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
            return false;
        }
    }
}
