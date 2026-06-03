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
import com.datasophon.common.model.AlertConfigEntry;
import com.datasophon.common.model.AlertItem;
import com.datasophon.common.model.ConfigFileEntry;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.HookConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
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
import com.datasophon.worker.handler.ConfigureServiceHandler;
import com.datasophon.worker.handler.InstallServiceHandler;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.hook.HookUtils;
import com.datasophon.worker.strategy.ServiceRoleStrategy;
import com.datasophon.worker.strategy.ServiceRoleStrategyContext;
import com.datasophon.worker.utils.FileUtils;
import com.datasophon.worker.utils.FreemakerUtils;
import com.datasophon.worker.utils.TaskConstants;
import com.datasophon.worker.utils.UnixUtils;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

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
            if (new File(logFileName).isAbsolute() && FileUtil.exist(logFileName)) {
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

            // 防御性拷贝，注入 frameCode 供 DownloadStrategy 等 hook 使用，不污染共享变量 map
            Map<String, String> hookVars = new HashMap<>(
                    cmd.getVariables() == null ? Collections.emptyMap() : cmd.getVariables());
            hookVars.put("${__frameCode__}", cmd.getFrameCode());

            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_INSTALL, cmd, hookVars),
                    () -> doInstall(cmd, taskLog),
                    () -> invokeHook(cmd.getHooks(), HookType.POST_INSTALL, cmd, hookVars)
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
            Map<String, String> startHookVars = new HashMap<>(
                    cmd.getVariables() == null ? Collections.emptyMap() : cmd.getVariables());
            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_START, cmd, startHookVars),
                    () -> {
                        ServiceRoleStrategy strategy = ServiceRoleStrategyContext.getServiceRoleHandler(cmd.getServiceRoleName());
                        if (Objects.nonNull(strategy)) {
                            return strategy.handler(cmd);
                        }
                        ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
                        return sh.start(cmd.getStartRunner(), cmd.getStatusRunner(), cmd,
                                cmd.getRunAs(), cmd.isCheckStatus());
                    },
                    () -> invokeHook(cmd.getHooks(), HookType.POST_START, cmd, startHookVars)
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
            Map<String, String> stopHookVars = new HashMap<>(
                    cmd.getVariables() == null ? Collections.emptyMap() : cmd.getVariables());
            result = invokeFunctions(
                    () -> invokeHook(cmd.getHooks(), HookType.PRE_STOP, cmd, stopHookVars),
                    () -> {
                        ServiceHandler sh = new ServiceHandler(cmd.getServiceName(), cmd.getServiceRoleName());
                        return sh.stop(cmd.getStopRunner(), cmd.getStatusRunner(), cmd, cmd.getRunAs());
                    },
                    () -> invokeHook(cmd.getHooks(), HookType.POST_STOP, cmd, stopHookVars)
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

    // ─── Phase 3: UnixGroup ───────────────────────────────────────────────────

    @Override
    public void createUnixGroup(UnixGroupRequest req, StreamObserver<ExecResultPb> obs) {
        try {
            log.info("gRPC createUnixGroup: {}", req.getGroupName());
            ExecResult result = UnixUtils.createUnixGroup(req.getGroupName());
            log.info("gRPC createUnixGroup {} {}", req.getGroupName(),
                    result.getExecResult() ? "success" : "failed");
            obs.onNext(toProto(result));
            obs.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createUnixGroup failed: {}", e.getMessage(), e);
            obs.onNext(toProto(ExecResult.error("createUnixGroup failed: " + e.getMessage())));
            obs.onCompleted();
        }
    }

    @Override
    public void deleteUnixGroup(UnixGroupRequest req, StreamObserver<ExecResultPb> obs) {
        try {
            log.info("gRPC deleteUnixGroup: {}", req.getGroupName());
            ExecResult result = UnixUtils.delUnixGroup(req.getGroupName());
            log.info("gRPC deleteUnixGroup {} {}", req.getGroupName(),
                    result.getExecResult() ? "success" : "failed");
            obs.onNext(toProto(result));
            obs.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteUnixGroup failed: {}", e.getMessage(), e);
            obs.onNext(toProto(ExecResult.error("deleteUnixGroup failed: " + e.getMessage())));
            obs.onCompleted();
        }
    }

    // ─── Phase 3: UnixUser ────────────────────────────────────────────────────

    @Override
    public void createUnixUser(UnixUserRequest req, StreamObserver<ExecResultPb> obs) {
        try {
            log.info("gRPC createUnixUser: {}", req.getUsername());
            ExecResult result = UnixUtils.createUnixUser(
                    req.getUsername(), req.getMainGroup(), req.getOtherGroups());
            log.info("gRPC createUnixUser {} {}", req.getUsername(),
                    result.getExecResult() ? "success" : "failed");
            obs.onNext(toProto(result));
            obs.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createUnixUser failed: {}", e.getMessage(), e);
            obs.onNext(toProto(ExecResult.error("createUnixUser failed: " + e.getMessage())));
            obs.onCompleted();
        }
    }

    @Override
    public void deleteUnixUser(UnixUserRequest req, StreamObserver<ExecResultPb> obs) {
        try {
            log.info("gRPC deleteUnixUser: {}", req.getUsername());
            ExecResult result = UnixUtils.delUnixUser(req.getUsername());
            log.info("gRPC deleteUnixUser {} {}", req.getUsername(),
                    result.getExecResult() ? "success" : "failed");
            obs.onNext(toProto(result));
            obs.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteUnixUser failed: {}", e.getMessage(), e);
            obs.onNext(toProto(ExecResult.error("deleteUnixUser failed: " + e.getMessage())));
            obs.onCompleted();
        }
    }

    // ─── Phase 3: FileOperate ─────────────────────────────────────────────────

    @Override
    public void operateFile(FileOperateRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult execResult = new ExecResult();
        try {
            log.info("gRPC operateFile: {}", req.getPath());
            TreeSet<String> lines = new TreeSet<>(req.getLinesList());
            if (!lines.isEmpty()) {
                File file = FileUtil.writeLines(lines, req.getPath(), Charset.defaultCharset());
                execResult.setExecResult(file.exists());
            } else {
                FileUtil.writeUtf8String(req.getContent(), req.getPath());
                execResult.setExecResult(true);
            }
        } catch (Exception e) {
            log.error("gRPC operateFile failed: {}", e.getMessage(), e);
            execResult = ExecResult.error("operateFile failed: " + e.getMessage());
        }
        obs.onNext(toProto(execResult));
        obs.onCompleted();
    }

    // ─── Phase 3: AlertConfig ─────────────────────────────────────────────────

    @Override
    public void generateAlertConfig(AlertConfigRequest req, StreamObserver<ExecResultPb> obs) {
        ExecResult execResult = new ExecResult();
        try {
            log.info("gRPC generateAlertConfig clusterId={}", req.getClusterId());
            List<AlertConfigEntry> entries = MAPPER.readValue(req.getConfigMapJson(),
                    new TypeReference<List<AlertConfigEntry>>() {});
            HashMap<Generators, List<AlertItem>> configFileMap = AlertConfigEntry.toMap(entries);
            for (Generators generators : configFileMap.keySet()) {
                List<AlertItem> alertItems = configFileMap.get(generators);
                FreemakerUtils.generatePromAlertFile(generators, alertItems,
                        generators.getFilename().replace(".yml", "").toUpperCase());
            }
            execResult.setExecResult(true);
            log.info("gRPC generateAlertConfig success, rules={}", configFileMap.size());
        } catch (Exception e) {
            log.error("gRPC generateAlertConfig failed: {}", e.getMessage(), e);
            execResult = ExecResult.error("generateAlertConfig failed: " + e.getMessage());
        }
        obs.onNext(toProto(execResult));
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
