package com.datasophon.api.service.tmpfile.comp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DownloaderFactory 单元测试
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class DownloaderFactoryTest {

    @Test
    void testGetDownloader_HttpUrl() {
        // 测试 HTTP URL
        RemoteFileDownloader downloader = DownloaderFactory.getDownloader("http://example.com/file.txt");
        assertNotNull(downloader);
        assertTrue(downloader instanceof HttpFileDownloader);
    }

    @Test
    void testGetDownloader_HttpsUrl() {
        // 测试 HTTPS URL
        RemoteFileDownloader downloader = DownloaderFactory.getDownloader("https://example.com/file.txt");
        assertNotNull(downloader);
        assertTrue(downloader instanceof HttpFileDownloader);
    }

    @Test
    void testGetDownloader_ScpUrl() {
        // 测试 SCP URL
        RemoteFileDownloader downloader = DownloaderFactory.getDownloader("scp://user:pass@host:22/path/file.txt");
        assertNotNull(downloader);
        assertTrue(downloader instanceof ScpFileDownloader);
    }

    @Test
    void testGetDownloader_ScpUrlWithDefaultPort() {
        // 测试 SCP URL（默认端口）
        RemoteFileDownloader downloader = DownloaderFactory.getDownloader("scp://user:pass@host/path/file.txt");
        assertNotNull(downloader);
        assertTrue(downloader instanceof ScpFileDownloader);
    }

    @Test
    void testGetDownloader_NullUrl() {
        // 测试 null URL
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DownloaderFactory.getDownloader(null)
        );
        assertEquals("URL 不能为空", exception.getMessage());
    }

    @Test
    void testGetDownloader_EmptyUrl() {
        // 测试空 URL
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DownloaderFactory.getDownloader("")
        );
        assertEquals("URL 不能为空", exception.getMessage());
    }

    @Test
    void testGetDownloader_WhitespaceUrl() {
        // 测试空白字符 URL
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DownloaderFactory.getDownloader("   ")
        );
        assertEquals("URL 不能为空", exception.getMessage());
    }

    @Test
    void testGetDownloader_UnsupportedProtocol() {
        // 测试不支持的协议
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DownloaderFactory.getDownloader("ftp://example.com/file.txt")
        );
        assertTrue(exception.getMessage().contains("不支持的 URL 协议"));
        assertTrue(exception.getMessage().contains("ftp://example.com/file.txt"));
        assertTrue(exception.getMessage().contains("支持的协议：http, https, scp"));
    }

    @Test
    void testGetDownloader_InvalidProtocol() {
        // 测试其他不支持的协议
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DownloaderFactory.getDownloader("file:///local/path/file.txt")
        );
        assertTrue(exception.getMessage().contains("不支持的 URL 协议"));
    }
}
