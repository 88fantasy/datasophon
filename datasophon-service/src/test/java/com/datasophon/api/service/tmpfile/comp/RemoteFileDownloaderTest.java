package com.datasophon.api.service.tmpfile.comp;

import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.extrepo.DownloadProgressVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RemoteFileDownloader 接口默认方法单元测试
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class RemoteFileDownloaderTest {

    /**
     * 用于测试的简单实现
     */
    private static class TestDownloader implements RemoteFileDownloader {
        @Override
        public boolean supports(String url) {
            return true;
        }

        @Override
        public void download(String url, java.io.File destFile, DownloadProgressVO progress) {
            // 不需要实现
        }
    }

    private final RemoteFileDownloader downloader = new TestDownloader();

    @Test
    void testDetermineFileName_WithCustomFileName() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/original.txt");
        dto.setFileName("custom.txt");

        String result = downloader.determineFileName(dto);
        assertEquals("custom.txt", result);
    }

    @Test
    void testDetermineFileName_FromUrlPath() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/to/file.txt");

        String result = downloader.determineFileName(dto);
        assertEquals("file.txt", result);
    }

    @Test
    void testDetermineFileName_FromUrlPathWithQuery() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/file.txt?param=value");

        String result = downloader.determineFileName(dto);
        assertEquals("file.txt", result);
    }

    @Test
    void testDetermineFileName_NoSlashInPath() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com");

        String result = downloader.determineFileName(dto);
        assertTrue(result.startsWith("download_"));
    }

    @Test
    void testDetermineFileName_EmptyPath() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/");

        String result = downloader.determineFileName(dto);
        assertTrue(result.startsWith("download_"));
    }

    @Test
    void testDetermineFileName_WithUrlEncodedFileName() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/%E6%B5%8B%E8%AF%95%E6%96%87%E4%BB%B6.txt");

        String result = downloader.determineFileName(dto);
        assertEquals("测试文件.txt", result);
    }

    @Test
    void testDetermineFileName_WithSpacesInUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/my%20file.txt");

        String result = downloader.determineFileName(dto);
        assertEquals("my file.txt", result);
    }

    @Test
    void testDetermineFileName_HttpsUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("https://secure.example.com/downloads/archive.zip");

        String result = downloader.determineFileName(dto);
        assertEquals("archive.zip", result);
    }

    @Test
    void testDetermineFileName_ScpUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("scp://user:pass@host:22/remote/path/data.tar.gz");

        String result = downloader.determineFileName(dto);
        assertEquals("data.tar.gz", result);
    }


    @Test
    void testDetermineFileName_NullUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl(null);

        assertThrows(IllegalStateException.class, () ->
            downloader.determineFileName(dto)
        );
    }

    @Test
    void testDetermineFileName_FileNameWithMultipleDots() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/file.name.with.dots.tar.gz");

        String result = downloader.determineFileName(dto);
        assertEquals("file.name.with.dots.tar.gz", result);
    }

    @Test
    void testDetermineFileName_FileNameWithoutExtension() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("http://example.com/path/README");

        String result = downloader.determineFileName(dto);
        assertEquals("README", result);
    }
}
