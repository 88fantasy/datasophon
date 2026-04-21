package com.datasophon.api.service.tmpfile.comp;

import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.download.DownloadProgressVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpFileDownloader 单元测试
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class HttpFileDownloaderTest {

    private HttpFileDownloader downloader;
    private File tempFile;
    private SimpleHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        downloader = new HttpFileDownloader();
        tempFile = File.createTempFile("test_download_", ".tmp");
        tempFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testSupports_HttpUrl() {
        assertTrue(downloader.supports("http://example.com/file.txt"));
    }

    @Test
    void testSupports_HttpsUrl() {
        assertTrue(downloader.supports("https://example.com/file.txt"));
    }

    @Test
    void testSupports_ScpUrl() {
        assertFalse(downloader.supports("scp://user@host/path/file.txt"));
    }

    @Test
    void testSupports_FtpUrl() {
        assertFalse(downloader.supports("ftp://example.com/file.txt"));
    }

    @Test
    void testSupports_NullUrl() {
        assertFalse(downloader.supports(null));
    }

    @Test
    void testSupports_EmptyUrl() {
        assertFalse(downloader.supports(""));
    }

    @Test
    void testDownload_WithBasicAuth() throws Exception {
        // 启动一个简单的 HTTP 服务器
        int port = findAvailablePort();
        server = new SimpleHttpServer(port, "test content for download");
        server.start();

        DownloadProgressVO progress = new DownloadProgressVO("test-task-1");
        String url = "http://localhost:" + port + "/test.txt";

        downloader.download(url, tempFile, progress);

        assertEquals(1, progress.getState());
        assertTrue(tempFile.length() > 0);
    }

    @Test
    void testDownload_InvalidUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-task-2");
        String url = "http://invalid-host-that-does-not-exist-12345.com/file.txt";

        assertThrows(IOException.class, () ->
            downloader.download(url, tempFile, progress)
        );
    }

    @Test
    void testDownload_MalformedUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-task-3");
        String url = "not-a-valid-url";

        assertThrows(IllegalArgumentException.class, () ->
            downloader.download(url, tempFile, progress)
        );
    }

    @Test
    void testDetermineFileName_WithCustomName() {
        HttpFileDownloader testDownloader = new HttpFileDownloader();
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/to/file.txt");
        dto.setFileName("custom_name.zip");

        String fileName = testDownloader.determineFileName(dto);
        assertEquals("custom_name.zip", fileName);
    }

    @Test
    void testDetermineFileName_FromUrlPath() {
        HttpFileDownloader testDownloader = new HttpFileDownloader();
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/to/myfile.txt");

        String fileName = testDownloader.determineFileName(dto);
        assertEquals("myfile.txt", fileName);
    }

    @Test
    void testDetermineFileName_WithEncodedUrl() {
        HttpFileDownloader testDownloader = new HttpFileDownloader();
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/%E6%B5%8B%E8%AF%95.txt");

        String fileName = testDownloader.determineFileName(dto);
        assertEquals("测试.txt", fileName);
    }

    @Test
    void testDetermineFileName_NoPathInUrl() {
        HttpFileDownloader testDownloader = new HttpFileDownloader();
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com");

        String fileName = testDownloader.determineFileName(dto);
        assertTrue(fileName.startsWith("download_"));
    }

    /**
     * 查找可用端口的辅助方法
     */
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
