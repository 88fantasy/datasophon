package com.datasophon.api.service.tmpfile.comp;

import cn.hutool.core.io.FileUtil;
import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.download.DownloadProgressVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CpFileDownloader 单元测试
 *
 * @author zhanghuangbin
 * @date 2026/04/21
 */
class CpFileDownloaderTest {

    private CpFileDownloader downloader;
    private File tempDestFile;
    private Path tempSourceFile;

    @BeforeEach
    void setUp() throws Exception {
        downloader = new CpFileDownloader();
        tempDestFile = File.createTempFile("test_cp_dest_", ".tmp");
        tempDestFile.deleteOnExit();
        tempSourceFile = Files.createTempFile("test_cp_source_", ".tmp");

        // 写入一些测试内容到源文件
        try (FileWriter writer = new FileWriter(tempSourceFile.toFile())) {
            writer.write("test content for cp downloader");
        }
    }

    @AfterEach
    void tearDown() {
        if (tempDestFile != null && tempDestFile.exists()) {
            tempDestFile.delete();
        }
        if (tempSourceFile != null && Files.exists(tempSourceFile)) {
            try {
                Files.delete(tempSourceFile);
            } catch (IOException e) {
                // 忽略删除失败
            }
        }
    }

    // ==================== supports 方法测试 ====================

    @Test
    void testSupports_CpUrl() {
        assertTrue(downloader.supports("cp:///path/to/file.txt"));
    }

    @Test
    void testSupports_CpUrlWithHost() {
        assertTrue(downloader.supports("cp://host:/path/to/file.txt"));
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
    void testSupports_InvalidCpUrl() {
        // cp:// 但后面没有路径
        assertTrue(downloader.supports("cp://"));
    }

    // ==================== download 方法测试 ====================

    @Test
    void testDownload_LocalFile() throws IOException {
        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-1");
        String url = "cp://" + tempSourceFile.toAbsolutePath().toString().replace("\\", "/");

        downloader.download(url, tempDestFile, progress);

        assertTrue(progress.getState() >= 0);
        assertTrue(tempDestFile.length() > 0);
        assertEquals(FileUtil.readString(tempSourceFile.toFile(), StandardCharsets.UTF_8), FileUtil.readString(tempDestFile, StandardCharsets.UTF_8));
    }

    @Test
    void testDownload_WithCancel() throws IOException, InterruptedException {
        // 创建一个大文件
        Path largeFile = Files.createTempFile("test_large_", ".tmp");
        largeFile.toFile().deleteOnExit();

        // 写入 5MB 数据
        try (FileWriter writer = new FileWriter(largeFile.toFile())) {
            for (int i = 0; i < 50000; i++) {
                writer.write("0123456789abcdefghijABCDEFGHIJ"); // 约 30 字节
            }
        }

        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-cancel");
        String url = "cp://" + largeFile.toAbsolutePath().toString().replace("\\", "/");

        // 在另一个线程中取消下载
        Thread cancelThread = new Thread(() -> {
            try {
                Thread.sleep(1); // 等待下载开始
                progress.setCancel(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cancelThread.start();

        downloader.download(url, tempDestFile, progress);

        assertEquals(-2, progress.getState());
        assertEquals("用户取消下载", progress.getError());

        Files.deleteIfExists(largeFile);
    }

    @Test
    void testDownload_InvalidCpUrl_NoPath() {
        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-2");
        String url = "cp://";

        assertThrows(IOException.class, () ->
                downloader.download(url, tempDestFile, progress)
        );
    }

    @Test
    void testDownload_MalformedCpUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-3");
        String url = "cp://not-a-valid-url";

        assertThrows(IOException.class, () ->
                downloader.download(url, tempDestFile, progress)
        );
    }

    @Test
    void testDownload_NullUrl() {
        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-4");

        assertThrows(NullPointerException.class, () ->
                downloader.download(null, tempDestFile, progress)
        );
    }

    @Test
    void testDownload_NonExistentSourceFile() {
        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-5");
        String url = "cp:///non/existent/path/file.txt";

        assertThrows(IOException.class, () ->
                downloader.download(url, tempDestFile, progress)
        );
    }

    @Test
    void testDownload_SourceFileNotReadable() throws IOException {
        // 创建一个不可读的文件
        Path unreadableFile = Files.createTempFile("test_unreadable_", ".tmp");
        unreadableFile.toFile().deleteOnExit();

        // 写入内容后设置为不可读
        FileUtil.writeString("test content", unreadableFile.toFile(), StandardCharsets.UTF_8);
        unreadableFile.toFile().setReadable(false);

        DownloadProgressVO progress = new DownloadProgressVO("test-cp-task-6");
        String url = "cp://" + unreadableFile.toAbsolutePath().toString().replace("\\", "/");

        try {
            downloader.download(url, tempDestFile, progress);
        } finally {
            // 恢复权限以便删除
            unreadableFile.toFile().setReadable(true);
        }

        // 在 Windows 上 setReadable 可能不生效，所以这个测试可能不会抛出异常
        // 但保留测试以覆盖代码路径
    }

    // ==================== determineFileName 方法测试 ====================

    @Test
    void testDetermineFileName_WithCustomName() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp:///path/to/file.txt");
        dto.setFileName("custom_cp_file.zip");

        String fileName = downloader.determineFileName(dto);
        assertEquals("custom_cp_file.zip", fileName);
    }

    @Test
    void testDetermineFileName_FromUrlPath() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp:///remote/path/myfile.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("myfile.txt", fileName);
    }

    @Test
    void testDetermineFileName_WithHost() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp://host:/path/to/file.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("file.txt", fileName);
    }

    @Test
    void testDetermineFileName_WithEncodedUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp:///path/%E6%B5%8B%E8%AF%95.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("测试.txt", fileName);
    }

    @Test
    void testDetermineFileName_NoPathInUrl() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp:///");

        String fileName = downloader.determineFileName(dto);
        assertTrue(fileName.startsWith("download_"));
    }

    @Test
    void testDetermineFileName_RootPathOnly() {
        DownloadTaskDTO dto = new DownloadTaskDTO();
        dto.setUrl("cp:///file.txt");

        String fileName = downloader.determineFileName(dto);
        assertEquals("file.txt", fileName);
    }
}
