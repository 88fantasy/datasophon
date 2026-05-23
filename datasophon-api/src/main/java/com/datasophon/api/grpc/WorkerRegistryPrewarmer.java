/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.grpc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.domain.host.enums.HostState;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Master 启动时预热 {@link WorkerRegistry}，消除重启窗口期（H3 修复）。
 *
 * <p>从数据库查询所有已纳管（managed=YES）且处于 RUNNING 状态的主机，
 * 以默认 gRPC 端口（18082）预填注册表。预热条目在 90 秒内有效，
 * Worker 在首次心跳/注册时会覆盖为真实端点。</p>
 *
 * <p>这样 Master 重启后注册表不为空，避免 30~60 秒窗口期内 DAG 操作因
 * "Worker not registered" 而成片失败。</p>
 */
@Component
public class WorkerRegistryPrewarmer {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistryPrewarmer.class);

    /** 与 MasterRegistryClient.WORKER_GRPC_PORT 保持一致 */
    private static final int DEFAULT_WORKER_GRPC_PORT = 18082;

    private final WorkerRegistry workerRegistry;
    private final ClusterHostMapper clusterHostMapper;

    public WorkerRegistryPrewarmer(WorkerRegistry workerRegistry, ClusterHostMapper clusterHostMapper) {
        this.workerRegistry = workerRegistry;
        this.clusterHostMapper = clusterHostMapper;
    }

    @PostConstruct
    public void prewarm() {
        try {
            List<ClusterHostDO> hosts = clusterHostMapper.selectList(
                    new QueryWrapper<ClusterHostDO>()
                            .eq(Constants.MANAGED, 1)
                            .eq(Constants.HOST_STATE, HostState.RUNNING));

            if (hosts.isEmpty()) {
                log.info("WorkerRegistry prewarm: no managed+running hosts found in DB");
                return;
            }

            for (ClusterHostDO host : hosts) {
                workerRegistry.preRegister(host.getHostname(), DEFAULT_WORKER_GRPC_PORT, host.getClusterId());
            }
            log.info("WorkerRegistry prewarm: pre-registered {} hosts from DB (port={})",
                    hosts.size(), DEFAULT_WORKER_GRPC_PORT);
        } catch (Exception e) {
            // 预热失败为非致命错误：注册表仍可正常工作，Workers 在首次心跳时会主动注册
            log.warn("WorkerRegistry prewarm failed (non-fatal, workers will re-register via heartbeat): {}",
                    e.getMessage());
        }
    }
}
