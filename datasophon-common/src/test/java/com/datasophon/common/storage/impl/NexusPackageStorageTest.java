package com.datasophon.common.storage.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.storage.vo.DownloadResult;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.client.CommonNexusClient;
import com.datasophon.common.utils.nexus.client.RawRepoClient;
import com.datasophon.common.utils.nexus.dto.AssertQueryDTO;
import com.datasophon.common.utils.nexus.vo.Assert;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import cn.hutool.crypto.digest.DigestUtil;

class NexusPackageStorageTest {

    private static final byte[] CONTENT = "jar-content".getBytes(StandardCharsets.UTF_8);

    private static final String CONTENT_MD5 = DigestUtil.md5Hex(CONTENT);

    @TempDir
    Path cacheDir;

    private MockedStatic<NexusFacade> facade;

    private RecordingRawRepoClient rawClient;

    private CommonNexusClient commonClient;

    private final List<String> downloadUrls = new ArrayList<>();

    private NexusPackageStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        facade = mockStatic(NexusFacade.class);
        NexusUri uri = new NexusUri();
        uri.setUri("http://nexus:8081");
        facade.when(NexusFacade::getNexusUri).thenReturn(uri);
        rawClient = new RecordingRawRepoClient();
        commonClient = mock(CommonNexusClient.class);
        doAnswer(invocation -> {
            downloadUrls.add(invocation.getArgument(0));
            OutputStream out = invocation.getArgument(1);
            out.write(CONTENT);
            return null;
        }).when(commonClient).download(anyString(), any(OutputStream.class));
        facade.when(NexusFacade::getRawRepoClient).thenReturn(rawClient);
        facade.when(NexusFacade::getCommonClient).thenReturn(commonClient);
        storage = new NexusPackageStorage(cacheDir.toString()) {

            @Override
            protected void ensureNexusEnable() {
            }
        };
    }

    @AfterEach
    void tearDown() {
        facade.close();
    }

    @Test
    void queriesRemoteMd5UnderPackagesPrefix() {
        DownloadResult result = storage.downloadResourceToLocal("spark-3.4.2-yarn-shuffle.jar");

        assertEquals(1, rawClient.queries.size());
        assertEquals("/packages", rawClient.queries.get(0).getGroup());
        // 已在 ddh Nexus 上实测，name 必须带前导 /（不带时搜索结果为 0 条）
        assertEquals("/packages/spark-3.4.2-yarn-shuffle.jar", rawClient.queries.get(0).getName());
        assertEquals(CONTENT_MD5, result.getMd5());
    }

    @Test
    void queriesRemoteMd5UnderPackagesPrefixForSubDirResource() {
        storage.downloadResourceToLocal("plugins/seatunnel/connector-jdbc.jar");

        assertEquals(1, rawClient.queries.size());
        assertEquals("/packages/plugins/seatunnel", rawClient.queries.get(0).getGroup());
        // 已在 ddh Nexus 上实测，name 必须带前导 /（不带时搜索结果为 0 条）
        assertEquals("/packages/plugins/seatunnel/connector-jdbc.jar", rawClient.queries.get(0).getName());
    }

    @Test
    void skipsDownloadWhenCachedFileMatchesRemoteMd5() throws Exception {
        Path cached = cacheDir.resolve("spark-3.4.2-yarn-shuffle.jar");
        Files.write(cached, CONTENT);

        DownloadResult result = storage.downloadResourceToLocal("spark-3.4.2-yarn-shuffle.jar");

        assertFalse(result.isChange());
        assertEquals(cached.toAbsolutePath().toString(), result.getTarget());
        verify(commonClient, never()).download(anyString(), any(OutputStream.class));
    }

    @Test
    void createsParentDirsForTwoLevelSubDirResource() throws Exception {
        DownloadResult result = storage.downloadResourceToLocal("plugins/seatunnel/connector-jdbc.jar");

        Path cached = cacheDir.resolve("plugins/seatunnel/connector-jdbc.jar");
        assertTrue(result.isChange());
        assertEquals(cached.toAbsolutePath().toString(), result.getTarget());
        assertArrayEquals(CONTENT, Files.readAllBytes(cached));
        assertEquals(List.of("http://nexus:8081/repository/raw/packages/plugins/seatunnel/connector-jdbc.jar"), downloadUrls);
    }

    @Test
    void downloadsSingleLevelPluginsResource() throws Exception {
        DownloadResult result = storage.downloadResourceToLocal("plugins/paimon-spark-3.5-1.2.0.jar");

        Path cached = cacheDir.resolve("plugins/paimon-spark-3.5-1.2.0.jar");
        assertEquals(cached.toAbsolutePath().toString(), result.getTarget());
        assertArrayEquals(CONTENT, Files.readAllBytes(cached));
        assertEquals(List.of("http://nexus:8081/repository/raw/packages/plugins/paimon-spark-3.5-1.2.0.jar"), downloadUrls);
    }

    @Test
    void downloadsResourceWithoutSubDir() throws Exception {
        DownloadResult result = storage.downloadResourceToLocal("spark-3.4.2-yarn-shuffle.jar");

        Path cached = cacheDir.resolve("spark-3.4.2-yarn-shuffle.jar");
        assertTrue(result.isChange());
        assertArrayEquals(CONTENT, Files.readAllBytes(cached));
        assertEquals(List.of("http://nexus:8081/repository/raw/packages/spark-3.4.2-yarn-shuffle.jar"), downloadUrls);
    }

    /** 记录传给 Nexus 资产查询的 group/name，按查询返回固定 md5。 */
    private static class RecordingRawRepoClient extends RawRepoClient {

        private final List<AssertQueryDTO> queries = new ArrayList<>();

        RecordingRawRepoClient() {
            super(NexusFacade.RAW_REPO);
        }

        @Override
        protected Assert getAssert(String repo, AssertQueryDTO query) {
            queries.add(query);
            Assert.Checksum checksum = new Assert.Checksum();
            checksum.setMd5(CONTENT_MD5);
            Assert item = new Assert();
            item.setChecksum(checksum);
            return item;
        }
    }
}
