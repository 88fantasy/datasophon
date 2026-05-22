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

import com.datasophon.common.utils.ExecResult;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
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
    private final ConcurrentHashMap<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    public WorkerCommandClient(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
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
