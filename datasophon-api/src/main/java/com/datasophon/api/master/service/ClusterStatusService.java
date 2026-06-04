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

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import cn.hutool.core.collection.CollectionUtil;

/**
 * 集群整体状态巡检 Spring Service（替代 ClusterStatusActor）。
 *
 * <p>原 ClusterStatusActor.doOnReceive/execCheckCmd() 逻辑迁移至此；
 * 该 Actor 本身无 Pekka 依赖，转为 @Service 零逻辑改动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterStatusService {
    
    private final ClusterInfoService clusterInfoService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final K8sService k8sService;
    private final K8sClusterConfigService k8sClusterConfigService;
    
    /**
     * 检查所有集群（或指定集群）的运行状态。
     *
     * @param clusterId 若非 null，则只检查该集群；为 null 时检查全部就绪集群
     */
    public void checkClusterStatus(Integer clusterId) {
        List<ClusterInfoEntity> clusterList = new ArrayList<>();
        if (clusterId != null) {
            ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
            if (clusterInfo != null) {
                clusterList.add(clusterInfo);
            }
        } else {
            clusterList.addAll(clusterInfoService.getReadyClusterList());
        }
        
        for (ClusterInfoEntity cluster : clusterList) {
            if (ClusterArchType.physical.equals(cluster.getArchType())) {
                List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                        roleInstanceService.getServiceRoleInstanceListByClusterId(cluster.getId());
                if (roleInstanceList.isEmpty()) {
                    continue;
                }
                if (roleInstanceList.stream().allMatch(r -> ServiceRoleState.STOP.equals(r.getServiceRoleState()))) {
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
                } else {
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.RUNNING.getValue());
                }
            } else {
                K8sClusterConfig config = k8sClusterConfigService.getByClusterId(cluster.getId());
                try {
                    K8sClusterStatus status = k8sService.getState(config);
                    ClusterState state = ClusterState.STOP;
                    if (CollectionUtil.isNotEmpty(status.getNodes())) {
                        state = status.getNodes().stream().anyMatch(node -> "True".equals(node.getStatus()))
                                ? ClusterState.RUNNING
                                : ClusterState.STOP;
                    }
                    clusterInfoService.updateClusterState(cluster.getId(), state.getValue());
                } catch (Exception e) {
                    log.error("check cluster:{} status fail, {}", cluster.getClusterName(), e.getMessage(), e);
                    clusterInfoService.updateClusterState(cluster.getId(), ClusterState.STOP.getValue());
                }
            }
        }
    }
}
