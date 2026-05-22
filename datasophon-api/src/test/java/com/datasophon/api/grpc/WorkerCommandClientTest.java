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
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkerCommandClient 单元测试。
 *
 * <p>通过 {@code grpc-inprocess} 启动一个假 Worker gRPC 服务端，
 * 用 {@link TestableWorkerCommandClient} 覆盖 {@code buildChannel()} 注入
 * in-process channel，无需真实 TCP 连接或外部进程。</p>
 *
 * <p>测试重点：</p>
 * <ul>
 *   <li>proto 请求字段是否按预期填充（commands vs commandLine 区分）</li>
 *   <li>响应正确映射为 {@link ExecResult}</li>
 *   <li>Worker 未注册时 client 返回 error 而非抛出异常</li>
 * </ul>
 */
class WorkerCommandClientTest {

    private static final String SERVER_NAME = "test-worker-client";
    private static final String HOSTNAME = "worker-host";

    private FakeWorkerCommandService fakeService;
    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private WorkerCommandClient client;
    private WorkerRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        fakeService = new FakeWorkerCommandService();
        inProcessServer = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(fakeService)
                .build()
                .start();
        inProcessChannel = InProcessChannelBuilder.forName(SERVER_NAME)
                .directExecutor()
                .build();

        registry = mock(WorkerRegistry.class);
        WorkerEndpoint endpoint = new WorkerEndpoint(HOSTNAME, 18082, "x86_64", 1);
        when(registry.getEndpoint(HOSTNAME)).thenReturn(Optional.of(endpoint));

        client = new TestableWorkerCommandClient(registry, inProcessChannel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        client.destroy();
        inProcessChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        inProcessServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── ping ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ping: 成功响应映射为 ExecResult{true, 'pong'}")
    void ping_mapsResponseToExecResult() {
        fakeService.pingResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("pong").build();

        ExecResult result = client.ping(HOSTNAME);

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("pong");
    }

    @Test
    @DisplayName("ping: Worker 未注册 → 返回 error ExecResult（不抛异常）")
    void ping_workerNotRegistered_returnsErrorResult() {
        when(registry.getEndpoint(HOSTNAME)).thenReturn(Optional.empty());

        ExecResult result = client.ping(HOSTNAME);

        assertThat(result.getExecResult()).isFalse();
        assertThat(result.getExecOut()).contains("not registered");
    }

    // ─── executeCmd ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeCmd: 命令列表模式 → proto.commands 填充，commandLine 为空")
    void executeCmd_sendsCommandsListAndMapsResponse() {
        fakeService.executeCmdResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("done").build();

        ExecResult result = client.executeCmd(HOSTNAME,
                List.of("yarn", "rmadmin", "-refreshQueues"));

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("done");
        assertThat(fakeService.lastExecuteCmdRequest.getCommandsList())
                .containsExactly("yarn", "rmadmin", "-refreshQueues");
        assertThat(fakeService.lastExecuteCmdRequest.getCommandLine()).isEmpty();
    }

    @Test
    @DisplayName("executeCmdLine: 单行命令模式 → proto.commandLine 填充，commands 为空")
    void executeCmdLine_sendsCommandLineAndMapsResponse() {
        fakeService.executeCmdResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("active").build();

        ExecResult result = client.executeCmdLine(HOSTNAME,
                "hdfs haadmin -getServiceState nn1");

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("active");
        assertThat(fakeService.lastExecuteCmdRequest.getCommandLine())
                .isEqualTo("hdfs haadmin -getServiceState nn1");
        assertThat(fakeService.lastExecuteCmdRequest.getCommandsList()).isEmpty();
    }

    // ─── getLog ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLog: 正确填充 logFile + baseDir，返回日志内容")
    void getLog_sendsRequestFieldsAndMapsResponse() {
        fakeService.getLogResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("ERROR out of memory").build();

        ExecResult result = client.getLog(HOSTNAME,
                "logs/hdfs/NameNode.log", "/opt/datasophon/worker");

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("ERROR out of memory");
        assertThat(fakeService.lastGetLogRequest.getLogFile())
                .isEqualTo("logs/hdfs/NameNode.log");
        assertThat(fakeService.lastGetLogRequest.getBaseDir())
                .isEqualTo("/opt/datasophon/worker");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /**
     * WorkerCommandClient 测试子类：重写 buildChannel() 注入 in-process channel，
     * 绕过真实 TCP 连接和 WorkerRegistry 端口查找。
     */
    static class TestableWorkerCommandClient extends WorkerCommandClient {
        private final ManagedChannel testChannel;

        TestableWorkerCommandClient(WorkerRegistry registry, ManagedChannel testChannel) {
            super(registry);
            this.testChannel = testChannel;
        }

        @Override
        protected ManagedChannel buildChannel(String hostname, int port) {
            return testChannel;
        }
    }

    /**
     * 可配置响应的假 Worker gRPC 服务端。
     * 记录最后一次收到的请求，供测试断言验证字段填充是否正确。
     */
    static class FakeWorkerCommandService
            extends WorkerCommandServiceGrpc.WorkerCommandServiceImplBase {

        ExecResultPb pingResponse;
        ExecResultPb executeCmdResponse;
        ExecResultPb getLogResponse;
        ExecuteCmdRequest lastExecuteCmdRequest;
        GetLogRequest lastGetLogRequest;

        @Override
        public void ping(PingRequest request, StreamObserver<ExecResultPb> obs) {
            obs.onNext(pingResponse != null ? pingResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void executeCmd(ExecuteCmdRequest request, StreamObserver<ExecResultPb> obs) {
            lastExecuteCmdRequest = request;
            obs.onNext(executeCmdResponse != null ? executeCmdResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void getLog(GetLogRequest request, StreamObserver<ExecResultPb> obs) {
            lastGetLogRequest = request;
            obs.onNext(getLogResponse != null ? getLogResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }
    }
}
