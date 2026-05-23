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

import org.springframework.context.ApplicationEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkerRegistry 单元测试（纯 Java，无 Spring、无网络）。
 *
 * <p>覆盖注册 / 心跳 / 注销 / 超时 四个状态转移场景，
 * 以及 WorkerOfflineEvent 发布时机验证（H1 修复）。</p>
 */
class WorkerRegistryTest {

    /** 捕获 WorkerRegistry 发布的事件，用于断言 WorkerOfflineEvent 时机。 */
    private final List<ApplicationEvent> publishedEvents = new ArrayList<>();
    private WorkerRegistry registry;

    @BeforeEach
    void setUp() {
        publishedEvents.clear();
        // ApplicationEventPublisher 是 @FunctionalInterface，可用 lambda 替代
        registry = new WorkerRegistry(event -> publishedEvents.add((ApplicationEvent) event));
    }

    // ─── 注册 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register 后 getEndpoint 可取到 endpoint")
    void register_thenGetEndpoint_returnsEndpoint() {
        WorkerEndpoint ep = new WorkerEndpoint("host1", 18082, "x86_64", 1);
        registry.register(ep);

        Optional<WorkerEndpoint> result = registry.getEndpoint("host1");
        assertThat(result).isPresent();
        assertThat(result.get().getHostname()).isEqualTo("host1");
        assertThat(result.get().getGrpcPort()).isEqualTo(18082);
    }

    @Test
    @DisplayName("未注册的 hostname getEndpoint 返回 empty")
    void getEndpoint_notRegistered_returnsEmpty() {
        assertThat(registry.getEndpoint("unknown-host")).isEmpty();
    }

    @Test
    @DisplayName("重复 register 同一 hostname 不产生多条记录（覆盖更新）且发布 WorkerOfflineEvent")
    void register_sameHostname_overwrites() {
        registry.register(new WorkerEndpoint("host2", 18082, "x86_64", 1));
        // 第一次注册不应发布事件
        assertThat(publishedEvents).isEmpty();

        registry.register(new WorkerEndpoint("host2", 18083, "aarch64", 2));
        // 第二次注册（重注册）应发布离线事件以关闭旧 Channel
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(WorkerOfflineEvent.class);
        assertThat(((WorkerOfflineEvent) publishedEvents.get(0)).getHostname()).isEqualTo("host2");

        Optional<WorkerEndpoint> result = registry.getEndpoint("host2");
        assertThat(result).isPresent();
        assertThat(result.get().getGrpcPort()).isEqualTo(18083);
    }

    // ─── 心跳 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("heartbeat 更新 lastHeartbeat 时间戳，返回 true")
    void heartbeat_knownHost_updatesTimestampAndReturnsTrue() throws InterruptedException {
        WorkerEndpoint ep = new WorkerEndpoint("host3", 18082, "x86_64", 1);
        registry.register(ep);
        Instant before = ep.getLastHeartbeat();

        Thread.sleep(10); // 保证时间戳有变化
        boolean ok = registry.heartbeat("host3");

        assertThat(ok).isTrue();
        assertThat(ep.getLastHeartbeat()).isAfter(before);
    }

    @Test
    @DisplayName("未注册 hostname heartbeat 返回 false")
    void heartbeat_unknownHost_returnsFalse() {
        assertThat(registry.heartbeat("not-registered")).isFalse();
    }

    // ─── 注销 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unregister 后 getEndpoint 返回 empty，且发布 WorkerOfflineEvent")
    void unregister_thenGetEndpoint_returnsEmpty() {
        registry.register(new WorkerEndpoint("host4", 18082, "x86_64", 1));
        publishedEvents.clear(); // 忽略 register 阶段可能的事件

        registry.unregister("host4");

        assertThat(registry.getEndpoint("host4")).isEmpty();
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(WorkerOfflineEvent.class);
        assertThat(((WorkerOfflineEvent) publishedEvents.get(0)).getHostname()).isEqualTo("host4");
    }

