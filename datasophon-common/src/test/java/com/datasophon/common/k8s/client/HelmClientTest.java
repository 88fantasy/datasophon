package com.datasophon.common.k8s.client;

import com.datasophon.common.PropertiesPathUtils;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.exception.HelmException;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HelmClient 单元测试类
 * <p>
 * 使用 Mockito 模拟 ShellUtils 静态方法调用，测试 HelmClient 的各项功能
 * </p>
 *
 * @author Claude
 */
@DisplayName("HelmClient 单元测试")
class HelmClientTest {

    private static final String TEST_HELM_PATH = "/usr/bin/helm";
    private static final int DEFAULT_TIMEOUT = 30;

    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
    }
    /**
     * 构造方法和 helmPath 检测相关测试
     */
    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("Windows 系统下应使用默认 helm 路径")
        void testConstructor_Windows() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Windows 10");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ClientOptions options = new ClientOptions();
                    options.setServerName("https://k8s.example.com");
                    options.setToken("test-token");

                    HelmClient client = new HelmClient(options);

                    Assertions.assertEquals("helm", client.getHelmPath());
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("非 Windows 系统下 helm 命令存在时应使用检测到的路径")
        void testConstructor_NonWindows_HelmExists() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Linux");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ExecResult mockResult = mock(ExecResult.class);
                    when(mockResult.isSuccess()).thenReturn(true);
                    when(mockResult.getExecOut()).thenReturn("/usr/bin/helm\n");

                    mockedShellUtils.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                            .thenReturn(mockResult);

                    ClientOptions options = new ClientOptions();
                    options.setServerName("https://k8s.example.com");
                    options.setToken("test-token");

                    HelmClient client = new HelmClient(options);

                    Assertions.assertEquals("/usr/bin/helm", client.getHelmPath());
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("非 Windows 系统下 helm 命令不存在时应使用默认路径")
        void testConstructor_NonWindows_HelmNotExists() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Linux");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ExecResult mockResult = mock(ExecResult.class);
                    when(mockResult.isSuccess()).thenReturn(false);

                    mockedShellUtils.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                            .thenReturn(mockResult);

                    ClientOptions options = new ClientOptions();
                    options.setServerName("https://k8s.example.com");
                    options.setToken("test-token");

                    HelmClient client = new HelmClient(options);

                    Assertions.assertEquals("helm", client.getHelmPath());
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("使用 KubeConfig 配置构造")
        void testConstructor_WithKubeConfig() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Linux");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ExecResult mockResult = mock(ExecResult.class);
                    when(mockResult.isSuccess()).thenReturn(false);

                    mockedShellUtils.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                            .thenReturn(mockResult);

                    ClientOptions options = new ClientOptions();
                    options.setKubeConfig("apiVersion: v1\nclusters: []");

                    HelmClient client = new HelmClient(options);

                    Assertions.assertNotNull(client.getKubeConfig());
                    Assertions.assertTrue(client.getKubeConfig().endsWith("kubeConfig.yaml"));
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("使用 Token 认证配置构造")
        void testConstructor_WithToken() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ClientOptions options = new ClientOptions();
                options.setToken("test-token-123");
                options.setServerName("https://k8s.example.com");

                HelmClient client = new HelmClient(options);

                Assertions.assertEquals("test-token-123", client.getToken());
                Assertions.assertEquals("https://k8s.example.com", client.getServerName());
            }
        }

        @Test
        @DisplayName("使用用户名密码认证配置构造")
        void testConstructor_WithUsernamePassword() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ClientOptions options = new ClientOptions();
                options.setUsername("admin");
                options.setPassword("password123");
                options.setServerName("https://k8s.example.com");

                HelmClient client = new HelmClient(options);

                Assertions.assertEquals("admin", client.getUsername());
                Assertions.assertEquals("password123", client.getPassword());
            }
        }
    }

    /**
     * execute 方法相关测试
     */
    @Nested
    @DisplayName("execute 方法测试")
    class ExecuteTests {

        @Test
        @DisplayName("execute 使用 KubeConfig 认证")
        void testExecute_WithKubeConfig() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--kubeconfig"));
                            Assertions.assertTrue(args.stream().anyMatch(a -> a.endsWith("kubeConfig.yaml")));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setKubeConfig("apiVersion: v1");
                HelmClient client = new HelmClient(options);

                client.execute(new ArrayList<>(Arrays.asList("list")), 30);
            }
        }

        @Test
        @DisplayName("execute 使用 Token 认证")
        void testExecute_WithToken() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--kube-token"));
                            Assertions.assertTrue(args.contains("test-token"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                client.execute(new ArrayList<>(Arrays.asList("list")), 30);
            }
        }

        @Test
        @DisplayName("execute 使用用户名密码认证")
        void testExecute_WithUsernamePassword() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--kube-username"));
                            Assertions.assertTrue(args.contains("admin"));
                            Assertions.assertTrue(args.contains("--kube-password"));
                            Assertions.assertTrue(args.contains("password123"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setUsername("admin");
                options.setPassword("password123");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                client.execute(new ArrayList<>(Arrays.asList("list")), 30);
            }
        }

        @Test
        @DisplayName("execute 命令执行失败")
        void testExecute_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("exit code: 1");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                ExecResult result = client.execute(new ArrayList<>(Arrays.asList("invalid-command")), 30);

                Assertions.assertFalse(result.isSuccess());
            }
        }
    }

    /**
     * executeWithResult 方法相关测试
     */
    @Nested
    @DisplayName("executeWithResult 方法测试")
    class ExecuteWithResultTests {

        @Test
        @DisplayName("executeWithResult 成功")
        void testExecuteWithResult_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("output\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                ExecResult result = client.execute(new ArrayList<>(Collections.singletonList("list")), 30);

                Assertions.assertTrue(result.isSuccess());
                Assertions.assertEquals("output\n", result.getExecOut());
            }
        }

        @Test
        @DisplayName("executeWithResult 失败应抛出 HelmException")
        void testExecuteWithResult_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("command failed");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                HelmException exception = Assertions.assertThrows(HelmException.class, () ->
                        client.execute(new ArrayList<>(Collections.singletonList("invalid")), 30));

                Assertions.assertTrue(exception.getMessage().contains("command failed"));
            }
        }
    }

    /**
     * upgrade 方法相关测试
     */
    @Nested
    @DisplayName("upgrade 方法测试")
    class UpgradeTests {

        @Test
        @DisplayName("upgrade 成功 - 基本参数")
        void testUpgrade_Success_Basic() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":1}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setNamespace("default");

                HelmReleaseVO result = client.upgrade(params);

                Assertions.assertNotNull(result);
            }
        }

        @Test
        @DisplayName("upgrade 成功 - 包含 valuesFiles")
        void testUpgrade_Success_WithValuesFiles() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":2}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--values"));
                            Assertions.assertTrue(args.contains("/path/to/values.yaml"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setNamespace("default");
                params.setValuesFiles(Collections.singletonList("/path/to/values.yaml"));

                client.upgrade(params);
            }
        }

        @Test
        @DisplayName("upgrade 成功 - 包含 setValues")
        void testUpgrade_Success_WithSetValues() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":2}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--set"));
                            Assertions.assertTrue(args.contains("image.tag=v1.0.0"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setNamespace("default");
                params.setSetValues(Collections.singletonList("image.tag=v1.0.0"));

                client.upgrade(params);
            }
        }

        @Test
        @DisplayName("upgrade 失败 - releaseName 为空")
        void testUpgrade_Failure_EmptyReleaseName() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("");
                params.setChartPath("./my-chart");

                HelmException exception = Assertions.assertThrows(HelmException.class, () ->
                        client.upgrade(params));

                Assertions.assertTrue(exception.getMessage().contains("releaseName 不能为空"));
            }
        }

        @Test
        @DisplayName("upgrade 失败 - chartPath 为空")
        void testUpgrade_Failure_EmptyChartPath() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("");

                HelmException exception = Assertions.assertThrows(HelmException.class, () ->
                        client.upgrade(params));

                Assertions.assertTrue(exception.getMessage().contains("chartPath 不能为空"));
            }
        }

        @Test
        @DisplayName("upgrade 失败 - 命令执行失败")
        void testUpgrade_Failure_CommandFailed() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("release not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");

                HelmException exception = Assertions.assertThrows(HelmException.class, () ->
                        client.upgrade(params));

                Assertions.assertTrue(exception.getMessage().contains("release not found"));
            }
        }

        @Test
        @DisplayName("upgrade 失败 - JSON 解析失败")
        void testUpgrade_Failure_JsonParseError() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("invalid json {");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");

                HelmException exception = Assertions.assertThrows(HelmException.class, () ->
                        client.upgrade(params));

                Assertions.assertTrue(exception.getMessage().contains("解析 helm upgrade 响应失败"));
            }
        }

        @Test
        @DisplayName("upgrade 包含 install 参数")
        void testUpgrade_WithInstall() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":1}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--install"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setInstall(true);

                client.upgrade(params);
            }
        }

        @Test
        @DisplayName("upgrade 包含 timeout 参数")
        void testUpgrade_WithTimeout() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":1}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--timeout"));
                            Assertions.assertTrue(args.contains("600s"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setTimeoutSeconds(600);

                client.upgrade(params);
            }
        }

        @Test
        @DisplayName("upgrade 包含 description 参数")
        void testUpgrade_WithDescription() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"name\":\"my-release\",\"version\":1}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--description"));
                            Assertions.assertTrue(args.contains("Release upgrade"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                HelmClient client = new HelmClient(options);

                UpgradeParams params = new UpgradeParams();
                params.setReleaseName("my-release");
                params.setChartPath("./my-chart");
                params.setDescription("Release upgrade");

                client.upgrade(params);
            }
        }
    }

    /**
     * close 方法相关测试
     */
    @Nested
    @DisplayName("close 方法测试")
    class CloseTests {

        @Test
        @DisplayName("close 方法调用成功")
        void testClose_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");

                HelmClient client = new HelmClient(options);

                Assertions.assertDoesNotThrow(() -> client.close());
            }
        }
    }
}
