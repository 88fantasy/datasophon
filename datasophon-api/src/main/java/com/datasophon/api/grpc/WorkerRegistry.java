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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Master 端的 Worker 注册表。
 * <p>
 * 维护一个 hostname → WorkerEndpoint 的内存映射。
 * WorkerRegistryGrpcService 在收到 Register / Heartbeat / Unregister 请求时操作此表。
 * </p>
 * <p>
 * 心跳超时判定：连续 3 次心跳间隔（默认 90s）未收到心跳，视为 worker 离线。
 * </p>
 */
@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    /** 心跳超时阈值：3 个心跳周期（30s * 3 = 90s） */
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, WorkerEndpoint> registry = new ConcurrentHashMap<>();

    /**
     * 注册 Worker 节点。若已存在则更新端口和心跳时间。
     */
    public void register(WorkerEndpoint endpoint) {
        registry.put(endpoint.getHostname(), endpoint);
        log.info("Worker registered: hostname={}, port={}, arch={}",
                endpoint.getHostname(), endpoint.getGrpcPort(), endpoint.getCpuArchitecture());
    }

    /**
     * 更新心跳时间。若 worker 不在注册表中，返回 false 触发重新注册。
     */
    public boolean heartbeat(String hostname) {
        WorkerEndpoint endpoint = registry.get(hostname);
        if (endpoint == null) {
            log.warn("Heartbeat received from unregistered worker: {}", hostname);
            return false;
        }
        endpoint.touch();
        return true;
    }

    /**
     * 注销 Worker 节点。
     */
    public void unregister(String hostname) {
        WorkerEndpoint removed = registry.remove(hostname);
        if (removed != null) {
            log.info("Worker unregistered: hostname={}", hostname);
        }
    }

    /**
     * 获取指定 hostname 的 Worker 端点信息。
     *
     * @return 非空 Optional 表示 worker 已注册且在线
     */
    public Optional<WorkerEndpoint> getEndpoint(String hostname) {
        WorkerEndpoint ep = registry.get(hostname);
        if (ep == null) {
            return Optional.empty();
        }
        if (isTimedOut(ep)) {
            log.warn("Worker {} heartbeat timed out (last seen: {}), treating as offline",
                    hostname, ep.getLastHeartbeat());
            return Optional.empty();
        }
        return Optional.of(ep);
    }

    /**
     * 返回所有已注册的在线 Worker 列表（过滤超时节点）。
     */
    public Collection<WorkerEndpoint> getAllOnline() {
        Instant cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT);
        return registry.values().stream()
                .filter(ep -> ep.getLastHeartbeat().isAfter(cutoff))
                .toList();
    }

    private boolean isTimedOut(WorkerEndpoint ep) {
        return ep.getLastHeartbeat().isBefore(Instant.now().minus(HEARTBEAT_TIMEOUT));
    }
}
