/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.master;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import com.datasophon.api.configuration.TransportProperties;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.command.HostCheckCommand;
import com.datasophon.common.command.PingCommand;
import com.datasophon.common.model.HostInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PromInfoUtils;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.domain.host.enums.HostState;
import com.datasophon.domain.host.enums.MANAGED;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 节点状态监测
 */
public class HostCheckActor extends TypedActor<HostCheckCommand> {
    
    private static final Logger logger = LoggerFactory.getLogger(HostCheckActor.class);
    
    @Override
    protected void doOnReceive(HostCheckCommand hostCheckCommand) throws Throwable {
        logger.info("start to check host info");
        ClusterInfoService clusterInfoService = getBean(ClusterInfoService.class);
        // 获取当前安装并且正在运行的集群
        List<ClusterInfoEntity> clusterList = clusterInfoService.getReadyClusterList();
        for (ClusterInfoEntity cluster : clusterList) {
            if (ClusterArchType.physical.equals(cluster.getArchType())) {
                try {
                    checkCluster(cluster, hostCheckCommand.getHostInfo());
                } catch (Exception ex) {
                    logger.error("检查集群{}状态失败，{}", cluster.getClusterName(), ex.getMessage(), ex);
                }
            }
        }
    }

    private void checkCluster(ClusterInfoEntity clusterInfoEntity, HostInfo hostInfo) {
        ClusterHostService clusterHostService = getBean(ClusterHostService.class);
        ClusterServiceRoleInstanceService roleInstanceService = getBean(ClusterServiceRoleInstanceService.class);
        ClusterServiceRoleInstanceEntity prometheusInstance = roleInstanceService.getOneServiceRole("Prometheus", "", clusterInfoEntity.getId());


        List<ClusterHostDO> list = clusterHostService.getHostListByClusterId(clusterInfoEntity.getId());
        List<ClusterHostDO> updates = new ArrayList<>();
        for (ClusterHostDO clusterHostDO : list) {
            if (hostInfo != null && !StringUtils.equals(clusterHostDO.getHostname(), hostInfo.getHostname())) {
                // 指定了节点，直接只处理这一个节点的
                continue;
            }

            checkHostByPingPong(clusterHostDO);
            if (!HostState.OFFLINE.equals(clusterHostDO.getHostState())) {
                if (prometheusInstance != null && ServiceRoleState.RUNNING.equals(prometheusInstance.getServiceRoleState())) {
                    String promUrl = "http://" + prometheusInstance.getHostname() + ":9090/api/v1/query";
                    checkHostByPrometheus(clusterHostDO, promUrl);
                }
            }
            updates.add(clusterHostDO);
        }
        if (!updates.isEmpty()) {
            clusterHostService.updateBatchById(updates);
        }
    }

    private void checkHostByPingPong(ClusterHostDO host) {
        host.setCheckTime(new Date());
        ExecResult execResult = null;
        try {
            if (getBean(TransportProperties.class).isGrpcEnabled()) {
                // ── gRPC 路径 ──────────────────────────────────────────────
                execResult = getBean(WorkerCommandClient.class).ping(host.getHostname());
            } else {
                // ── Pekko 路径（默认，transport=pekko）─────────────────────
                final ActorRef pingActor = ActorUtils.getRemoteActor(host.getHostname(), "pingActor");
                PingCommand pingCommand = new PingCommand();
                pingCommand.setMessage("ping");
                Timeout timeout = new Timeout(Duration.create(180, TimeUnit.SECONDS));
                Future<Object> execFuture = Patterns.ask(pingActor, pingCommand, timeout);
                execResult = (ExecResult) Await.result(execFuture, timeout.duration());
            }
            host.setManaged(MANAGED.YES);
            if (execResult.getExecResult()) {
                host.setHostState(HostState.RUNNING);
                logger.info("ping host: {} success", host.getHostname());
            } else {
                host.setHostState(HostState.OFFLINE);
                host.setManaged(MANAGED.YES);
                logger.warn("ping host: {} fail, reason: {}", host.getHostname(), execResult.getExecOut());
            }
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                logger.warn("ping: {} timeout, it maybe offline", host.getHostname());
            } else {
                logger.error("ping host: {} error, cause: {}", host.getHostname(), e.getMessage());
            }
            host.setHostState(HostState.OFFLINE);
        }
    }

    private void checkHostByPrometheus(ClusterHostDO clusterHostDO, String promUrl) {
        try {
            String hostname = clusterHostDO.getHostname();
            // 查询内存总量
            String totalMemPromQl = "node_memory_MemTotal_bytes{job=~\"node\",instance=\"" + hostname + ":9100\"}/1024/1024/1024";
            String totalMemStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, totalMemPromQl);
            if (StringUtils.isNotBlank(totalMemStr)) {
                int totalMem = Double.valueOf(totalMemStr).intValue();
                clusterHostDO.setTotalMem(totalMem);
            }
            // 查询内存使用量
            String memAvailablePromQl = "node_memory_MemAvailable_bytes{job=~\"node\",instance=\"" + hostname + ":9100\"}/1024/1024/1024";
            String memAvailableStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, memAvailablePromQl);
            if (StringUtils.isNotBlank(memAvailableStr)) {
                int memAvailable = Double.valueOf(memAvailableStr).intValue();
                Integer memUsed = clusterHostDO.getTotalMem() - memAvailable;
                clusterHostDO.setUsedMem(memUsed);
            }
            // 总磁盘容量
            String totalDistPromQl = "sum(node_filesystem_size_bytes{instance=\"" + hostname
                                     + ":9100\",fstype=~\"ext4|xfs\",mountpoint !~\".*pod.*\"})/1024/1024/1024";
            String totalDiskStr = PromInfoUtils.getSinglePrometheusMetric(promUrl, totalDistPromQl);
            if (StringUtils.isNotBlank(totalDiskStr)) {
                int totalDisk = Double.valueOf(totalDiskStr).intValue();
                clusterHostDO.setTotalDisk(totalDisk);
            }
            // 查询磁盘使用量
            String diskUsedPromQl = "sum(node_filesystem_size_bytes{instance=\"" + hostname
                                    + ":9100\",fstype=~\"ext.*|xfs\",mountpoint !~\".*pod.*\"}-node_filesystem_free_bytes{instance=\""
                                    + hostname
                                    + ":9100\",fstype=~\"ext.*|xfs\",mountpoint !~\".*pod.*\"})/1024/1024/1024";
            String diskUsed = PromInfoUtils.getSinglePrometheusMetric(promUrl, diskUsedPromQl);
            if (StringUtils.isNotBlank(diskUsed)) {
                clusterHostDO.setUsedDisk(Double.valueOf(diskUsed).intValue());
            }
            // 查询cpu负载
            String cpuLoadPromQl = "node_load5{job=~\"node\",instance=\"" + hostname + ":9100\"}";
            String cpuLoad = PromInfoUtils.getSinglePrometheusMetric(promUrl, cpuLoadPromQl);
            if (StringUtils.isNotBlank(cpuLoad)) {
                clusterHostDO.setAverageLoad(cpuLoad);
            }
        } catch (Exception e) {
            logger.warn("check cluster state error, cause: {}", e.getMessage());
            clusterHostDO.setHostState(HostState.EXISTS_ALARM);
        }
    }

}
