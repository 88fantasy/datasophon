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

import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.grpc.api.ExecuteCmdRequest;
import com.datasophon.grpc.api.ExecResultPb;
import com.datasophon.grpc.api.GetLogRequest;
import com.datasophon.grpc.api.PingRequest;
import com.datasophon.grpc.api.WorkerCommandServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * WorkerCommandGrpcService 单元测试。
 *
 * <p>使用 {@code grpc-inprocess} 在同一 JVM 内创建 gRPC 服务端/客户端，
 * 无需真实 TCP 端口，全程不依赖外部进程。</p>
 *
 * <p>PropertyUtils 说明：其静态初始化块在找不到 common.properties 时会调用
 * {@code System.exit(1)}。通过 static 块（比任何 @BeforeEach 更早运行）提前
 * 设置 {@code commonPropertiesLocation} 系统属性，指向测试资源文件，确保
 * PropertyUtils 在 {@link #getLog_absolutePathExists_returnsContent} 中首次
 * 加载时能成功读取到配置。</p>
 */
class WorkerCommandGrpcServiceTest {

    // ─── PropertyUtils 预置（必须在任何测试运行前完成） ──────────────────────────
    static {
        URL url = WorkerCommandGrpcServiceTest.class.getClassLoader()
                .getResource("common-test.properties");
        if (url != null) {
            System.setProperty("commonPropertiesLocation", url.getPath());
        }
    }

    private static final String SERVER_NAME = "test-worker-service";

    private Server server;
    private ManagedChannel channel;
    private WorkerCommandServiceGrpc.WorkerCommandServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(new WorkerCommandGrpcService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME)
                .directExecutor()
                .build();
        stub = WorkerCommandServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── Ping ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ping 返回 execResult=true, execOut=pong")
    void ping_returnsPong() {
        ExecResultPb result = stub.ping(PingRequest.newBuilder().setMessage("ping").build());

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("pong");
    }

    // ─── ExecuteCmd ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeCmd: commandLine 非空 → 路由到 ShellUtils.execShell")
    void executeCmd_withCommandLine_routesToExecShell() {
        ExecResult fakeResult = ExecResult.success("active");

        try (MockedStatic<ShellUtils> mocked = mockStatic(ShellUtils.class)) {
            mocked.when(() -> ShellUtils.execShell(anyString())).thenReturn(fakeResult);

            ExecResultPb result = stub.executeCmd(
                    ExecuteCmdRequest.newBuilder()
                            .setCommandLine("yarn rmadmin -getServiceState rm1")
                            .build());

            assertThat(result.getExecResult()).isTrue();
            assertThat(result.getExecOut()).isEqualTo("active");
            // 验证路由正确：调用的是 execShell 而非 exec
            mocked.verify(() -> ShellUtils.execShell("yarn rmadmin -getServiceState rm1"));
        }
    }

    @Test
    @DisplayName("executeCmd: commandLine 为空 → 路由到 ShellUtils.exec（命令列表模式）")
    void executeCmd_withCommandsList_routesToExec() {
        ExecResult fakeResult = ExecResult.success("refreshed");

        try (MockedStatic<ShellUtils> mocked = mockStatic(ShellUtils.class)) {
            mocked.when(() -> ShellUtils.exec(anyString(), any(), anyLong()))
                    .thenReturn(fakeResult);

            ExecResultPb result = stub.executeCmd(
                    ExecuteCmdRequest.newBuilder()
                            .addAllCommands(List.of("yarn", "rmadmin", "-refreshQueues"))
                            .build());

            assertThat(result.getExecResult()).isTrue();
            assertThat(result.getExecOut()).isEqualTo("refreshed");
            // 验证路由正确：调用的是 exec 而非 execShell
            mocked.verify(() -> ShellUtils.exec(anyString(), any(), anyLong()));
        }
    }

    // ─── GetLog ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLog: 绝对路径文件存在 → 返回文件内容")
    void getLog_absolutePathExists_returnsContent(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("service.log");
        Files.writeString(logFile, "INFO startup\nWARN slow query\nERROR disk full\n");

        ExecResultPb result = stub.getLog(
                GetLogRequest.newBuilder()
                        .setLogFile(logFile.toString())
                        .setBaseDir("/some/base")
                        .build());

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut())
                .contains("INFO startup")
                .contains("WARN slow query")
                .contains("ERROR disk full");
    }

    @Test
    @DisplayName("getLog: 文件不存在 → 返回 'can not find log file'")
    void getLog_fileNotFound_returnsFallbackMessage() {
        ExecResultPb result = stub.getLog(
                GetLogRequest.newBuilder()
                        .setLogFile("/absolute/nonexistent/path/service.log")
                        .setBaseDir("/base")
                        .build());

        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).isEqualTo("can not find log file");
    }

    @Test
    @DisplayName("getLog: 相对路径 → 拼接 baseDir 后读取")
    void getLog_relativePath_usesBaseDir(@TempDir Path tempDir) throws IOException {
        // 相对路径（不以 / 开头）→ baseDir + "/" + "/" + logFile
        Path logFile = tempDir.resolve("app.log");
        Files.writeString(logFile, "relative log line\n");

        ExecResultPb result = stub.getLog(
                GetLogRequest.newBuilder()
                        .setLogFile("app.log")
                        .setBaseDir(tempDir.toString())
                        .build());

        // 路径拼接为 baseDir + // + "app.log" = tempDir//app.log（Linux 下等价于 tempDir/app.log）
        assertThat(result.getExecResult()).isTrue();
        assertThat(result.getExecOut()).contains("relative log line");
    }
}
