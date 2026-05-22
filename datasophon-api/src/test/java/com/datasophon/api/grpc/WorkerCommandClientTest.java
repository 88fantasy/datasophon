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

import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.model.ConfigFileEntry;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.ServiceRoleRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * WorkerCommandClient 单元测试（Phase 1 + Phase 2）。
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
 *   <li>Phase 2: cofigFileMap 序列化/反序列化正确性</li>
 * </ul>
 */
class WorkerCommandClientTest {

    private static final String SERVER_NAME = "test-worker-client";
    private static final String HOSTNAME = "worker-host";

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        client = new TestableWorkerCommandClient(registry, MAPPER, inProcessChannel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        client.destroy();
        inProcessChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        inProcessServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── Phase 1: ping ────────────────────────────────────────────────────────

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

    // ─── Phase 1: executeCmd ─────────────────────────────────────────────────

    @Test
    @DisplayName("executeCmd: 命令列表模式 → proto.commands 填充，commandLine 为空")
    void executeCmd_sendsCommandsListAndMapsResponse() {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("done").build();
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

    // ─── Phase 1: getLog ─────────────────────────────────────────────────────

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

    // ─── Phase 2: installServiceRole ─────────────────────────────────────────

    @Test
    @DisplayName("installServiceRole: JSON payload 包含服务名称，成功映射响应")
    void installServiceRole_serializedAndMapsResponse() throws Exception {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("installed").build();

        InstallServiceRoleCommand cmd = new InstallServiceRoleCommand();
        cmd.setServiceName("ZOOKEEPER");
        cmd.setServiceRoleName("zkserver");

        ExecResult result = client.installServiceRole(HOSTNAME, cmd);

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("installed");
        assertThat(fakeService.lastServiceRoleRequest.getServiceName()).isEqualTo("ZOOKEEPER");
        assertThat(fakeService.lastServiceRoleRequest.getServiceRoleName()).isEqualTo("zkserver");
        // JSON payload 应包含服务名
        assertThat(fakeService.lastServiceRoleRequest.getJsonPayload()).contains("ZOOKEEPER");
        // configMapJson 为空（InstallServiceRoleCommand 无 cofigFileMap）
        assertThat(fakeService.lastServiceRoleRequest.getConfigMapJson()).isEmpty();
    }

    // ─── Phase 2: configureServiceRole ───────────────────────────────────────

    @Test
    @DisplayName("configureServiceRole: cofigFileMap 序列化为 config_map_json，json_payload 中 map 为 null")
    void configureServiceRole_cofigFileMapSerializedSeparately() throws Exception {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("configured").build();

        GenerateServiceConfigCommand cmd = new GenerateServiceConfigCommand();
        cmd.setServiceName("HDFS");
        cmd.setServiceRoleName("NameNode");
        // cofigFileMap 为 null（无配置文件映射的简单情况）
        cmd.setCofigFileMap(null);

        ExecResult result = client.configureServiceRole(HOSTNAME, cmd);

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("configured");

        ServiceRoleRequest req = fakeService.lastServiceRoleRequest;
        assertThat(req.getServiceName()).isEqualTo("HDFS");
        assertThat(req.getServiceRoleName()).isEqualTo("NameNode");

        // json_payload 应包含 serviceName
        assertThat(req.getJsonPayload()).contains("HDFS");
        // cofigFileMap=null → config_map_json 应为 "null" 或空数组，反序列化后为 null/empty
        List<ConfigFileEntry> entries = MAPPER.readValue(req.getConfigMapJson(),
                new TypeReference<List<ConfigFileEntry>>() {});
        assertThat(entries).isNullOrEmpty();
    }

    // ─── Phase 2: startServiceRole ───────────────────────────────────────────

    @Test
    @DisplayName("startServiceRole: 请求字段正确填充，响应正确映射")
    void startServiceRole_sendsRequestAndMapsResponse() {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("started").build();

        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName("YARN");
        cmd.setServiceRoleName("ResourceManager");

        ExecResult result = client.startServiceRole(HOSTNAME, cmd);

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("started");
        assertThat(fakeService.lastServiceRoleRequest.getServiceName()).isEqualTo("YARN");
        assertThat(fakeService.lastServiceRoleRequest.getServiceRoleName()).isEqualTo("ResourceManager");
    }

    // ─── Phase 2: stopServiceRole ────────────────────────────────────────────

    @Test
    @DisplayName("stopServiceRole: 请求字段正确填充，响应正确映射")
    void stopServiceRole_sendsRequestAndMapsResponse() {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(true).setExecOut("stopped").build();

        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName("KAFKA");
        cmd.setServiceRoleName("KafkaBroker");

        ExecResult result = client.stopServiceRole(HOSTNAME, cmd);

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("stopped");
        assertThat(fakeService.lastServiceRoleRequest.getServiceName()).isEqualTo("KAFKA");
    }

    // ─── Phase 2: serviceRoleStatus ──────────────────────────────────────────

    @Test
    @DisplayName("serviceRoleStatus: 返回 false 时映射为 ExecResult{false}")
    void serviceRoleStatus_failureResponse_mapsCorrectly() {
        fakeService.serviceRoleResponse = ExecResultPb.newBuilder()
                .setExecResult(false).setExecOut("not running").build();

        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName("HDFS");
        cmd.setServiceRoleName("DataNode");

        ExecResult result = client.serviceRoleStatus(HOSTNAME, cmd);

        assertThat(result.getExecResult()).isFalse();
        assertThat(result.getExecOut()).isEqualTo("not running");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /**
     * WorkerCommandClient 测试子类：重写 buildChannel() 注入 in-process channel，
     * 绕过真实 TCP 连接和 WorkerRegistry 端口查找。
     */
    static class TestableWorkerCommandClient extends WorkerCommandClient {
        private final ManagedChannel testChannel;

        TestableWorkerCommandClient(WorkerRegistry registry, ObjectMapper objectMapper,
                ManagedChannel testChannel) {
            super(registry, objectMapper);
            this.testChannel = testChannel;
        }

        @Override
        protected ManagedChannel buildChannel(String hostname, int port) {
            return testChannel;
        }
    }

    /**
     * 可配置响应的假 Worker gRPC 服务端（Phase 1 + Phase 2）。
     * 记录最后一次收到的请求，供测试断言验证字段填充是否正确。
     */
    static class FakeWorkerCommandService
            extends WorkerCommandServiceGrpc.WorkerCommandServiceImplBase {

        ExecResultPb pingResponse;
        ExecResultPb executeCmdResponse;
        ExecResultPb getLogResponse;
        ExecResultPb serviceRoleResponse;

        ExecuteCmdRequest lastExecuteCmdRequest;
        GetLogRequest lastGetLogRequest;
        ServiceRoleRequest lastServiceRoleRequest;

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

        @Override
        public void installServiceRole(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void configureServiceRole(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void startServiceRole(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void stopServiceRole(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void restartServiceRole(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }

        @Override
        public void serviceRoleStatus(ServiceRoleRequest request, StreamObserver<ExecResultPb> obs) {
            lastServiceRoleRequest = request;
            obs.onNext(serviceRoleResponse != null ? serviceRoleResponse : ExecResultPb.getDefaultInstance());
            obs.onCompleted();
        }
    }
}
