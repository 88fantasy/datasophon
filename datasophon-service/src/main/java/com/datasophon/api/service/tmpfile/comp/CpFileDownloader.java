package com.datasophon.api.service.tmpfile.comp;

import cn.hutool.core.util.StrUtil;
import com.datasophon.api.vo.download.DownloadProgressVO;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CP 文件下载器
 * 支持 URL 格式： cp:///path/to/local/file
 * 用于本地文件系统或网络文件系统 (NFS/SMB) 的文件拷贝
 */
@Slf4j
public class CpFileDownloader implements RemoteFileDownloader {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public boolean supports(String url) {
        return url != null && url.startsWith("cp://");
    }

    @Override
    public void download(String url, File destFile, DownloadProgressVO progress) throws IOException {
        try {
            CpConnectionInfo connInfo = parseCpUrl(url);

            Path sourcePath;

            // 如果有 host 且不是 localhost，尝试作为网络路径处理
            if (StrUtil.isNotBlank(connInfo.host) && !"localhost".equals(connInfo.host) && !"127.0.0.1".equals(connInfo.host)) {
                // 网络路径 (NFS/SMB 等)，直接使用路径
                sourcePath = Paths.get(connInfo.host + ":" + connInfo.remotePath);
            } else {
                // 本地路径
                sourcePath = Paths.get(connInfo.remotePath);
            }

            log.info("开始拷贝文件：{} -> {}", sourcePath, destFile.getAbsolutePath());

            // 确保目标文件父目录存在
            File parentDir = destFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 如果源文件是本地可访问的，使用 Files.copy
            if (Files.isReadable(sourcePath)) {
                long fileSize = Files.size(sourcePath);
                progress.setTotal(fileSize);
                log.info("源文件大小：{} bytes", fileSize);

                try (InputStream in = Files.newInputStream(sourcePath);
                     FileOutputStream out = new FileOutputStream(destFile)) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long totalRead = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (progress.isCancel()) {
                            progress.setState(-2);
                            progress.setError("用户取消下载");
                            return;
                        }

                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        progress.plusDownloaded(bytesRead);

                        // 每 100MB 输出进度
                        if (totalRead % (100 * 1024 * 1024) < BUFFER_SIZE) {
                            log.info("CP 已下载：{} bytes, 进度 {}%", totalRead, totalRead * 100 / fileSize);
                        }
                    }

                    log.info("CP 下载完成：{} bytes", totalRead);
                }
            } else {
                // 源文件不可读，可能是权限问题或网络路径不可达
                throw new IOException("无法读取源文件：" + sourcePath + "，请检查权限或网络挂载");
            }
        } catch (URISyntaxException e) {
            throw new IOException("无效的 CP URL 格式：" + url, e);
        }
    }


    /**
     * 解析 CP URL
     * 格式：cp:///path
     * - cp:///path/to/local/file - 本地文件
     * - cp:///path/to/file - 远程文件
     * - cp://host:/path/to/file - 远程文件
     */
    private CpConnectionInfo parseCpUrl(String url) throws IOException, URISyntaxException {
        URI uri = new URI(url);

        String host = uri.getHost();
        String path = uri.getPath();

        if (StrUtil.isBlank(path)) {
            throw new IOException("CP URL 中缺少文件路径");
        }
        return new CpConnectionInfo(host, path);
    }

    private static class CpConnectionInfo {
        String host;
        String remotePath;

        CpConnectionInfo(String host, String remotePath) {
            this.host = host;
            this.remotePath = remotePath;
        }
    }
}
