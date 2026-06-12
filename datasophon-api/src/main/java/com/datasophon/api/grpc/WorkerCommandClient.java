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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.core.util.StrUtil;

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
        return callWorker(hostname, "ping", () -> getStub(hostname)
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .ping(PingRequest.newBuilder().setMessage("ping").build()));
    }
    
    /** 执行命令列表（ExecuteCmdActor 模式），默认 90s deadline。 */
    public ExecResult executeCmd(String hostname, List<String> commands) {
        return executeCmd(hostname, commands, 90);
    }
    
    /** 执行命令列表，可指定 deadline（如巡检等周期性场景收紧为 30s）。 */
    public ExecResult executeCmd(String hostname, List<String> commands, long deadlineSeconds) {
        return callWorker(hostname, "executeCmd", () -> getStub(hostname)
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .executeCmd(ExecuteCmdRequest.newBuilder()
                        .addAllCommands(commands)
                        .build()));
    }
    
    /** 执行单行 shell 命令（RMStateActor / NMStateActor 模式）。 */
    public ExecResult executeCmdLine(String hostname, String commandLine) {
        return callWorker(hostname, "executeCmdLine", () -> getStub(hostname)
                .withDeadlineAfter(90, TimeUnit.SECONDS)
                .executeCmd(ExecuteCmdRequest.newBuilder()
                        .setCommandLine(commandLine)
                        .build()));
    }
    
    /** 读取 Worker 节点日志（LogActor 模式）。 */
    public ExecResult getLog(String hostname, String logFile, String baseDir) {
        return callWorker(hostname, "getLog", () -> getStub(hostname)
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
        return callWorker(hostname, "startServiceRole", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .startServiceRole(buildServiceRoleRequest(cmd)));
    }
    
    /** 停止服务角色（对应 StopServiceActor）。 */
    public ExecResult stopServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "stopServiceRole", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .stopServiceRole(buildServiceRoleRequest(cmd)));
    }
    
    /** 重启服务角色（对应 RestartServiceActor）。 */
    public ExecResult restartServiceRole(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "restartServiceRole", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .restartServiceRole(buildServiceRoleRequest(cmd)));
    }
    
    /** 检查服务角色状态（对应 ServiceStatusActor）。 */
    public ExecResult serviceRoleStatus(String hostname, ServiceRoleOperateCommand cmd) {
        return callWorker(hostname, "serviceRoleStatus", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .serviceRoleStatus(buildServiceRoleRequest(cmd)));
    }
    
    // ─── Phase 3 API ─────────────────────────────────────────────────────────
    
    /** 创建 Unix 组（对应 UnixGroupActor create）。 */
    public ExecResult createUnixGroup(String hostname, CreateUnixGroupCommand cmd) {
        return callWorker(hostname, "createUnixGroup", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .createUnixGroup(UnixGroupRequest.newBuilder()
                        .setGroupName(nullToEmpty(cmd.getGroupName()))
                        .build()));
    }
    
    /** 删除 Unix 组（对应 UnixGroupActor delete）。 */
    public ExecResult deleteUnixGroup(String hostname, DelUnixGroupCommand cmd) {
        return callWorker(hostname, "deleteUnixGroup", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .deleteUnixGroup(UnixGroupRequest.newBuilder()
                        .setGroupName(nullToEmpty(cmd.getGroupName()))
                        .build()));
    }
    
    /** 创建 Unix 用户（对应 UnixUserActor create）。 */
    public ExecResult createUnixUser(String hostname, CreateUnixUserCommand cmd) {
        return callWorker(hostname, "createUnixUser", () -> getStub(hostname)
                .withDeadlineAfter(180, TimeUnit.SECONDS)
                .createUnixUser(UnixUserRequest.newBuilder()
                        .setUsername(nullToEmpty(cmd.getUsername()))
                        .setMainGroup(nullToEmpty(cmd.getMainGroup()))
                        .setOtherGroups(nullToEmpty(cmd.getOtherGroups()))
                        .build()));
    }
    
    /** 删除 Unix 用户（对应 UnixUserActor delete）。 */
    public ExecResult deleteUnixUser(String hostname, DelUnixUserCommand cmd) {
        return callWorker(hostname, "deleteUnixUser", () -> getStub(hostname)
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
            try {
                if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
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
        // address 求值在 lambda 内部，确保每次建立 Channel 时读取注册表中最新的端点信息，
        // 避免 Worker IP 变更时用旧地址建立新连接的竞态窗口。
        // compute 而非 computeIfAbsent：缓存命中时校验 Channel 状态，已 shutdown
        // 的失效 Channel（离线事件丢失等场景）原子地重建，避免复用死连接。
        ManagedChannel channel = channelCache.compute(hostname, (h, cached) -> {
            if (cached != null && !cached.isShutdown() && !cached.isTerminated()) {
                return cached;
            }
            WorkerEndpoint ep = workerRegistry.getEndpoint(h)
                    .orElseThrow(() -> new IllegalStateException(
                            "Worker not registered in gRPC registry: " + h));
            // ip 优先：Worker 上报的可达 IP（k8s hostNetwork 场景集群外可达）。
            // ip 为空时回落 hostname（裸机兼容模式、旧版 Worker 或 Master 重启预热窗口）。
            String address = StrUtil.isNotBlank(ep.getIp()) ? ep.getIp() : ep.getHostname();
            return buildChannel(address, ep.getGrpcPort());
        });
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
