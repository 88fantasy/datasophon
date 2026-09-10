package com.datasophon.common.k8s.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.common.PropertiesPathUtils;
import com.datasophon.common.k8s.exception.DockerException;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * DockerClient 单元测试类
 * <p>
 * 使用 Mockito 模拟 ShellUtils 静态方法调用，测试 DockerClient 的各项功能
 * </p>
 *
 * @author Claude
 */
@DisplayName("DockerClient 单元测试")
class DockerClientTest {

    // 测试用的 Docker 路径
    private static final String TEST_DOCKER_PATH = "/usr/bin/docker";
    private static final int DEFAULT_TIMEOUT = 30;

    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
    }

    /**
     * 构造方法和 dockerPath 检测相关测试
     */
    @Nested
    @DisplayName("构造方法测试")
    class ConstructorTests {

        @Test
        @DisplayName("未配置 docker.install_path 时回退到 PATH 中的 docker")
        void usesCommandNameWhenInstallPathIsAbsent() {
            try (MockedStatic<PropertyUtils> mockedProps = Mockito.mockStatic(PropertyUtils.class)) {
                mockedProps.when(() -> PropertyUtils.getString("docker.install_path")).thenReturn(null);

                Assertions.assertEquals("docker", new DockerClient().getDockerPath());
            }
        }

        @Test
        @DisplayName("配置了 docker.install_path 时使用该绝对路径")
        void usesConfiguredInstallPath() {
            try (MockedStatic<PropertyUtils> mockedProps = Mockito.mockStatic(PropertyUtils.class)) {
                mockedProps.when(() -> PropertyUtils.getString("docker.install_path"))
                        .thenReturn("/opt/datasophon/bin/docker");

                Assertions.assertEquals("/opt/datasophon/bin/docker", new DockerClient().getDockerPath());
            }
        }
    }

    /**
     * load 方法相关测试
     */
    @Nested
    @DisplayName("load 方法测试")
    class LoadTests {

        @Test
        @DisplayName("load 镜像成功")
        void testLoad_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("Loaded image: myimage:latest\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                File mockFile = new File("/path/to/image.tar");

                String result = client.load(mockFile, null);

                // load 方法返回去除 "Loaded image:" 前缀后的内容
                Assertions.assertEquals(" myimage:latest\n", result);
            }
        }

        @Test
        @DisplayName("load 镜像成功 - 输出格式正确")
        void testLoad_Success_CorrectFormat() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("Loaded image: registry.example.com/myimage:v1.0\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                File mockFile = new File("/path/to/image.tar");

                String result = client.load(mockFile, null);

                Assertions.assertEquals(" registry.example.com/myimage:v1.0\n", result);
            }
        }

        @Test
        @DisplayName("load 命令成功但无输出时抛 DockerException 而非 NPE")
        void testLoad_Success_NullOutput() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                // getExecOut() 未 stub，返回 null——ExecResult.execOut 无默认值

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertThrows(
                        DockerException.class, () -> client.load(new File("/path/to/image.tar"), null));
            }
        }

        @Test
        @DisplayName("load 镜像失败 - 命令执行失败")
        void testLoad_Failure_CommandFailed() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("exit code: 1");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                File mockFile = new File("/path/to/image.tar");

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.load(mockFile, null));

                Assertions.assertTrue(exception.getMessage().contains("image.tar"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }

        @Test
        @DisplayName("load 镜像失败 - 输出格式不正确")
        void testLoad_Failure_InvalidOutputFormat() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("Some unexpected output\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                File mockFile = new File("/path/to/image.tar");

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.load(mockFile, null));

                Assertions.assertTrue(exception.getMessage().contains("image.tar"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * tagImage 方法相关测试
     */
    @Nested
    @DisplayName("tagImage 方法测试")
    class TagImageTests {

        @Test
        @DisplayName("tagImage 成功")
        void testTagImage_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.tagImage("oldimage:oldtag", "newimage:newtag"));
            }
        }

        @Test
        @DisplayName("tagImage 失败")
        void testTagImage_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("exit code: 1");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.tagImage("oldimage:oldtag", "newimage:newtag"));

                // 验证异常消息包含关键信息
                Assertions.assertTrue(exception.getMessage().contains("oldimage:oldtag"));
                Assertions.assertTrue(exception.getMessage().contains("newimage:newtag"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * removeImage 方法相关测试
     */
    @Nested
    @DisplayName("removeImage 方法测试")
    class RemoveImageTests {

        @Test
        @DisplayName("removeImage 成功")
        void testRemoveImage_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.removeImage("myimage:latest"));
            }
        }

        @Test
        @DisplayName("removeImage 失败")
        void testRemoveImage_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("image in use");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.removeImage("myimage:latest"));

                Assertions.assertTrue(exception.getMessage().contains("myimage:latest"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * push 方法相关测试
     */
    @Nested
    @DisplayName("push 方法测试")
    class PushTests {

        @Test
        @DisplayName("push 成功")
        void testPush_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.push("registry.example.com/myimage:v1.0"));
            }
        }

        @Test
        @DisplayName("push 失败")
        void testPush_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("denied: access forbidden");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.push("registry.example.com/myimage:v1.0"));

                Assertions.assertTrue(exception.getMessage().contains("registry.example.com/myimage:v1.0"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * login 方法相关测试
     */
    @Nested
    @DisplayName("login 方法测试")
    class LoginTests {

        @Test
        @DisplayName("login 成功")
        void testLogin_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithStdin(any(), any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.login("https://registry.example.com", "myuser", "mypassword"));

                // 验证使用了 execWithStdin 而非 execWithBash（安全传递密码）
                verify(mockResult, times(1)).isSuccess();
            }
        }

        @Test
        @DisplayName("login 失败")
        void testLogin_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("unauthorized");

                mockedShellUtils.when(() -> ShellUtils.execWithStdin(any(), any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.login("https://registry.example.com", "myuser", "wrongpassword"));

                Assertions.assertTrue(exception.getMessage().contains("registry.example.com"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * logout 方法相关测试
     */
    @Nested
    @DisplayName("logout 方法测试")
    class LogoutTests {

        @Test
        @DisplayName("logout 成功")
        void testLogout_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.logout("registry.example.com"));
            }
        }

        @Test
        @DisplayName("logout 失败")
        void testLogout_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("not logged in");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.logout("registry.example.com"));

                Assertions.assertTrue(exception.getMessage().contains("registry.example.com"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * rmManifest 方法相关测试
     */
    @Nested
    @DisplayName("rmManifest 方法测试")
    class RmManifestTests {

        @Test
        @DisplayName("rmManifest 成功")
        void testRmManifest_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.rmManifest("myimage:manifest", false));
            }
        }

        @Test
        @DisplayName("rmManifest 失败 - 不忽略错误")
        void testRmManifest_Failure_DontIgnore() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("manifest not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.rmManifest("myimage:manifest", false));

                Assertions.assertTrue(exception.getMessage().contains("删除 manifest myimage:manifest 失败"));
            }
        }

        @Test
        @DisplayName("rmManifest 失败 - manifest 不存在时忽略错误")
        void testRmManifest_Failure_Ignore() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getExecOut()).thenReturn("Error: No such manifest: myimage:manifest");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                // ignoreErrorIfAbsent=true 且确为"不存在"时不应抛出异常
                Assertions.assertDoesNotThrow(() -> client.rmManifest("myimage:manifest", true));
            }
        }

        @Test
        @DisplayName("rmManifest 失败 - 非\"不存在\"错误即使 ignoreErrorIfAbsent 也应抛出")
        void testRmManifest_Failure_Ignore_OtherError() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                // execOut 为 null（命令失败无 stdout），不得因此 NPE，应正常抛出 DockerException
                when(mockResult.getErrorTraceMessage()).thenReturn("permission denied");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(
                        DockerException.class, () -> client.rmManifest("myimage:manifest", true));
                Assertions.assertTrue(exception.getMessage().contains("permission denied"));
            }
        }
    }

    /**
     * createManifest 方法相关测试
     */
    @Nested
    @DisplayName("createManifest 方法测试")
    class CreateManifestTests {

        @Test
        @DisplayName("createManifest 成功 - 不使用 insecure")
        void testCreateManifest_Success_NoInsecure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                List<String> tags = Arrays.asList("image1:tag1", "image2:tag2");

                Assertions.assertDoesNotThrow(() -> client.createManifest("myimage:manifest", tags, false));
            }
        }

        @Test
        @DisplayName("createManifest 成功 - 使用 insecure")
        void testCreateManifest_Success_WithInsecure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                List<String> tags = Collections.singletonList("image1:tag1");

                Assertions.assertDoesNotThrow(() -> client.createManifest("myimage:manifest", tags, true));
            }
        }

        @Test
        @DisplayName("createManifest 失败")
        void testCreateManifest_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("invalid reference format");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                List<String> tags = Arrays.asList("image1:tag1", "image2:tag2");

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.createManifest("invalid-manifest", tags, false));

                Assertions.assertTrue(exception.getMessage().contains("创建 manifest invalid-manifest 失败"));
            }
        }

        @Test
        @DisplayName("createManifest 空 tags 列表")
        void testCreateManifest_EmptyTags() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.createManifest("myimage:manifest", Collections.emptyList(), false));
            }
        }
    }

    /**
     * annotateManifest 方法相关测试
     */
    @Nested
    @DisplayName("annotateManifest 方法测试")
    class AnnotateManifestTests {

        @Test
        @DisplayName("annotateManifest 成功")
        void testAnnotateManifest_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.annotateManifest("myimage:manifest", "image1:tag1", "amd64", "linux"));
            }
        }

        @Test
        @DisplayName("annotateManifest 失败")
        void testAnnotateManifest_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("manifest not found");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.annotateManifest("myimage:manifest", "image1:tag1", "amd64", "linux"));

                Assertions.assertTrue(exception.getMessage().contains("myimage:manifest"));
                Assertions.assertTrue(exception.getMessage().contains("image1:tag1"));
                Assertions.assertTrue(exception.getMessage().contains("失败"));
            }
        }
    }

    /**
     * pushManifest 方法相关测试
     */
    @Nested
    @DisplayName("pushManifest 方法测试")
    class PushManifestTests {

        @Test
        @DisplayName("pushManifest 成功 - 不使用 insecure")
        void testPushManifest_Success_NoInsecure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.pushManifest("myimage:manifest", false));
            }
        }

        @Test
        @DisplayName("pushManifest 成功 - 使用 insecure")
        void testPushManifest_Success_WithInsecure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                Assertions.assertDoesNotThrow(() -> client.pushManifest("myimage:manifest", true));
            }
        }

        @Test
        @DisplayName("pushManifest 失败")
        void testPushManifest_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("denied: access forbidden");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.pushManifest("myimage:manifest", false));

                Assertions.assertTrue(exception.getMessage().contains("推送 manifest myimage:manifest 失败"));
            }
        }
    }

    /**
     * executeToString 方法相关测试（直接测试这个公共方法）
     */
    @Nested
    @DisplayName("executeToString 方法测试")
    class ExecuteToStringTests {

        @Test
        @DisplayName("executeToString 成功")
        void testExecuteToString_Success() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("command output\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                List<String> command = Arrays.asList("images", "--format", "{{.Repository}}");

                String result = client.executeToString(command, 30);

                Assertions.assertEquals("command output\n", result);
            }
        }

        @Test
        @DisplayName("executeToString 失败")
        void testExecuteToString_Failure() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(false);
                when(mockResult.getErrorTraceMessage()).thenReturn("exit code: 127");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenReturn(mockResult);

                DockerClient client = new DockerClient();
                List<String> command = Arrays.asList("invalid-command");

                DockerException exception = Assertions.assertThrows(DockerException.class, () -> client.executeToString(command, 30));

                Assertions.assertTrue(exception.getMessage().contains("docker 命令执行失败"));
            }
        }
    }

    /**
     * 命令参数验证测试
     */
    @Nested
    @DisplayName("命令参数验证测试")
    class CommandArgumentTests {

        @Test
        @DisplayName("load 命令参数正确")
        void testLoad_CommandArguments() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);
                when(mockResult.getExecOut()).thenReturn("Loaded image: test\n");

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("load"));
                            Assertions.assertTrue(args.contains("-i"));
                            Assertions.assertTrue(args.stream().anyMatch(a -> a.endsWith("image.tar")));
                            return mockResult;
                        });

                DockerClient client = new DockerClient();
                client.load(new File("/path/to/image.tar"), null);
            }
        }

        @Test
        @DisplayName("tag 命令参数正确")
        void testTag_CommandArguments() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            // 验证命令包含 tag 及其参数
                            Assertions.assertTrue(args.contains("tag"));
                            Assertions.assertTrue(args.contains("src:tag"));
                            Assertions.assertTrue(args.contains("dest:tag"));
                            return mockResult;
                        });

                DockerClient client = new DockerClient();
                client.tagImage("src:tag", "dest:tag");
            }
        }

        @Test
        @DisplayName("rmi 命令参数正确")
        void testRmi_CommandArguments() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithBash(any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("rmi"));
                            Assertions.assertTrue(args.contains("myimage:latest"));
                            return mockResult;
                        });

                DockerClient client = new DockerClient();
                client.removeImage("myimage:latest");
            }
        }

        @Test
        @DisplayName("login 命令参数正确")
        void testLogin_CommandArguments() {
            try (MockedStatic<ShellUtils> mockedShellUtils = Mockito.mockStatic(ShellUtils.class)) {
                ExecResult mockResult = mock(ExecResult.class);
                when(mockResult.isSuccess()).thenReturn(true);

                mockedShellUtils.when(() -> ShellUtils.execWithStdin(any(), any(), any(), anyLong()))
                        .thenAnswer(invocation -> {
                            List<String> args = invocation.getArgument(1);
                            Assertions.assertTrue(args.contains("login"));
                            Assertions.assertTrue(args.contains("-u"));
                            Assertions.assertTrue(args.contains("--password-stdin"));
                            return mockResult;
                        });

                DockerClient client = new DockerClient();
                client.login("registry.example.com", "user", "pass");
            }
        }
    }
}
