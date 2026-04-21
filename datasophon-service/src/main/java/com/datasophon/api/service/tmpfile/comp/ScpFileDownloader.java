package com.datasophon.api.service.tmpfile.comp;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.vo.download.DownloadProgressVO;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SCP/SFTP 文件下载器
 * 支持 URL 格式：scp://username:password@host:port/path/to/file
 */
@Slf4j
public class ScpFileDownloader implements RemoteFileDownloader {

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int BUFFER_SIZE = 8192;

    @Override
    public boolean supports(String url) {
        return url != null && url.startsWith("scp://");
    }


    @Override
    public void download(String url, File destFile, DownloadProgressVO progress) throws IOException {
        Session session = null;
        ChannelSftp channel = null;
        InputStream in = null;
        FileOutputStream out = null;

        try {
            ScpConnectionInfo connInfo = parseScpUrl(url);
            log.info("开始连接 SCP 服务器：{}@{}:{}", connInfo.username, connInfo.host, connInfo.port);

            JSch jsch = new JSch();
            session = jsch.getSession(connInfo.username, connInfo.host, connInfo.port);
            session.setPassword(connInfo.password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            session.setConfig(config);
            session.connect(CONNECT_TIMEOUT);

            log.info("SCP 连接成功，打开 SFTP 通道");
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT);

//          获取远程文件信息
            SftpATTRS attrs = channel.stat(connInfo.remotePath);
            long fileSize = attrs.getSize();
            progress.setTotal(fileSize);
            log.info("远程文件大小：{} bytes", fileSize);

            log.info("开始下载文件：{}", connInfo.remotePath);
            in = channel.get(connInfo.remotePath);

            if (in == null) {
                throw new IOException("无法获取远程文件流，文件可能不存在：" + connInfo.remotePath);
            }

            out = new FileOutputStream(destFile);

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

//                每100MB输出进度
                if (totalRead % 100 * 1024 * 1024 == 0) {
                    log.info("SCP 已经下载：{} bytes, 进度 {}%", totalRead, totalRead * 100 / fileSize);
                }
            }
            log.info("SCP 下载完成：{} bytes", totalRead);
        } catch (JSchException e) {
            log.error("SCP 连接失败", e);
            throw new IOException("SCP 连接失败：" + e.getMessage(), e);
        } catch (SftpException e) {
            log.error("SFTP 操作失败", e);
            throw new IOException("SFTP 操作失败：" + e.getMessage(), e);
        } finally {
            IoUtil.close(in);
            IoUtil.close(out);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 解析 SCP URL
     * 格式：scp://username:password@host:port/path
     */
    private ScpConnectionInfo parseScpUrl(String url) throws IOException {
        try {
            URI scpUrl = new URI(url);

            String username = scpUrl.getUserInfo();
            String host = scpUrl.getHost();
            int port = scpUrl.getPort() != -1 ? scpUrl.getPort() : 22;
            String path = scpUrl.getPath();
            if (host == null || host.isEmpty()) {
                throw new IOException("SCP URL 中缺少主机地址");
            }
            if (path == null || path.isEmpty()) {
                throw new IOException("SCP URL 中缺少远程文件路径");
            }

            if (StrUtil.isBlank(username)) {
                username = "root";
            }
            int atPos = username.indexOf(':');
            String password = "";
            if (atPos >= 0) {
                password = URLDecoder.decode(username.substring(atPos + 1), StandardCharsets.UTF_8.name());
                username = username.substring(0, atPos);
            }

            return new ScpConnectionInfo(username, password, host, port, path);
        } catch (URISyntaxException e) {
            throw new IOException("无效的 SCP URL 格式：" + url, e);
        }
    }

    private static class ScpConnectionInfo {
        String username;
        String password;
        String host;
        int port;
        String remotePath;

        ScpConnectionInfo(String username, String password, String host, int port, String remotePath) {
            this.username = username;
            this.password = password;
            this.host = host;
            this.port = port;
            this.remotePath = remotePath;
        }
    }
}
