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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
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
 * 超时或注销时发布 {@link WorkerOfflineEvent}，由 {@link WorkerCommandClient}
 * 监听以关闭对应的 gRPC Channel，防止连接句柄泄漏。
 * </p>
 */
@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    /** 心跳超时阈值：3 个心跳周期（30s * 3 = 90s） */
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, WorkerEndpoint> registry = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public WorkerRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Master 启动时预热注册表：根据 DB 中已管理的主机列表，以默认端口预填端点。
     *
     * <p>预热条目的 {@link WorkerEndpoint#getLastHeartbeat()} 设为当前时间，
     * 在 {@link #HEARTBEAT_TIMEOUT}（90s）内有效；Worker 真实注册后会覆盖该条目。
     * 预热时不发布 {@link WorkerOfflineEvent}（没有旧 Channel 需要清理）。</p>
     *
     * @param hostname  Worker 主机名
     * @param grpcPort  Worker gRPC 端口（通常 18082）
     * @param clusterId 所属集群 ID
     */
    public void preRegister(String hostname, int grpcPort, int clusterId) {
        // 仅在节点尚未注册时才预热，避免覆盖已在线 Worker 的真实端点
        registry.putIfAbsent(hostname, new WorkerEndpoint(hostname, grpcPort, "", clusterId));
        log.debug("Worker pre-registered from DB: hostname={}, port={}", hostname, grpcPort);
    }

    /**
     * 注册 Worker 节点。
     * 若同名节点已存在（如 worker 重启后端口变更），先发布 {@link WorkerOfflineEvent}
     * 以关闭旧 Channel，再写入新端点。
     */
    public void register(WorkerEndpoint endpoint) {
        WorkerEndpoint old = registry.put(endpoint.getHostname(), endpoint);
        if (old != null) {
            // worker 重新注册（重启/端口变更）—— 旧 channel 需要关闭
            log.info("Worker re-registered, closing old channel: hostname={}", endpoint.getHostname());
            eventPublisher.publishEvent(new WorkerOfflineEvent(this, endpoint.getHostname()));
        }
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
     * 注销 Worker 节点，并发布 {@link WorkerOfflineEvent} 关闭其 gRPC Channel。
     */
    public void unregister(String hostname) {
        WorkerEndpoint removed = registry.remove(hostname);
        if (removed != null) {
            log.info("Worker unregistered: hostname={}", hostname);
            eventPublisher.publishEvent(new WorkerOfflineEvent(this, hostname));
        }
    }

    /**
     * 获取指定 hostname 的 Worker 端点信息。
     * 若心跳超时，主动从注册表移除并发布 {@link WorkerOfflineEvent}。
     *
     * @return 非空 Optional 表示 worker 已注册且在线
     */
    public Optional<WorkerEndpoint> getEndpoint(String hostname) {
        WorkerEndpoint ep = registry.get(hostname);
        if (ep == null) {
            return Optional.empty();
        }
        if (isTimedOut(ep)) {
            log.warn("Worker {} heartbeat timed out (last seen: {}), evicting from registry",
                    hostname, ep.getLastHeartbeat());
            // 条件移除：只有值未被其他线程更新时才移除，避免误删刚重新注册的端点
            if (registry.remove(hostname, ep)) {
                eventPublisher.publishEvent(new WorkerOfflineEvent(this, hostname));
            }
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
