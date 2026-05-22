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

package com.datasophon.worker.grpc;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ServiceLoaderUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.function.ThrowableSupplier;
import com.datasophon.common.model.ConfigFileEntry;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.ServiceRoleRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
import com.datasophon.worker.handler.ConfigureServiceHandler;
import com.datasophon.worker.handler.InstallServiceHandler;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.HookUtils;
import com.datasophon.worker.strategy.ServiceRoleStrategy;
import com.datasophon.worker.strategy.ServiceRoleStrategyContext;
import com.datasophon.worker.utils.FileUtils;
import com.datasophon.worker.utils.TaskConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Worker 端 gRPC 服务实现（Phase 1 + Phase 2）。
 *
 * <p>Phase 1 对应三个 Pekko Actor：</p>
 * <ul>
 *   <li>{@code PingActor}      → {@link #ping}</li>
 *   <li>{@code ExecuteCmdActor / RMStateActor / NMStateActor} → {@link #executeCmd}</li>
 *   <li>{@code LogActor}       → {@link #getLog}</li>
 * </ul>
 *
 * <p>Phase 2 对应六个 Pekko Actor：</p>
 * <ul>
 *   <li>{@code InstallServiceActor}   → {@link #installServiceRole}</li>
 *   <li>{@code ConfigureServiceActor} → {@link #configureServiceRole}</li>
 *   <li>{@code StartServiceActor}     → {@link #startServiceRole}</li>
 *   <li>{@code StopServiceActor}      → {@link #stopServiceRole}</li>
 *   <li>{@code RestartServiceActor}   → {@link #restartServiceRole}</li>
 *   <li>{@code ServiceStatusActor}    → {@link #serviceRoleStatus}</li>
 * </ul>
 *
 * <p>Worker 无 Spring 容器，此类作为普通 Java 对象由 {@link WorkerGrpcServer} 注册到
 * {@code ServerBuilder}。</p>
 */
public class WorkerCommandGrpcService extends WorkerCommandServiceGrpc.WorkerCommandServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(WorkerCommandGrpcService.class);

    /** 用于反序列化 ServiceRoleRequest.json_payload 的 ObjectMapper（Worker 无 Spring 容器）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    // ─── Phase 1: Ping ────────────────────────────────────────────────────────

    @Override
    public void ping(PingRequest request, StreamObserver<ExecResultPb> responseObserver) {
        responseObserver.onNext(ExecResultPb.newBuilder()
                .setExecResult(true)
                .setExecOut("pong")
                .build());
        responseObserver.onCompleted();
    }

    // ─── Phase 1: ExecuteCmd ─────────────────────────────────────────────────

    /**
     * 执行 Shell 命令。
     *
     * <ul>
     *   <li>若 {@code command_line} 非空 → {@code ShellUtils.execShell(commandLine)}
     *       （对应 RMStateActor / NMStateActor）</li>
     *   <li>否则 → {@code ShellUtils.exec(INSTALL_PATH, commands, 60L)}
     *       （对应 ExecuteCmdActor）</li>
     * </ul>
     */
    @Override
    public void executeCmd(ExecuteCmdRequest request, StreamObserver<ExecResultPb> responseObserver) {
        ExecResult execResult;
        if (!request.getCommandLine().isEmpty()) {
            execResult = ShellUtils.execShell(request.getCommandLine());
        } else {
            execResult = ShellUtils.exec(Constants.INSTALL_PATH, request.getCommandsList(), 60L);
        }
        responseObserver.onNext(toProto(execResult));
        responseObserver.onCompleted();
    }

    // ─── Phase 1: GetLog ─────────────────────────────────────────────────────

    @Override
    public void getLog(GetLogRequest request, StreamObserver<ExecResultPb> responseObserver) {
        Map<String, String> paramMap = new HashMap<>();
        try {
            paramMap.put("${host}", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            paramMap.put("${host}", "unknown");
        }
        String logFileName = PlaceholderUtils.replacePlaceholders(
                request.getLogFile(), paramMap, Constants.REGEX_VARIABLE);
        log.info("gRPC getLog: {}", logFileName);

        String logStr = "can not find log file";
        try {
            if (logFileName.startsWith(StrUtil.SLASH) && FileUtil.exist(logFileName)) {
                logStr = FileUtils.readLastRows(logFileName, Charset.defaultCharset(),
                        PropertyUtils.getInt("rows"));
            } else {
                String path = request.getBaseDir() + Constants.SLASH + Constants.SLASH + logFileName;
                if (new File(path).exists()) {
                    logStr = FileUtils.readLastRows(path, Charset.defaultCharset(),
                            PropertyUtils.getInt("rows"));
                }
            }
        } catch (java.io.IOException e) {
            log.error("gRPC getLog failed to read file: {}", logFileName, e);
            logStr = "read log failed: " + e.getMessage();
        }
        responseObserver.onNext(ExecResultPb.newBuilder()
                .setExecResult(true)
                .setExecOut(logStr)
                .build());
        responseObserver.onCompleted();
    }

    // ─── Phase 2: InstallServiceRole ─────────────────────────────────────────

    @Override
    public void installServiceRole(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result;
        try {
            InstallServiceRoleCommand cmd = MAPPER.readValue(req.getJsonPayload(), InstallServiceRoleCommand.class);
            Logger taskLog = LoggerFactory.getLogger(
                    TaskConstants.createLoggerName(cmd.getServiceName(), cmd.getServiceRoleName(), WorkerCommandGrpcService.class));
            taskLog.info("开始安装服务:{} {}", cmd.getServiceName(), cmd.getServiceRoleName());

            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_INSTALL, cmd, cmd.getVariables()),
                    () -> doInstall(cmd, taskLog),
                    () -> invokeHook(cmd.getHooks(), HookType.POST_INSTALL, cmd, cmd.getVariables())
            );
            taskLog.info("安装 {} {}, 信息: {}", cmd.getPackageName(),
                    result.getExecResult() ? "成功" : "失败", result.getExecOut());
            log.info("gRPC installServiceRole {} {} {}", cmd.getServiceName(), cmd.getServiceRoleName(),
                    result.getExecResult() ? "success" : "failed");
        } catch (Exception e) {
            log.error("gRPC installServiceRole failed: {}", e.getMessage(), e);
            result = ExecResult.error("安装服务失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── Phase 2: ConfigureServiceRole ───────────────────────────────────────

    @Override
    public void configureServiceRole(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result;
        try {
            GenerateServiceConfigCommand cmd = MAPPER.readValue(req.getJsonPayload(), GenerateServiceConfigCommand.class);
            if (!req.getConfigMapJson().isEmpty()) {
                List<ConfigFileEntry> entries = MAPPER.readValue(
                        req.getConfigMapJson(), new TypeReference<List<ConfigFileEntry>>() {});
                cmd.setCofigFileMap(ConfigFileEntry.toMap(entries));
            }
            log.info("gRPC configureServiceRole: {} {}", cmd.getServiceName(), cmd.getServiceRoleName());
            ConfigureServiceHandler handler = new ConfigureServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
            result = handler.configure(cmd, cmd);
        } catch (Exception e) {
            log.error("gRPC configureServiceRole failed: {}", e.getMessage(), e);
            result = ExecResult.error("配置服务失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── Phase 2: StartServiceRole ───────────────────────────────────────────

    @Override
    public void startServiceRole(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result;
        try {
            ServiceRoleOperateCommand cmd = MAPPER.readValue(req.getJsonPayload(), ServiceRoleOperateCommand.class);
            log.info("gRPC startServiceRole: {} {}", cmd.getServiceName(), cmd.getServiceRoleName());
            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_START, cmd, cmd.getVariables()),
                    () -> {
                        ServiceRoleStrategy strategy = ServiceRoleStrategyContext.getServiceRoleHandler(cmd.getServiceRoleName());
                        if (Objects.nonNull(strategy)) {
                            return strategy.handler(cmd);
                        }
                        ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
                        return sh.start(cmd.getStartRunner(), cmd.getStatusRunner(), cmd,
                                cmd.getRunAs(), cmd.isCheckStatus());
                    },
                    () -> invokeHook(cmd.getHooks(), HookType.POST_START, cmd, cmd.getVariables())
            );
            log.info("gRPC startServiceRole {} {} {}", cmd.getServiceName(), cmd.getServiceRoleName(),
                    result.getExecResult() ? "success" : "failed");
        } catch (Exception e) {
            log.error("gRPC startServiceRole failed: {}", e.getMessage(), e);
            result = ExecResult.error("启动服务失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── Phase 2: StopServiceRole ────────────────────────────────────────────

    @Override
    public void stopServiceRole(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result;
        try {
            ServiceRoleOperateCommand cmd = MAPPER.readValue(req.getJsonPayload(), ServiceRoleOperateCommand.class);
            log.info("gRPC stopServiceRole: {} {}", cmd.getServiceName(), cmd.getServiceRoleName());
            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_STOP, cmd, cmd.getVariables()),
                    () -> {
                        ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
                        return sh.stop(cmd.getStopRunner(), cmd.getStatusRunner(), cmd, cmd.getRunAs());
                    },
                    () -> invokeHook(cmd.getHooks(), HookType.POST_STOP, cmd, cmd.getVariables())
            );
            log.info("gRPC stopServiceRole {} {} {}", cmd.getServiceName(), cmd.getServiceRoleName(),
                    result.getExecResult() ? "success" : "failed");
        } catch (Exception e) {
            log.error("gRPC stopServiceRole failed: {}", e.getMessage(), e);
            result = ExecResult.error("停止服务失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── Phase 2: RestartServiceRole ─────────────────────────────────────────

    @Override
    public void restartServiceRole(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result;
        try {
            ServiceRoleOperateCommand cmd = MAPPER.readValue(req.getJsonPayload(), ServiceRoleOperateCommand.class);
            log.info("gRPC restartServiceRole: {} {}", cmd.getServiceName(), cmd.getServiceRoleName());
            ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
            result = sh.restart(cmd.getRestartRunner(), cmd);
            log.info("gRPC restartServiceRole {} {} {}", cmd.getServiceName(), cmd.getServiceRoleName(),
                    result.getExecResult() ? "success" : "failed");
        } catch (Exception e) {
            log.error("gRPC restartServiceRole failed: {}", e.getMessage(), e);
            result = ExecResult.error("重启服务失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── Phase 2: ServiceRoleStatus ──────────────────────────────────────────

    @Override
    public void serviceRoleStatus(ServiceRoleRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult result = new ExecResult();
        try {
            ServiceRoleOperateCommand cmd = MAPPER.readValue(req.getJsonPayload(), ServiceRoleOperateCommand.class);
            log.info("gRPC serviceRoleStatus: {} {}", cmd.getServiceName(), cmd.getServiceRoleName());
            ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
            int times = cmd.getTimes() == null ? PropertyUtils.getInt("times") : Math.max(cmd.getTimes(), 1);
            int count = 0;
            while (count < times) {
                count++;
                result = sh.status(cmd.getStatusRunner(), cmd);
                if (result.getExecResult()) {
                    break;
                }
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (count == times && !result.getExecResult()) {
                result.setExecResult(false);
                result.setExecOut(String.format("检查%s状态失败，已经达到重试次数%s",
                        cmd.getServiceRoleName(), times));
            }
            log.info("gRPC serviceRoleStatus {} {} {}", cmd.getServiceName(), cmd.getServiceRoleName(),
                    result.getExecResult() ? "running" : "stopped");
        } catch (Exception e) {
            log.error("gRPC serviceRoleStatus failed: {}", e.getMessage(), e);
            result = ExecResult.error("检查服务状态失败: " + e.getMessage());
        }
        obs.onNext(toProto(result));
        obs.onCompleted();
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private static ExecResult doInstall(InstallServiceRoleCommand command, Logger taskLog) {
        taskLog.info("开始安装软件包{}", command.getPackageName());
        String normalPkgDir = PkgInstallPathUtils.getInstallHomeName(command);
        command.setNormalPkgDir(normalPkgDir);
        InstallServiceHandler serviceHandler = getInstallHandler(command);
        ExecResult installResult = serviceHandler.install(command);
        if (installResult.getExecResult()) {
            installResult = serviceHandler.createLink(command);
        }
        return installResult;
    }

    private static InstallServiceHandler getInstallHandler(InstallServiceRoleCommand command) {
        List<InstallServiceHandler> handlers = ServiceLoaderUtil.loadList(InstallServiceHandler.class);
        handlers.sort(Comparator.comparing(InstallServiceHandler::getOrder));
        for (InstallServiceHandler handler : handlers) {
            handler.init(command);
            if (handler.match(command)) {
                return handler;
            }
        }
        // 兜底
        InstallServiceHandler fallback = new InstallServiceHandler();
        fallback.init(command);
        return fallback;
    }

    @SafeVarargs
    private static ExecResult invokeFunctions(ThrowableSupplier<ExecResult>... actions) throws Exception {
        ExecResult result = ExecResult.error("no task called");
        for (ThrowableSupplier<ExecResult> supplier : actions) {
            result = supplier.get();
            if (!result.isSuccess()) {
                return result;
            }
        }
        return result;
    }

    private static ExecResult invokeHook(List<HookConfig> hooks, HookType type,
            ServiceRoleResource resource, Map<String, String> globalVariables) {
        if (hooks == null || hooks.isEmpty()) {
            return ExecResult.success();
        }
        ExecResult result = ExecResult.success();
        List<HookConfig> hookList = HookUtils.getMatchedHooks(hooks, type);
        int i = -1;
        try {
            for (i = 0; i < hookList.size(); i++) {
                HookConfig hook = hookList.get(i);
                HookContext ctx = HookUtils.createContext(hook, resource, globalVariables);
                if (HookUtils.isHookEnable(hook.getCondition(), ctx.getAllInfoAsMap())) {
                    log.info("{}.{} invoke {} hook, index:{}, action:{}", resource.getServiceName(),
                            resource.getServiceRoleName(), type, i, hook.getAction());
                    result = HookUtils.invokeHook(hook, ctx);
                    if (!result.isSuccess()) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("invoke {} hook(index:{}) fail", type, i, ex);
            result = ExecResult.error(ex.getMessage());
        }
        return result;
    }

    private static ExecResultPb toProto(ExecResult r) {
        return ExecResultPb.newBuilder()
                .setExecResult(r.getExecResult())
                .setExecOut(r.getExecOut() != null ? r.getExecOut() : "")
                .setExecErrOut(r.getExecErrOut() != null ? r.getExecErrOut() : "")
                .build();
    }
}
