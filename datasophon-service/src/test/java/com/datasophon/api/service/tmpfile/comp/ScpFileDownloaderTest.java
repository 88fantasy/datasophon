package com.datasophon.api.service.tmpfile.comp;

import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.download.DownloadProgressVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScpFileDownloader 单元测试
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class ScpFileDownloaderTest {

    private ScpFileDownloader downloader;
    private File tempFile;

    @BeforeEach
    void setUp() throws Exception {
        downloader = new ScpFileDownloader();
        tempFile = File.createTempFile("test_scp_", ".tmp");
        tempFile.deleteOnExit();
    }

    @Test
    void testSupports_ScpUrl() {
        assertTrue(downloader.supports("scp://user:pass@host:22/path/file.txt"));
    }

    @Test
    void testSupports_ScpUrlWithoutPort() {
        assertTrue(downloader.supports("scp://user:pass@host/path/file.txt"));
    }

    @Test
    void testSupports_HttpUrl() {
        assertFalse(downloader.supports("http://example.com/file.txt"));
    }

    @Test
    void testSupports_HttpsUrl() {
        assertFalse(downloader.supports("https://example.com/file.txt"));
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
    void testDetermineFileName_WithCustomName() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("scp://user:pass@host:22/path/to/file.txt");
        dto.setFileName("custom_scp_file.zip");

        String fileName = downloader.determineFileName(dto);
        assertEquals("custom_scp_file.zip", fileName);
    }

    @Test
    void testDetermineFileName_FromUrlPath() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("scp://user:pass@host:22/remote/path/myfile.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("myfile.txt", fileName);
    }

    @Test
    void testDetermineFileName_WithEncodedUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("scp://user:pass@host:22/path/%E6%B5%8B%E8%AF%95.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("测试.txt", fileName);
    }

    @Test
    void testDetermineFileName_NoPathInUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("scp://user:pass@host:22");

        String fileName = downloader.determineFileName(dto);
        assertTrue(fileName.startsWith("download_"));
    }

    @Test
    void testDownload_InvalidScpUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-scp-task-1");
        String url = "scp://invalid-host-that-does-not-exist-12345/path/file.txt";

        assertThrows(IOException.class, () ->
            downloader.download(url, tempFile, progress)
        );
    }

    @Test
    void testDownload_MalformedScpUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-scp-task-2");
        String url = "scp://not-a-valid-url";

        assertThrows(IOException.class, () ->
            downloader.download(url, tempFile, progress)
        );
    }

    @Test
    void testDownload_NullUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-scp-task-3");

        assertThrows(NullPointerException.class, () ->
            downloader.download(null, tempFile, progress)
        );
    }
}
