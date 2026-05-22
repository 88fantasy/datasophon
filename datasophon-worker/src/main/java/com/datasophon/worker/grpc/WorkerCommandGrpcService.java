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
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
import com.datasophon.worker.utils.FileUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Worker 端 gRPC 服务实现（Phase 1）。
 *
 * <p>对应三个 Pekko Actor：</p>
 * <ul>
 *   <li>{@code PingActor}      → {@link #ping}</li>
 *   <li>{@code ExecuteCmdActor / RMStateActor / NMStateActor} → {@link #executeCmd}</li>
 *   <li>{@code LogActor}       → {@link #getLog}</li>
 * </ul>
 *
 * <p>Worker 无 Spring 容器，此类作为普通 Java 对象由 {@link WorkerGrpcServer} 注册到
 * {@code ServerBuilder}。</p>
 */
public class WorkerCommandGrpcService extends WorkerCommandServiceGrpc.WorkerCommandServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(WorkerCommandGrpcService.class);

    // ─── Ping ─────────────────────────────────────────────────────────────────

    @Override
    public void ping(PingRequest request, StreamObserver<ExecResultPb> responseObserver) {
        responseObserver.onNext(ExecResultPb.newBuilder()
                .setExecResult(true)
                .setExecOut("pong")
                .build());
        responseObserver.onCompleted();
    }

    // ─── ExecuteCmd ───────────────────────────────────────────────────────────

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

    // ─── GetLog ───────────────────────────────────────────────────────────────

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

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static ExecResultPb toProto(ExecResult r) {
        return ExecResultPb.newBuilder()
                .setExecResult(r.getExecResult())
                .setExecOut(r.getExecOut() != null ? r.getExecOut() : "")
                .setExecErrOut(r.getExecErrOut() != null ? r.getExecErrOut() : "")
                .build();
    }
}
