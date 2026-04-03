package com.datasophon.common.k8s.client;

import com.datasophon.common.PropertiesPathUtils;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UpdateDeploymentDTO;
import com.datasophon.common.k8s.exception.KubectlException;
import com.datasophon.common.k8s.vo.k8s.K8sConfigMap;
import com.datasophon.common.k8s.vo.k8s.K8sDeployment;
import com.datasophon.common.k8s.vo.k8s.K8sIngress;
import com.datasophon.common.k8s.vo.k8s.K8sNamespace;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.common.k8s.vo.k8s.K8sPod;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.k8s.vo.k8s.K8sService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KubectlClient 单元测试类
 * <p>
 * 使用 Mockito 模拟 ShellUtils 静态方法调用，测试 KubectlClient 的各项功能
 * </p>
 *
 * @author Claude
 */
@DisplayName("KubectlClient 单元测试")
class KubectlClientTest {

    private static final String TEST_KUBECTL_PATH = "/usr/bin/kubectl";
    private static final String TEST_JSON_RESPONSE = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";

    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
    }
    /**
     * 构造方法和 kubectlPath 检测相关测试
     */
    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("Windows 系统下应使用默认 kubectl 路径")
        void testConstructor_Windows() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Windows 10");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ClientOptions options = new ClientOptions();
                    options.setServerName("https://k8s.example.com");
                    options.setToken("test-token");

                    KubectlClient client = new KubectlClient(options);

                    Assertions.assertEquals("kubectl", client.getKubectlPath());
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("非 Windows 系统下 kubectl 命令存在时应使用检测到的路径")
        void testConstructor_NonWindows_KubectlExists() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Linux");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ExecResult mockResult = mock(ExecResult.class);
                    when(mockResult.isSuccess()).thenReturn(true);
                    when(mockResult.getExecOut()).thenReturn("/usr/bin/kubectl\n");

                    mockedShellUtils.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                            .thenReturn(mockResult);

                    ClientOptions options = new ClientOptions();
                    options.setServerName("https://k8s.example.com");
                    options.setToken("test-token");

                    KubectlClient client = new KubectlClient(options);

                    Assertions.assertEquals("/usr/bin/kubectl", client.getKubectlPath());
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @DisplayName("非 Windows 系统下 kubectl 命令不存在时应使用默认路径")
        void testConstructor_NonWindows_KubectlNotExists() {
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

                    KubectlClient client = new KubectlClient(options);

                    Assertions.assertEquals("kubectl", client.getKubectlPath());
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

                    KubectlClient client = new KubectlClient(options);

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

                KubectlClient client = new KubectlClient(options);

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

                KubectlClient client = new KubectlClient(options);

                Assertions.assertEquals("admin", client.getUsername());
                Assertions.assertEquals("password123", client.getPassword());
            }
        }

        @Test
        @DisplayName("使用服务器证书配置构造")
        void testConstructor_WithServerCert() {
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
                    options.setServerCert("SGVsbG8gV29ybGQ="); // Base64 encoded "Hello World"

                    KubectlClient client = new KubectlClient(options);

                    Assertions.assertNotNull(client.getServerCert());
                    Assertions.assertTrue(client.getServerCert().endsWith("ca.cert"));
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }
    }

    /**
     * execute 方法相关测试（通过 executeToJson 间接测试）
     */
    @Nested
    @DisplayName("execute 方法测试")
    class ExecuteTests {

        @Test
        @DisplayName("execute 使用 KubeConfig 认证 - 通过 executeToJson 间接测试")
        void testExecute_WithKubeConfig() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(TEST_JSON_RESPONSE);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--kubeconfig"));
                            Assertions.assertTrue(args.stream().anyMatch(a -> a.endsWith("kubeConfig.yaml")));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setKubeConfig("apiVersion: v1");
                KubectlClient client = new KubectlClient(options);

                client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30);
            }
        }

        @Test
        @DisplayName("execute 使用 Token 认证 - 通过 executeToJson 间接测试")
        void testExecute_WithToken() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(TEST_JSON_RESPONSE);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--token"));
                            Assertions.assertTrue(args.contains("test-token"));
                            Assertions.assertTrue(args.contains("--insecure-skip-tls-verify=true"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30);
            }
        }

        @Test
        @DisplayName("execute 使用用户名密码认证 - 通过 executeToJson 间接测试")
        void testExecute_WithUsernamePassword() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(TEST_JSON_RESPONSE);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("--username"));
                            Assertions.assertTrue(args.contains("admin"));
                            Assertions.assertTrue(args.contains("--password"));
                            Assertions.assertTrue(args.contains("password123"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setUsername("admin");
                options.setPassword("password123");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30);
            }
        }

        @Test
        @DisplayName("execute 使用证书认证 - 通过 executeToJson 间接测试")
        void testExecute_WithServerCert() {
            String originalOsName = System.getProperty("os.name");

            try {
                System.setProperty("os.name", "Linux");

                try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                    ExecResult mockResult = mock(ExecResult.class);
                    when(mockResult.isSuccess()).thenReturn(true);
                    when(mockResult.getExecOut()).thenReturn(TEST_JSON_RESPONSE);

                    mockedShellUtils.when(() -> ShellUtils.exec(any(), any(), anyLong()))
                            .thenReturn(mockResult);

                    mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                            .thenAnswer(invocation -> {
                                List<String> args = invocation.getArgument(1);
                                Assertions.assertTrue(args.contains("--certificate-authority"));
                                Assertions.assertTrue(args.stream().anyMatch(a -> a.endsWith("ca.cert")));
                                return mockResult;
                            });

                    ClientOptions options = new ClientOptions();
                    options.setToken("test-token");
                    options.setServerName("https://k8s.example.com");
                    options.setServerCert("SGVsbG8gV29ybGQ=");

                    KubectlClient client = new KubectlClient(options);
                    client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30);
                }
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }
    }

    /**
     * executeToJson 方法相关测试
     */
    @Nested
    @DisplayName("executeToJson 方法测试")
    class ExecuteToJsonTests {

        @Test
        @DisplayName("executeToJson 成功")
        void testExecuteToJson_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(TEST_JSON_RESPONSE);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                String result = client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30);

                Assertions.assertTrue(result.contains("items"));
            }
        }

        @Test
        @DisplayName("executeToJson 失败应抛出 KubectlException")
        void testExecuteToJson_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("connection refused");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.executeToJson(new ArrayList<>(Arrays.asList("get pods")), 30));

                Assertions.assertTrue(exception.getMessage().contains("connection refused"));
            }
        }
    }

    /**
     * getVersion 方法相关测试
     */
    @Nested
    @DisplayName("getVersion 方法测试")
    class GetVersionTests {

        @Test
        @DisplayName("getVersion 成功")
        void testGetVersion_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String versionOutput = "Client Version: v1.28.0\nServer Version: v1.28.2\n";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(versionOutput);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                String version = client.getVersion();

                Assertions.assertEquals("v1.28.2", version);
            }
        }

        @Test
        @DisplayName("getVersion 失败 - 命令执行失败")
        void testGetVersion_Failure_CommandFailed() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getExecOut()).thenReturn("connection refused");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.getVersion());

                Assertions.assertTrue(exception.getMessage().contains("connection refused"));
            }
        }

        @Test
        @DisplayName("getVersion 失败 - 无法解析版本信息")
        void testGetVersion_Failure_CannotParse() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("invalid output format");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.getVersion());

                Assertions.assertTrue(exception.getMessage().contains("无法解析 K8s 版本信息"));
            }
        }
    }

    /**
     * getNodes 方法相关测试
     */
    @Nested
    @DisplayName("getNodes 方法测试")
    class GetNodesTests {

        @Test
        @DisplayName("getNodes 成功")
        void testGetNodes_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[{\"metadata\":{\"name\":\"node1\"}}]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sNode> result = client.getNodes();

                Assertions.assertNotNull(result);
                Assertions.assertEquals("List", result.getKind());
            }
        }
    }

    /**
     * getNamespaces 方法相关测试
     */
    @Nested
    @DisplayName("getNamespaces 方法测试")
    class GetNamespacesTests {

        @Test
        @DisplayName("getNamespaces 成功")
        void testGetNamespaces_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[{\"metadata\":{\"name\":\"default\"}}]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sNamespace> result = client.getNamespaces();

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * getPods 方法相关测试
     */
    @Nested
    @DisplayName("getPods 方法测试")
    class GetPodsTests {

        @Test
        @DisplayName("getPods 成功 - 无 labelSelector")
        void testGetPods_Success_NoLabelSelector() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sPod> result = client.getPods("default", null);

                Assertions.assertNotNull(result);
            }
        }

        @Test
        @DisplayName("getPods 成功 - 有 labelSelector")
        void testGetPods_Success_WithLabelSelector() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("-l"));
                            Assertions.assertTrue(args.contains("app=myapp"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sPod> result = client.getPods("default", "app=myapp");

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * getDeployments 方法相关测试
     */
    @Nested
    @DisplayName("getDeployments 方法测试")
    class GetDeploymentsTests {

        @Test
        @DisplayName("getDeployments 成功 - 有 labelSelector")
        void testGetDeployments_Success_WithLabelSelector() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("deployments"));
                            Assertions.assertTrue(args.contains("-n"));
                            Assertions.assertTrue(args.contains("kube-system"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sDeployment> result = client.getDeployments("kube-system", "app=nginx");

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * getServices 方法相关测试
     */
    @Nested
    @DisplayName("getServices 方法测试")
    class GetServicesTests {

        @Test
        @DisplayName("getServices 成功")
        void testGetServices_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sService> result = client.getServices("default", null);

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * getIngresses 方法相关测试
     */
    @Nested
    @DisplayName("getIngresses 方法测试")
    class GetIngressesTests {

        @Test
        @DisplayName("getIngresses 成功")
        void testGetIngresses_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sIngress> result = client.getIngresses("default", null);

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * getConfigMaps 方法相关测试
     */
    @Nested
    @DisplayName("getConfigMaps 方法测试")
    class GetConfigMapsTests {

        @Test
        @DisplayName("getConfigMaps 成功")
        void testGetConfigMaps_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                String jsonResponse = "{\"apiVersion\":\"v1\",\"kind\":\"List\",\"items\":[]}";
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn(jsonResponse);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                K8sResourceList<K8sConfigMap> result = client.getConfigMaps("default", null);

                Assertions.assertNotNull(result);
            }
        }
    }

    /**
     * restartDeployment 方法相关测试
     */
    @Nested
    @DisplayName("restartDeployment 方法测试")
    class RestartDeploymentTests {

        @Test
        @DisplayName("restartDeployment 成功")
        void testRestartDeployment_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("rollout"));
                            Assertions.assertTrue(args.contains("restart"));
                            Assertions.assertTrue(args.contains("deployment/my-deployment"));
                            Assertions.assertTrue(args.contains("-n"));
                            Assertions.assertTrue(args.contains("default"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                Assertions.assertDoesNotThrow(() ->
                        client.restartDeployment("default", "my-deployment"));
            }
        }

        @Test
        @DisplayName("restartDeployment 失败")
        void testRestartDeployment_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("deployment not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.restartDeployment("default", "nonexistent-deployment"));

                Assertions.assertTrue(exception.getMessage().contains("deployment not found"));
            }
        }
    }

    /**
     * updateDeploymentImage 方法相关测试
     */
    @Nested
    @DisplayName("updateDeploymentImage 方法测试")
    class UpdateDeploymentImageTests {

        @Test
        @DisplayName("updateDeploymentImage 成功 - 单容器")
        void testUpdateDeploymentImage_Success_SingleContainer() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("set"));
                            Assertions.assertTrue(args.contains("image"));
                            Assertions.assertTrue(args.contains("deployment/my-deployment"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                UpdateDeploymentDTO dto = new UpdateDeploymentDTO();
                dto.setNamespace("default");
                dto.setDeployment("my-deployment");
                UpdateDeploymentDTO.Image image = new UpdateDeploymentDTO.Image();
                image.setContainerName("nginx");
                image.setNewImage("nginx");
                image.setTag("1.21");
                dto.setImages(new ArrayList<>(Arrays.asList(image)));

                Assertions.assertDoesNotThrow(() -> client.updateDeploymentImage(dto));
            }
        }

        @Test
        @DisplayName("updateDeploymentImage 成功 - 多容器")
        void testUpdateDeploymentImage_Success_MultipleContainers() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                UpdateDeploymentDTO dto = new UpdateDeploymentDTO();
                dto.setNamespace("default");
                dto.setDeployment("my-deployment");
                UpdateDeploymentDTO.Image image1 = new UpdateDeploymentDTO.Image();
                image1.setContainerName("nginx");
                image1.setNewImage("nginx");
                image1.setTag("1.21");
                UpdateDeploymentDTO.Image image2 = new UpdateDeploymentDTO.Image();
                image2.setContainerName("sidecar");
                image2.setNewImage("sidecar");
                image2.setTag("v1.0");
                dto.setImages(Arrays.asList(image1, image2));

                Assertions.assertDoesNotThrow(() -> client.updateDeploymentImage(dto));
            }
        }

        @Test
        @DisplayName("updateDeploymentImage 失败")
        void testUpdateDeploymentImage_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("image not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                UpdateDeploymentDTO dto = new UpdateDeploymentDTO();
                dto.setNamespace("default");
                dto.setDeployment("my-deployment");
                UpdateDeploymentDTO.Image image = new UpdateDeploymentDTO.Image();
                image.setContainerName("nginx");
                image.setNewImage("nginx");
                image.setTag("1.21");
                dto.setImages(new ArrayList<>(Arrays.asList(image)));

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.updateDeploymentImage(dto));

                Assertions.assertTrue(exception.getMessage().contains("image not found"));
            }
        }
    }

    /**
     * createNamespace 方法相关测试
     */
    @Nested
    @DisplayName("createNamespace 方法测试")
    class CreateNamespaceTests {

        @Test
        @DisplayName("createNamespace 成功")
        void testCreateNamespace_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                Assertions.assertDoesNotThrow(() -> client.createNamespace("my-namespace"));
            }
        }

        @Test
        @DisplayName("createNamespace 命名空间已存在")
        void testCreateNamespace_AlreadyExists() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getExecOut()).thenReturn("namespace already exists");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                // 命名空间已存在时不应抛出异常
                Assertions.assertDoesNotThrow(() -> client.createNamespace("existing-namespace"));
            }
        }

        @Test
        @DisplayName("createNamespace 失败")
        void testCreateNamespace_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getExecOut()).thenReturn("permission denied");
                when(mockResult.getErrorTraceMessage()).thenReturn("permission denied");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.createNamespace("my-namespace"));

                Assertions.assertTrue(exception.getMessage().contains("permission denied"));
            }
        }
    }

    /**
     * scaleDeployment 方法相关测试
     */
    @Nested
    @DisplayName("scaleDeployment 方法测试")
    class ScaleDeploymentTests {

        @Test
        @DisplayName("scaleDeployment 成功")
        void testScaleDeployment_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("scale"));
                            Assertions.assertTrue(args.contains("deployment/my-deployment"));
                            Assertions.assertTrue(args.contains("--replicas=3"));
                            return mockResult;
                        });

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                Assertions.assertDoesNotThrow(() ->
                        client.scaleDeployment("default", "my-deployment", 3));
            }
        }

        @Test
        @DisplayName("scaleDeployment 失败")
        void testScaleDeployment_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("deployment not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.scaleDeployment("default", "nonexistent", 3));

                Assertions.assertTrue(exception.getMessage().contains("deployment not found"));
            }
        }
    }

    /**
     * hasResources 方法相关测试
     */
    @Nested
    @DisplayName("hasResources 方法测试")
    class HasResourcesTests {

        @Test
        @DisplayName("hasResources 资源存在")
        void testHasResources_Exists() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("pod1 pod2 pod3");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                boolean result = client.hasResources("default", "pods", "app=myapp");

                Assertions.assertTrue(result);
            }
        }

        @Test
        @DisplayName("hasResources 资源不存在")
        void testHasResources_NotExists() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                boolean result = client.hasResources("default", "pods", "app=nonexistent");

                Assertions.assertFalse(result);
            }
        }

        @Test
        @DisplayName("hasResources 命令执行失败返回 false")
        void testHasResources_CommandFailed() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                boolean result = client.hasResources("default", "pods", null);

                Assertions.assertFalse(result);
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

                KubectlClient client = new KubectlClient(options);

                Assertions.assertDoesNotThrow(() -> client.close());
            }
        }
    }

    /**
     * JSON 解析失败相关测试
     */
    @Nested
    @DisplayName("JSON 解析失败测试")
    class JsonParseFailureTests {

        @Test
        @DisplayName("parseResourceList JSON 解析失败")
        void testParseResourceList_InvalidJson() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("invalid json {");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                ClientOptions options = new ClientOptions();
                options.setToken("test-token");
                options.setServerName("https://k8s.example.com");
                KubectlClient client = new KubectlClient(options);

                KubectlException exception = Assertions.assertThrows(KubectlException.class, () ->
                        client.getNodes());

                Assertions.assertTrue(exception.getMessage().contains("解析结果错误失败"));
            }
        }
    }
}