    // ─── 超时 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("心跳超时（>90s）时 getEndpoint 返回 empty，并主动从注册表移除、发布 WorkerOfflineEvent")
    void getEndpoint_heartbeatTimedOut_returnsEmpty() throws Exception {
        WorkerEndpoint ep = new WorkerEndpoint("host5", 18082, "x86_64", 1);
        registry.register(ep);
        publishedEvents.clear();

        // 将 lastHeartbeat 强制设为 100s 前（超过 90s 阈值）
        setLastHeartbeat(ep, Instant.now().minusSeconds(100));

        assertThat(registry.getEndpoint("host5")).isEmpty();
        // 超时后应主动发布离线事件以关闭 Channel（H1 修复）
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(WorkerOfflineEvent.class);
        assertThat(((WorkerOfflineEvent) publishedEvents.get(0)).getHostname()).isEqualTo("host5");
        // 超时 endpoint 应从注册表中移除
        assertThat(registry.getAllOnline()).isEmpty();
    }

    @Test
    @DisplayName("getAllOnline 过滤超时节点，只返回在线节点")
    void getAllOnline_filtersTimedOutWorkers() throws Exception {
        WorkerEndpoint alive = new WorkerEndpoint("host6", 18082, "x86_64", 1);
        WorkerEndpoint timedOut = new WorkerEndpoint("host7", 18082, "x86_64", 1);
        registry.register(alive);
        registry.register(timedOut);

        setLastHeartbeat(timedOut, Instant.now().minusSeconds(100));

        Collection<WorkerEndpoint> online = registry.getAllOnline();
        assertThat(online).hasSize(1);
        assertThat(online.iterator().next().getHostname()).isEqualTo("host6");
    }

    // ─── preRegister（H3 修复）────────────────────────────────────────────────

    @Test
    @DisplayName("preRegister 后 getEndpoint 可取到端点（Master 重启预热场景）")
    void preRegister_thenGetEndpoint_returnsEndpoint() {
        registry.preRegister("host-pre", 18082, 1);

        Optional<WorkerEndpoint> result = registry.getEndpoint("host-pre");
        assertThat(result).isPresent();
        assertThat(result.get().getHostname()).isEqualTo("host-pre");
        assertThat(result.get().getGrpcPort()).isEqualTo(18082);
    }

    @Test
    @DisplayName("preRegister 不发布 WorkerOfflineEvent（预热不是替换离线节点）")
    void preRegister_doesNotPublishOfflineEvent() {
        registry.preRegister("host-pre2", 18082, 1);
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    @DisplayName("preRegister 若节点已真实注册则 putIfAbsent 不覆盖，register 真实更新时才发 OfflineEvent")
    void preRegister_doesNotOverwriteExistingRealEndpoint() {
        // 真实注册
        registry.register(new WorkerEndpoint("host-pre3", 18082, "x86_64", 1));
        publishedEvents.clear();

        // 预热不应覆盖真实注册
        registry.preRegister("host-pre3", 19999, 1);
        assertThat(registry.getEndpoint("host-pre3").get().getGrpcPort()).isEqualTo(18082);
        assertThat(publishedEvents).isEmpty(); // putIfAbsent → 不触发 OfflineEvent
    }

    @Test
    @DisplayName("preRegister 后 Worker 真实注册会覆盖预热条目并发布 WorkerOfflineEvent")
    void preRegister_thenRealRegister_overwritesAndPublishesEvent() {
        registry.preRegister("host-pre4", 18082, 1);
        publishedEvents.clear(); // 预热不发事件

        // Worker 真实注册覆盖预热条目
        registry.register(new WorkerEndpoint("host-pre4", 18082, "x86_64", 1));
        // register() 发现 putIfAbsent 条目存在 → 视为旧端点发 OfflineEvent（关旧 Channel）
        assertThat(publishedEvents).hasSize(1);
        assertThat(((WorkerOfflineEvent) publishedEvents.get(0)).getHostname()).isEqualTo("host-pre4");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** 通过反射设置 WorkerEndpoint.lastHeartbeat，模拟心跳超时场景。 */
    private static void setLastHeartbeat(WorkerEndpoint ep, Instant instant) throws Exception {
        Field field = WorkerEndpoint.class.getDeclaredField("lastHeartbeat");
        field.setAccessible(true);
        field.set(ep, instant);
    }
}
