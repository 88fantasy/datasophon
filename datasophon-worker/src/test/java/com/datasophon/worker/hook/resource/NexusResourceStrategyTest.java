package com.datasophon.worker.hook.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.DownloadResult;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.hook.HookContext;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import cn.hutool.crypto.digest.DigestUtil;

class NexusResourceStrategyTest {

    private static final byte[] CONTENT = "jar-content".getBytes(StandardCharsets.UTF_8);

    private static final String CONTENT_MD5 = DigestUtil.md5Hex(CONTENT);

    static {
        URL url = NexusResourceStrategyTest.class.getClassLoader().getResource("common-test.properties");
        if (url != null) {
            System.setProperty("commonPropertiesLocation", url.getPath());
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void copiesResourceWithoutSubDirForYarn() throws IOException {
        HookContext context = newContext("spark-3.4.2-yarn-shuffle.jar", "spark-3.4.2-yarn-shuffle.jar", null);
        PackageStorage storage = storageReturning("spark-3.4.2-yarn-shuffle.jar", CONTENT);

        ExecResult result = invoke(context, storage);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(tempDir.resolve("install/spark-3.4.2-yarn-shuffle.jar"))).isEqualTo(CONTENT);
        verify(storage).downloadResourceToLocal("spark-3.4.2-yarn-shuffle.jar");
    }

    @Test
    void copiesPluginsResourceIntoJarsForSpark3() throws IOException {
        HookContext context = newContext("plugins/paimon-spark-3.5-1.2.0.jar", "jars/paimon-spark-3.5-1.2.0.jar", null);
        PackageStorage storage = storageReturning("plugins/paimon-spark-3.5-1.2.0.jar", CONTENT);

        ExecResult result = invoke(context, storage);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(tempDir.resolve("install/jars/paimon-spark-3.5-1.2.0.jar"))).isEqualTo(CONTENT);
        verify(storage).downloadResourceToLocal("plugins/paimon-spark-3.5-1.2.0.jar");
    }

    @Test
    void copiesResourceWhenMd5Matches() throws IOException {
        HookContext context = newContext("plugins/seatunnel/connector-jdbc.jar", "connectors/connector-jdbc.jar", CONTENT_MD5);
        PackageStorage storage = storageReturning("plugins/seatunnel/connector-jdbc.jar", CONTENT);

        ExecResult result = invoke(context, storage);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readAllBytes(tempDir.resolve("install/connectors/connector-jdbc.jar"))).isEqualTo(CONTENT);
    }

    @Test
    void skipsDownloadWhenTargetMd5Matches() throws IOException {
        assertSkipsDownload(CONTENT_MD5);
    }

    /** 下载后的校验不区分大小写，已存在跳过的判断也必须一致，否则大写 md5 会每次重下。 */
    @Test
    void skipsDownloadWhenDeclaredMd5IsUpperCase() throws IOException {
        assertSkipsDownload(CONTENT_MD5.toUpperCase());
    }

    private void assertSkipsDownload(String declaredMd5) throws IOException {
        Path target = tempDir.resolve("install/jars/paimon-spark-3.5-1.2.0.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, CONTENT);
        HookContext context = newContext("plugins/paimon-spark-3.5-1.2.0.jar", "jars/paimon-spark-3.5-1.2.0.jar", declaredMd5);

        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.resolve("install").toString());

            ExecResult result = new NexusResourceStrategy().invoke(context);

            assertThat(result.isSuccess()).isTrue();
            storageUtils.verifyNoInteractions();
        }
    }

    @Test
    void rejectsMismatchedMd5WithoutReplacingExistingTarget() throws IOException {
        byte[] oldContent = "old-content".getBytes(StandardCharsets.UTF_8);
        Path target = tempDir.resolve("install/jars/paimon-spark-3.5-1.2.0.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, oldContent);
        String expectedMd5 = DigestUtil.md5Hex("expected-content");
        HookContext context = newContext("plugins/paimon-spark-3.5-1.2.0.jar", "jars/paimon-spark-3.5-1.2.0.jar", expectedMd5);
        PackageStorage storage = storageReturning("plugins/paimon-spark-3.5-1.2.0.jar", CONTENT);

        ExecResult result = invoke(context, storage);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExecOut()).contains("MD5");
        assertThat(Files.readAllBytes(target)).isEqualTo(oldContent);
    }

    private ExecResult invoke(HookContext context, PackageStorage storage) {
        try (
                MockedStatic<PkgInstallPathUtils> installPath = mockStatic(PkgInstallPathUtils.class);
                MockedStatic<StorageUtils> storageUtils = mockStatic(StorageUtils.class)) {
            installPath.when(() -> PkgInstallPathUtils.getInstallHome(context)).thenReturn(tempDir.resolve("install").toString());
            storageUtils.when(StorageUtils::getPackageStorage).thenReturn(storage);
            return new NexusResourceStrategy().invoke(context);
        }
    }

    /** 模拟存储已把资源缓存到本地 cache/<from>。 */
    private PackageStorage storageReturning(String from, byte[] content) throws IOException {
        Path cached = tempDir.resolve("cache").resolve(from);
        Files.createDirectories(cached.getParent());
        Files.write(cached, content);
        DownloadResult downloadResult = new DownloadResult();
        downloadResult.setTarget(cached.toString());
        PackageStorage storage = mock(PackageStorage.class);
        when(storage.downloadResourceToLocal(from)).thenReturn(downloadResult);
        return storage;
    }

    private HookContext newContext(String from, String to, String md5) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("to", to);
        if (md5 != null) {
            params.put("md5", md5);
        }
        HookContext context = new HookContext();
        context.setServiceName("SPARK3");
        context.setServiceRoleName("SparkClient3");
        context.setParams(params);
        context.setGlobalVariables(new HashMap<>());
        return context;
    }
}
