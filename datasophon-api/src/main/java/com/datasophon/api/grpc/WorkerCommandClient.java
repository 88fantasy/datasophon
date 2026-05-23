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

import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.command.GenerateAlertConfigCommand;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.CreateUnixUserCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixUserCommand;
import com.datasophon.common.model.AlertConfigEntry;
import com.datasophon.common.model.ConfigFileEntry;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.grpc.api.AlertConfigRequest;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.FileOperateRequest;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.ServiceRoleRequest;
import com.datasophon.grpc.api.UnixGroupRequest;
import com.datasophon.grpc.api.UnixUserRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Master 端 gRPC 客户端：向目标 Worker 发送命令。
 *
 * <p>Channel 按 hostname 懒建并缓存。当 Worker 离线（注销、重注册、心跳超时）时，
 * {@link WorkerRegistry} 发布 {@link WorkerOfflineEvent}，本类通过 {@link EventListener}
 * 监听并立即关闭对应 Channel，防止连接句柄泄漏。Spring 销毁时关闭所有剩余 Channel。</p>
 */
@Component
public class WorkerCommandClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerCommandClient.class);

    private final WorkerRegistry workerRegistry;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    public WorkerCommandClient(WorkerRegistry workerRegistry, ObjectMapper objectMapper) {
        this.workerRegistry = workerRegistry;
        this.objectMapper = objectMapper;
    }

    // ─── Phase 1 API ─────────────────────────────────────────────────────────

    public ExecResult ping(String hostname) {
        return callWorker(hostname, "ping", () ->
                getStub(hostname)
                        .withDeadlineAfter(30, TimeUnit.SECONDS)
                        .ping(PingRequest.newBuilder().setMessage("ping").build()));
    }

    /** 执行命令列表（ExecuteCmdActor 模式）。 */
    public ExecResult executeCmd(String hostname, List<String> commands) {
        return callWorker(hostname, "executeCmd", () ->
                getStub(hostname)
                        .withDeadlineAfter(90, TimeUnit.SECONDS)
                        .executeCmd(ExecuteCmdRequest.newBuilder()
                                .addAllCommands(commands)
                                .build()));
    }

    /** 执行单行 shell 命令（RMStateActor / NMStateActor 模式）。 */
    public ExecResult executeCmdLine(String hostname, String commandLine) {
        return callWorker(hostname, "executeCmdLine", () ->
                getStub(hostname)
                        .withDeadlineAfter(90, TimeUnit.SECONDS)
                        .executeCmd(ExecuteCmdRequest.newBuilder()
                                .setCommandLine(commandLine)
                                .build()));
    }

    /** 读取 Worker 节点日志（LogActor 模式）。 */
    public ExecResult getLog(String hostname, String logFile, String baseDir) {
        return callWorker(hostname, "getLog", () ->
                getStub(hostname)
                        .withDeadlineAfter(30, TimeUnit.SECONDS)
                        .getLog(GetLogRequest.newBuilder()
                                .setLogFile(logFile)
                                .setBaseDir(baseDir)
                                .build()));
    }

    // ─── Phase 2 API ─────────────────────────────────────────────────────────

    /** 安装服务角色（对应 InstallServiceActor）。 */
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        return callWorker(hostname, "installServiceRole", () -> {
            String jsonPayload = objectMapper.writeValueAsString(cmd);
            ServiceRoleRequest req = ServiceRoleRequest.newBuilder()
                    .setServiceName(nullToEmpty(cmd.getServiceName()))
                    .setServiceRoleName(nullToEmpty(cmd.getServiceRoleName()))
                    .setJsonPayload(jsonPayload)
                    .build();
            return getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .installServiceRole(req);
        });
    }

    /**
     * 配置服务角色（对应 ConfigureServiceActor）。
     *
     * <p>由于 {@code cofigFileMap} 的 key 类型是 {@code Map<Generators, List<ServiceConfig>>}，
     * JSON 不支持对象 key，因此单独序列化为 {@code config_map_json}（List&lt;ConfigFileEntry&gt;）。</p>
     */
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        return callWorker(hostname, "configureServiceRole", () -> {
            String configMapJson = objectMapper.writeValueAsString(
                    ConfigFileEntry.fromMap(cmd.getCofigFileMap()));
            cmd.setCofigFileMap(null); // 避免 JSON 序列化 Map<Object, ...> key 问题
            String jsonPayload = objectMapper.writeValueAsString(cmd);
            ServiceRoleRequest req = ServiceRoleRequest.newBuilder()
                    .setServiceName(nullToEmpty(cmd.getServiceName()))
                    .setServiceRoleName(nullToEmpty(cmd.getServiceRoleName()))
                    .setJsonPayload(jsonPayload)
                    .setConfigMapJson(configMapJson)
                    .build();
            return getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .configureServiceRole(req);
        });
    }

    /** 启动服务角色（对应 StartServiceActor）。 */
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "startServiceRole", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .startServiceRole(buildServiceRoleRequest(cmd)));
    }

    /** 停止服务角色（对应 StopServiceActor）。 */
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "stopServiceRole", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .stopServiceRole(buildServiceRoleRequest(cmd)));
    }

    /** 重启服务角色（对应 RestartServiceActor）。 */
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "restartServiceRole", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .restartServiceRole(buildServiceRoleRequest(cmd)));
    }

    /** 检查服务角色状态（对应 ServiceStatusActor）。 */
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "serviceRoleStatus", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .serviceRoleStatus(buildServiceRoleRequest(cmd)));
    }

    // ─── Phase 3 API ─────────────────────────────────────────────────────────

    /** 创建 Unix 组（对应 UnixGroupActor create）。 */
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return callWorker(hostname, "createUnixGroup", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .createUnixGroup(UnixGroupRequest.newBuilder()
                                .setGroupName(nullToEmpty(cmd.getGroupName()))
                                .build()));
    }

    /** 删除 Unix 组（对应 UnixGroupActor delete）。 */
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return callWorker(hostname, "deleteUnixGroup", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .deleteUnixGroup(UnixGroupRequest.newBuilder()
                                .setGroupName(nullToEmpty(cmd.getGroupName()))
                                .build()));
    }

    /** 创建 Unix 用户（对应 UnixUserActor create）。 */
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return callWorker(hostname, "createUnixUser", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .createUnixUser(UnixUserRequest.newBuilder()
                                .setUsername(nullToEmpty(cmd.getUsername()))
                                .setMainGroup(nullToEmpty(cmd.getMainGroup()))
                                .setOtherGroups(nullToEmpty(cmd.getOtherGroups()))
                                .build()));
    }

    /** 删除 Unix 用户（对应 UnixUserActor delete）。 */
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return callWorker(hostname, "deleteUnixUser", () ->
                getStub(hostname)
                        .withDeadlineAfter(180, TimeUnit.SECONDS)
                        .deleteUnixUser(UnixUserRequest.newBuilder()
                                .setUsername(nullToEmpty(cmd.getUsername()))
                                .build()));
    }

    /** 文件写入操作（对应 FileOperateActor）。 */
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        return callWorker(hostname, "operateFile", () -> {
            FileOperateRequest.Builder reqBuilder = FileOperateRequest.newBuilder()
                    .setPath(nullToEmpty(cmd.getPath()))
                    .setContent(nullToEmpty(cmd.getContent()));
            if (cmd.getLines() != null) {
                reqBuilder.addAllLines(cmd.getLines());
            }
            return getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .operateFile(reqBuilder.build());
        });
    }

    /**
     * 生成告警配置（对应 AlertConfigActor）。
     *
     * <p>{@code configFileMap} 的 key 是 {@link com.datasophon.common.model.Generators}
     * 对象，不可直接作为 JSON key，故通过 {@link AlertConfigEntry} 桥接列表序列化。</p>
     */
    public ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd) {
        return callWorker(hostname, "generateAlertConfig", () -> {
            String configMapJson = objectMapper.writeValueAsString(
                    AlertConfigEntry.fromMap(cmd.getConfigFileMap()));
            AlertConfigRequest req = AlertConfigRequest.newBuilder()
                    .setClusterId(cmd.getClusterId() != null ? cmd.getClusterId() : 0)
                    .setConfigMapJson(configMapJson)
                    .build();
            return getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .generateAlertConfig(req);
        });
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

    /**
     * 监听 Worker 离线事件（注销、重注册、心跳超时），立即关闭并移除对应 Channel。
     * 这是 H1（Channel 泄漏）的核心修复：Channel 生命周期与 WorkerEndpoint 保持同步。
     */
    @EventListener
    public void onWorkerOffline(WorkerOfflineEvent event) {
        ManagedChannel channel = channelCache.remove(event.getHostname());
        if (channel != null) {
            channel.shutdown();
            log.info("gRPC channel closed for offline worker: {}", event.getHostname());
        }
    }

    @PreDestroy
    public void destroy() {
        channelCache.forEach((hostname, channel) -> {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        });
        channelCache.clear();
        log.info("WorkerCommandClient: all channels closed");
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    /**
     * 统一的 gRPC 调用包装器，消除各方法中的重复 try-catch。
     *
     * <ul>
     *   <li>{@link StatusRuntimeException} / {@link IllegalStateException} → warn 级日志（预期错误）</li>
     *   <li>其他 {@link Exception}（如 JSON 序列化失败）→ error 级日志（非预期）</li>
     * </ul>
     */
    private ExecResult callWorker(String hostname, String method, WorkerCall<ExecResultPb> call) {
        try {
            return toExecResult(call.call());
        } catch (StatusRuntimeException e) {
            log.warn("gRPC {} to {} failed: {}", method, hostname, e.getStatus());
            return ExecResult.error("gRPC " + method + " failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC {} to {} failed: {}", method, hostname, e.getMessage());
            return ExecResult.error("gRPC " + method + " failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC {} to {} failed: {}", method, hostname, e.getMessage(), e);
            return ExecResult.error("gRPC " + method + " failed: " + e.getMessage());
        }
    }

    /** 供 callWorker 使用的 checked-exception 版 Supplier。 */
    @FunctionalInterface
    private interface WorkerCall<T> {
        T call() throws Exception;
    }

    /** 将 ServiceRoleOperateCommand 序列化为 ServiceRoleRequest proto。 */
    private ServiceRoleRequest buildServiceRoleRequest(ServiceRoleOperateCommand cmd) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(cmd);
            return ServiceRoleRequest.newBuilder()
                    .setServiceName(nullToEmpty(cmd.getServiceName()))
                    .setServiceRoleName(nullToEmpty(cmd.getServiceRoleName()))
                    .setJsonPayload(jsonPayload)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ServiceRoleOperateCommand", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * 构建到指定 Worker 的 gRPC Channel。
     * 测试子类可重写此方法注入 in-process channel，无需真实 TCP 连接。
     */
    protected ManagedChannel buildChannel(String hostname, int port) {
        return ManagedChannelBuilder.forAddress(hostname, port)
                .usePlaintext()
                .build();
    }

    private WorkerCommandServiceGrpc.WorkerCommandServiceBlockingStub getStub(String hostname) {
        WorkerEndpoint endpoint = workerRegistry.getEndpoint(hostname)
                .orElseThrow(() -> new IllegalStateException(
                        "Worker not registered in gRPC registry: " + hostname));
        ManagedChannel channel = channelCache.computeIfAbsent(hostname, h ->
                buildChannel(endpoint.getHostname(), endpoint.getGrpcPort()));
        return WorkerCommandServiceGrpc.newBlockingStub(channel);
    }

    private static ExecResult toExecResult(ExecResultPb pb) {
        ExecResult result = new ExecResult();
        result.setExecResult(pb.getExecResult());
        result.setExecOut(pb.getExecOut());
        result.setExecErrOut(pb.getExecErrOut());
        return result;
    }
}
