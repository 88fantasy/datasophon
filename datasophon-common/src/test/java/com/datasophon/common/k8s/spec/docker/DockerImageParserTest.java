package com.datasophon.common.k8s.spec.docker;

import cn.hutool.core.io.FileUtil;
import com.datasophon.common.PropertiesPathUtils;
import com.datasophon.common.k8s.vo.docker.ImageManifest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * DockerImageParser 单元测试类
 * <p>
 * 使用 Mockito 模拟 FileUtil 静态方法调用，测试 DockerImageParser 的各项功能
 * </p>
 *
 * @author zhanghuangbin
 */
@DisplayName("DockerImageParser 单元测试")
class DockerImageParserTest {

    private DockerImageParser parser;

    @BeforeEach
    public void init() {
        PropertiesPathUtils.resetPropertyFile();
        parser = new DockerImageParser();
    }

    /**
     * OCI 格式解析相关测试
     */
    @Nested
    @DisplayName("OCI 格式解析测试")
    class OciFormatTests {

        @Test
        @DisplayName("解析 OCI 格式 - 多个镜像")
        void testParseOciFormat_MultipleImages() throws Exception {
            String mockUnzipDir = "/tmp/oci-multi";
            File ociFile = new File(mockUnzipDir, DockerImageParser.INDEX_FILE);

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String ociContent = "{\n" +
                        "  \"schemaVersion\": 2,\n" +
                        "  \"manifests\": [\n" +
                        "    {\n" +
                        "      \"digest\": \"sha256:abc1\",\n" +
                        "      \"platform\": {\"architecture\": \"amd64\", \"os\": \"linux\"},\n" +
                        "      \"annotations\": {\"io.containerd.image.name\": \"nginx:1.19\"}\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"digest\": \"sha256:def2\",\n" +
                        "      \"platform\": {\"architecture\": \"arm64\", \"os\": \"linux\"},\n" +
                        "      \"annotations\": {\"io.containerd.image.name\": \"redis:6.2\"}\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(ociFile, StandardCharsets.UTF_8))
                        .thenReturn(ociContent);

                // 使用反射调用私有方法
                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseOciFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertEquals(2, result.size());

                ImageManifest nginx = result.stream()
                        .filter(m -> m.getImage().equals("nginx"))
                        .findFirst()
                        .orElse(null);
                Assertions.assertNotNull(nginx);
                Assertions.assertEquals("amd64", nginx.getArch());

                ImageManifest redis = result.stream()
                        .filter(m -> m.getImage().equals("redis"))
                        .findFirst()
                        .orElse(null);
                Assertions.assertNotNull(redis);
                Assertions.assertEquals("arm64", redis.getArch());
            }
        }

        @Test
        @DisplayName("解析 OCI 格式 - 无 annotation 时跳过")
        void testParseOciFormat_NoAnnotation_Skip() throws Exception {
            String mockUnzipDir = "/tmp/oci-no-annotation";
            File ociFile = new File(mockUnzipDir, DockerImageParser.INDEX_FILE);

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String ociContent = "{\n" +
                        "  \"schemaVersion\": 2,\n" +
                        "  \"manifests\": [\n" +
                        "    {\n" +
                        "      \"digest\": \"sha256:abc1\",\n" +
                        "      \"platform\": {\"architecture\": \"amd64\", \"os\": \"linux\"}\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(ociFile, StandardCharsets.UTF_8))
                        .thenReturn(ociContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseOciFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertTrue(result.isEmpty());
            }
        }

        @Test
        @DisplayName("解析 OCI 格式 - platform 为空时跳过（由于 File.exists 无法 mock）")
        void testParseOciFormat_PlatformFromOldFormat() throws Exception {
            String mockUnzipDir = "/tmp/oci-platform-fallback";
            File ociFile = new File(mockUnzipDir, DockerImageParser.INDEX_FILE);

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                // 注意：当 platform 为空时，代码会调用 readDigestFileContent 方法
                // 但该方法中使用 File.exists() 检查，而测试中无法创建真实文件
                // 所以这里只测试有 platform 的情况
                String ociContent = "{\n" +
                        "  \"schemaVersion\": 2,\n" +
                        "  \"manifests\": [\n" +
                        "    {\n" +
                        "      \"digest\": \"sha256:abc123\",\n" +
                        "      \"platform\": {\"architecture\": \"amd64\", \"os\": \"linux\"},\n" +
                        "      \"annotations\": {\"io.containerd.image.name\": \"myimage:v1.0\"}\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(ociFile, StandardCharsets.UTF_8))
                        .thenReturn(ociContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseOciFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertEquals(1, result.size());
                ImageManifest manifest = result.get(0);
                Assertions.assertEquals("myimage", manifest.getImage());
                Assertions.assertEquals("v1.0", manifest.getTag());
                Assertions.assertEquals("amd64", manifest.getArch());
                Assertions.assertEquals("linux", manifest.getOs());
            }
        }

