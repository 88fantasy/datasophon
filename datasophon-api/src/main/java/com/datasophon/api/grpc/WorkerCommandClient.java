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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Master 端 gRPC 客户端：向目标 Worker 发送命令（Phase 1）。
 *
 * <p>Channel 按 hostname 缓存，Spring 销毁时统一关闭。
 * 调用方先通过 {@link TransportProperties#isGrpcEnabled()} 判断是否走 gRPC，
 * 不走 gRPC 时不调用此 Bean。</p>
 *
 * <p>Phase 1 方法：</p>
 * <ul>
 *   <li>{@link #ping(String)}                         — 对应 PingActor</li>
 *   <li>{@link #executeCmd(String, List)}              — 对应 ExecuteCmdActor（commands 列表）</li>
 *   <li>{@link #executeCmdLine(String, String)}        — 对应 RMStateActor / NMStateActor（单行命令）</li>
 *   <li>{@link #getLog(String, String, String)}        — 对应 LogActor</li>
 * </ul>
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
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .ping(PingRequest.newBuilder().setMessage("ping").build());
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC ping to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC ping failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC ping to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC ping failed: " + e.getMessage());
        }
    }

    /** 执行命令列表（ExecuteCmdActor 模式）。 */
    public ExecResult executeCmd(String hostname, List<String> commands) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(90, TimeUnit.SECONDS)
                    .executeCmd(ExecuteCmdRequest.newBuilder()
                            .addAllCommands(commands)
                            .build());
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC executeCmd to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC executeCmd failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC executeCmd to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC executeCmd failed: " + e.getMessage());
        }
    }

    /** 执行单行 shell 命令（RMStateActor / NMStateActor 模式）。 */
    public ExecResult executeCmdLine(String hostname, String commandLine) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(90, TimeUnit.SECONDS)
                    .executeCmd(ExecuteCmdRequest.newBuilder()
                            .setCommandLine(commandLine)
                            .build());
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC executeCmdLine to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC executeCmdLine failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC executeCmdLine to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC executeCmdLine failed: " + e.getMessage());
        }
    }

    /** 读取 Worker 节点日志（LogActor 模式）。 */
    public ExecResult getLog(String hostname, String logFile, String baseDir) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .getLog(GetLogRequest.newBuilder()
                            .setLogFile(logFile)
                            .setBaseDir(baseDir)
                            .build());
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC getLog to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC getLog failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC getLog to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC getLog failed: " + e.getMessage());
        }
    }

    // ─── Phase 2 API ─────────────────────────────────────────────────────────

    /** 安装服务角色（对应 InstallServiceActor）。 */
    public ExecResult installServiceRole(String hostname, InstallServiceRoleCommand cmd) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(cmd);
            ServiceRoleRequest req = ServiceRoleRequest.newBuilder()
                    .setServiceName(nullToEmpty(cmd.getServiceName()))
                    .setServiceRoleName(nullToEmpty(cmd.getServiceRoleName()))
                    .setJsonPayload(jsonPayload)
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .installServiceRole(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC installServiceRole to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC installServiceRole failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC installServiceRole to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC installServiceRole failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC installServiceRole to {} serialization failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC installServiceRole serialization failed: " + e.getMessage());
        }
    }

    /**
     * 配置服务角色（对应 ConfigureServiceActor）。
     *
     * <p>由于 {@code cofigFileMap} 的 key 类型是 {@code Map<Generators, List<ServiceConfig>>}，
     * JSON 不支持对象 key，因此单独序列化为 {@code config_map_json}（List&lt;ConfigFileEntry&gt;）。</p>
     */
    public ExecResult configureServiceRole(String hostname, GenerateServiceConfigCommand cmd) {
        try {
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
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .configureServiceRole(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC configureServiceRole to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC configureServiceRole failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC configureServiceRole to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC configureServiceRole failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC configureServiceRole to {} serialization failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC configureServiceRole serialization failed: " + e.getMessage());
        }
    }

    /** 启动服务角色（对应 StartServiceActor）。 */
    public ExecResult startServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .startServiceRole(buildServiceRoleRequest(cmd));
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC startServiceRole to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC startServiceRole failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC startServiceRole to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC startServiceRole failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC startServiceRole to {} failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC startServiceRole failed: " + e.getMessage());
        }
    }

    /** 停止服务角色（对应 StopServiceActor）。 */
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .stopServiceRole(buildServiceRoleRequest(cmd));
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC stopServiceRole to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC stopServiceRole failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC stopServiceRole to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC stopServiceRole failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC stopServiceRole to {} failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC stopServiceRole failed: " + e.getMessage());
        }
    }

    /** 重启服务角色（对应 RestartServiceActor）。 */
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .restartServiceRole(buildServiceRoleRequest(cmd));
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC restartServiceRole to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC restartServiceRole failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC restartServiceRole to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC restartServiceRole failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC restartServiceRole to {} failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC restartServiceRole failed: " + e.getMessage());
        }
    }

    /** 检查服务角色状态（对应 ServiceStatusActor）。 */
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        try {
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .serviceRoleStatus(buildServiceRoleRequest(cmd));
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC serviceRoleStatus to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC serviceRoleStatus failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC serviceRoleStatus to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC serviceRoleStatus failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC serviceRoleStatus to {} failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC serviceRoleStatus failed: " + e.getMessage());
        }
    }

    // ─── Phase 3 API ─────────────────────────────────────────────────────────

    /** 创建 Unix 组（对应 UnixGroupActor create）。 */
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        try {
            UnixGroupRequest req = UnixGroupRequest.newBuilder()
                    .setGroupName(nullToEmpty(cmd.getGroupName()))
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .createUnixGroup(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC createUnixGroup to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC createUnixGroup failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC createUnixGroup to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC createUnixGroup failed: " + e.getMessage());
        }
    }

    /** 删除 Unix 组（对应 UnixGroupActor delete）。 */
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        try {
            UnixGroupRequest req = UnixGroupRequest.newBuilder()
                    .setGroupName(nullToEmpty(cmd.getGroupName()))
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .deleteUnixGroup(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC deleteUnixGroup to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC deleteUnixGroup failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC deleteUnixGroup to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC deleteUnixGroup failed: " + e.getMessage());
        }
    }

    /** 创建 Unix 用户（对应 UnixUserActor create）。 */
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        try {
            UnixUserRequest req = UnixUserRequest.newBuilder()
                    .setUsername(nullToEmpty(cmd.getUsername()))
                    .setMainGroup(nullToEmpty(cmd.getMainGroup()))
                    .setOtherGroups(nullToEmpty(cmd.getOtherGroups()))
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .createUnixUser(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC createUnixUser to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC createUnixUser failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC createUnixUser to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC createUnixUser failed: " + e.getMessage());
        }
    }

    /** 删除 Unix 用户（对应 UnixUserActor delete）。 */
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        try {
            UnixUserRequest req = UnixUserRequest.newBuilder()
                    .setUsername(nullToEmpty(cmd.getUsername()))
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .deleteUnixUser(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC deleteUnixUser to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC deleteUnixUser failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC deleteUnixUser to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC deleteUnixUser failed: " + e.getMessage());
        }
    }

    /** 文件写入操作（对应 FileOperateActor）。 */
    public ExecResult operateFile(String hostname, FileOperateCommand cmd) {
        try {
            FileOperateRequest.Builder reqBuilder = FileOperateRequest.newBuilder()
                    .setPath(nullToEmpty(cmd.getPath()))
                    .setContent(nullToEmpty(cmd.getContent()));
            if (cmd.getLines() != null) {
                reqBuilder.addAllLines(cmd.getLines());
            }
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .operateFile(reqBuilder.build());
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC operateFile to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC operateFile failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC operateFile to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC operateFile failed: " + e.getMessage());
        }
    }

    /**
     * 生成告警配置（对应 AlertConfigActor）。
     *
     * <p>{@code configFileMap} 的 key 是 {@link com.datasophon.common.model.Generators}
     * 对象，不可直接作为 JSON key，故通过 {@link AlertConfigEntry} 桥接列表序列化。</p>
     */
    public ExecResult generateAlertConfig(String hostname, GenerateAlertConfigCommand cmd) {
        try {
            String configMapJson = objectMapper.writeValueAsString(
                    AlertConfigEntry.fromMap(cmd.getConfigFileMap()));
            AlertConfigRequest req = AlertConfigRequest.newBuilder()
                    .setClusterId(cmd.getClusterId() != null ? cmd.getClusterId() : 0)
                    .setConfigMapJson(configMapJson)
                    .build();
            ExecResultPb pb = getStub(hostname)
                    .withDeadlineAfter(180, TimeUnit.SECONDS)
                    .generateAlertConfig(req);
            return toExecResult(pb);
        } catch (StatusRuntimeException e) {
            log.warn("gRPC generateAlertConfig to {} failed: {}", hostname, e.getStatus());
            return ExecResult.error("gRPC generateAlertConfig failed: " + e.getStatus());
        } catch (IllegalStateException e) {
            log.warn("gRPC generateAlertConfig to {} failed: {}", hostname, e.getMessage());
            return ExecResult.error("gRPC generateAlertConfig failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("gRPC generateAlertConfig to {} serialization failed: {}", hostname, e.getMessage(), e);
            return ExecResult.error("gRPC generateAlertConfig serialization failed: " + e.getMessage());
        }
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

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