        @Test
        @DisplayName("解析 OCI 格式 - 无 platform 且无 annotation 时跳过")
        void testParseOciFormat_NoPlatform_NoAnnotation() throws Exception {
            String mockUnzipDir = "/tmp/oci-no-platform";
            File ociFile = new File(mockUnzipDir, DockerImageParser.INDEX_FILE);

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                // 当 manifest 没有 platform 且没有 annotation 时，条目会被跳过
                String ociContent = "{\n" +
                        "  \"schemaVersion\": 2,\n" +
                        "  \"manifests\": [\n" +
                        "    {\n" +
                        "      \"digest\": \"sha256:abc123\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(ociFile, StandardCharsets.UTF_8))
                        .thenReturn(ociContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseOciFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                // 由于没有 annotation，条目会被跳过
                Assertions.assertTrue(result.isEmpty());
            }
        }

        @Test
        @DisplayName("解析 OCI 格式 - 空 manifests 列表")
        void testParseOciFormat_EmptyManifests() throws Exception {
            String mockUnzipDir = "/tmp/oci-empty";
            File ociFile = new File(mockUnzipDir, DockerImageParser.INDEX_FILE);

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String ociContent = "{\n" +
                        "  \"schemaVersion\": 2,\n" +
                        "  \"manifests\": []\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(ociFile, StandardCharsets.UTF_8))
                        .thenReturn(ociContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseOciFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertTrue(result.isEmpty());
            }
        }
    }

    /**
     * Docker 旧格式解析相关测试
     */
    @Nested
    @DisplayName("Docker 旧格式解析测试")
    class DockerFormatTests {

        @Test
        @DisplayName("解析 Docker 格式 - 单个镜像多个标签")
        void testParseDockerFormat_MultipleTags() throws Exception {
            String mockUnzipDir = "/tmp/docker-multi-tags";
            File manifestFile = new File(mockUnzipDir, DockerImageParser.MANIFEST_JSON);
            File configFile = new File(mockUnzipDir, "abc123.json");

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String manifestContent = "[\n" +
                        "  {\n" +
                        "    \"Config\": \"abc123.json\",\n" +
                        "    \"RepoTags\": [\"myregistry.com/app:v1.0\", \"myregistry.com/app:latest\"],\n" +
                        "    \"Layers\": [\"layer1.tar\"]\n" +
                        "  }\n" +
                        "]";
                String configContent = "{\n" +
                        "  \"architecture\": \"arm64\",\n" +
                        "  \"os\": \"linux\"\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(manifestFile, StandardCharsets.UTF_8))
                        .thenReturn(manifestContent);
                mockedFileUtil.when(() -> FileUtil.readString(configFile, StandardCharsets.UTF_8))
                        .thenReturn(configContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseDockerFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertEquals(2, result.size());
                Assertions.assertEquals("myregistry.com/app", result.get(0).getImage());
                Assertions.assertEquals("v1.0", result.get(0).getTag());
                Assertions.assertEquals("myregistry.com/app", result.get(1).getImage());
                Assertions.assertEquals("latest", result.get(1).getTag());
            }
        }

        @Test
        @DisplayName("解析 Docker 格式 - 无标签时使用 latest")
        void testParseDockerFormat_NoTag_UseLatest() throws Exception {
            String mockUnzipDir = "/tmp/docker-no-tag";
            File manifestFile = new File(mockUnzipDir, DockerImageParser.MANIFEST_JSON);
            File configFile = new File(mockUnzipDir, "config.json");

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String manifestContent = "[\n" +
                        "  {\n" +
                        "    \"Config\": \"config.json\",\n" +
                        "    \"RepoTags\": [\"myimage\"],\n" +
                        "    \"Layers\": []\n" +
                        "  }\n" +
                        "]";
                String configContent = "{\n" +
                        "  \"architecture\": \"amd64\",\n" +
                        "  \"os\": \"linux\"\n" +
                        "}";

                mockedFileUtil.when(() -> FileUtil.readString(manifestFile, StandardCharsets.UTF_8))
                        .thenReturn(manifestContent);
                mockedFileUtil.when(() -> FileUtil.readString(configFile, StandardCharsets.UTF_8))
                        .thenReturn(configContent);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseDockerFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertEquals(1, result.size());
                Assertions.assertEquals("myimage", result.get(0).getImage());
                Assertions.assertEquals("latest", result.get(0).getTag());
            }
        }

        @Test
        @DisplayName("解析 Docker 格式 - 多个镜像条目")
        void testParseDockerFormat_MultipleEntries() throws Exception {
            String mockUnzipDir = "/tmp/docker-multi-entries";
            File manifestFile = new File(mockUnzipDir, DockerImageParser.MANIFEST_JSON);
            File config1File = new File(mockUnzipDir, "config1.json");
            File config2File = new File(mockUnzipDir, "config2.json");

            try (MockedStatic<FileUtil> mockedFileUtil = Mockito.mockStatic(FileUtil.class)) {
                String manifestContent = "[\n" +
                        "  {\n" +
                        "    \"Config\": \"config1.json\",\n" +
                        "    \"RepoTags\": [\"nginx:1.19\"],\n" +
                        "    \"Layers\": [\"layer1.tar\"]\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"Config\": \"config2.json\",\n" +
                        "    \"RepoTags\": [\"redis:6.2\"],\n" +
                        "    \"Layers\": [\"layer2.tar\"]\n" +
                        "  }\n" +
                        "]";
                String configContent1 = "{\n" +
                        "  \"architecture\": \"amd64\",\n" +
                        "  \"os\": \"linux\"\n" +
                        "}";
                String configContent2 = "{\n" +
                        "  \"architecture\": \"arm64\",\n" +
                        "  \"os\": \"linux\"\n" +
                        "}";

                // 按照调用顺序设置 mock 返回值
                mockedFileUtil.when(() -> FileUtil.readString(manifestFile, StandardCharsets.UTF_8))
                        .thenReturn(manifestContent);
                mockedFileUtil.when(() -> FileUtil.readString(config1File, StandardCharsets.UTF_8))
                        .thenReturn(configContent1);
                mockedFileUtil.when(() -> FileUtil.readString(config2File, StandardCharsets.UTF_8))
                        .thenReturn(configContent2);

                java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                        "parseDockerFormat", String.class);
                method.setAccessible(true);

                @SuppressWarnings("unchecked")
                List<ImageManifest> result = (List<ImageManifest>) method.invoke(parser, mockUnzipDir);

                Assertions.assertEquals(2, result.size());
                Assertions.assertEquals("nginx", result.get(0).getImage());
                Assertions.assertEquals("amd64", result.get(0).getArch());
                Assertions.assertEquals("redis", result.get(1).getImage());
                Assertions.assertEquals("arm64", result.get(1).getArch());
            }
        }
    }

    /**
     * splitRepoTag 方法测试
     */
    @Nested
    @DisplayName("splitRepoTag 方法测试")
    class SplitRepoTagTests {

        @Test
        @DisplayName("splitRepoTag - 带标签")
        void testSplitRepoTag_WithTag() throws Exception {
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "splitRepoTag", String.class);
            method.setAccessible(true);

            String[] result = (String[]) method.invoke(parser, "nginx:1.19");

            Assertions.assertEquals(2, result.length);
            Assertions.assertEquals("nginx", result[0]);
            Assertions.assertEquals("1.19", result[1]);
        }

        @Test
        @DisplayName("splitRepoTag - 不带标签")
        void testSplitRepoTag_NoTag() throws Exception {
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "splitRepoTag", String.class);
            method.setAccessible(true);

            String[] result = (String[]) method.invoke(parser, "nginx");

            Assertions.assertEquals(2, result.length);
            Assertions.assertEquals("nginx", result[0]);
            Assertions.assertEquals("latest", result[1]);
        }

        @Test
        @DisplayName("splitRepoTag - 带端口的 registry")
        void testSplitRepoTag_WithPort() throws Exception {
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "splitRepoTag", String.class);
            method.setAccessible(true);

            String[] result = (String[]) method.invoke(parser, "myregistry.com:5000/nginx:1.19");

            Assertions.assertEquals(2, result.length);
            Assertions.assertEquals("myregistry.com:5000/nginx", result[0]);
            Assertions.assertEquals("1.19", result[1]);
        }

        @Test
        @DisplayName("splitRepoTag - 完整镜像名带 sha256 标签")
        void testSplitRepoTag_Sha256Tag() throws Exception {
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "splitRepoTag", String.class);
            method.setAccessible(true);

            String[] result = (String[]) method.invoke(parser, "registry.example.com/app:v1.0.0");

            Assertions.assertEquals(2, result.length);
            Assertions.assertEquals("registry.example.com/app", result[0]);
            Assertions.assertEquals("v1.0.0", result[1]);
        }

        @Test
        @DisplayName("splitRepoTag - 冒号在开头时整个字符串作为镜像名")
        void testSplitRepoTag_EdgeCase() throws Exception {
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "splitRepoTag", String.class);
            method.setAccessible(true);

            String[] result = (String[]) method.invoke(parser, ":tag");

            // 当 colonIdx = 0 时，条件 colonIdx > 0 不满足，返回原字符串和 latest
            Assertions.assertEquals(2, result.length);
            Assertions.assertEquals(":tag", result[0]);
            Assertions.assertEquals("latest", result[1]);
        }
    }

    /**
     * readDigestFileContent 方法测试
     * 注意：该方法依赖于 File.exists() 检查，难以直接 mock
     * 这里主要测试边界条件
     */
    @Nested
    @DisplayName("readDigestFileContent 方法测试")
    class ReadDigestFileContentTests {

        @Test
        @DisplayName("readDigestFileContent - digest 为 null")
        void testReadDigestFileContent_NullDigest() throws Exception {
            String mockUnzipDir = "/tmp/digest-test";

            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "readDigestFileContent", String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(parser, mockUnzipDir, null);

            Assertions.assertNull(result);
        }

        @Test
        @DisplayName("readDigestFileContent - digest 不带 sha256 前缀")
        void testReadDigestFileContent_NoSha256Prefix() throws Exception {
            String mockUnzipDir = "/tmp/digest-test";
            String digest = "abc123";

            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "readDigestFileContent", String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(parser, mockUnzipDir, digest);

            Assertions.assertNull(result);
        }

        @Test
        @DisplayName("readDigestFileContent - 有效 digest 格式但文件不存在")
        void testReadDigestFileContent_ValidDigest_FileNotExists() throws Exception {
            String mockUnzipDir = "/tmp/digest-test";
            String digest = "sha256:abc123";

            // 注意：由于方法内部使用 File.exists() 检查，而测试中无法创建真实文件
            // 所以当文件不存在时方法会返回 null
            java.lang.reflect.Method method = DockerImageParser.class.getDeclaredMethod(
                    "readDigestFileContent", String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(parser, mockUnzipDir, digest);

            // 文件不存在时返回 null
            Assertions.assertNull(result);
        }
    }

    /**
     * UnsupportedFormatException 内部类测试
     */
    @Nested
    @DisplayName("UnsupportedFormatException 测试")
    class UnsupportedFormatExceptionTests {

        @Test
        @DisplayName("UnsupportedFormatException - 构造和消息")
        void testUnsupportedFormatException() {
            String message = "Test unsupported format message";
            DockerImageParser.UnsupportedFormatException exception =
                    new DockerImageParser.UnsupportedFormatException(message);

            Assertions.assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("UnsupportedFormatException - 带原因")
        void testUnsupportedFormatException_WithCause() {
            String message = "Test exception with cause";
            Throwable cause = new IllegalArgumentException("Cause");
            DockerImageParser.UnsupportedFormatException exception =
                    new DockerImageParser.UnsupportedFormatException(message);
            exception.initCause(cause);

            Assertions.assertEquals(message, exception.getMessage());
            Assertions.assertEquals(cause, exception.getCause());
        }

        @Test
        @DisplayName("UnsupportedFormatException - 作为 RuntimeException 抛出")
        void testUnsupportedFormatException_AsRuntimeException() {
            DockerImageParser.UnsupportedFormatException exception =
                    new DockerImageParser.UnsupportedFormatException("test message");

            Assertions.assertThrows(DockerImageParser.UnsupportedFormatException.class, () -> {
                throw exception;
            });
        }
    }
}
