package com.datasophon.api.service.tmpfile.comp;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.download.DownloadProgressVO;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP/HTTPS 文件下载器
 */
@Slf4j
public class HttpFileDownloader implements RemoteFileDownloader {

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 300000;
    private static final int BUFFER_SIZE = 8192;

    @Override
    public boolean supports(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }



    @Override
    public void download(String url, File destFile, DownloadProgressVO progress) throws IOException {
        HttpURLConnection connection = null;
        InputStream in = null;
        FileOutputStream out = null;

        try {
            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;
            if (StrUtil.isNotBlank(userInfo)) {
                String[] parts = userInfo.split(":");
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }
            // 构建不包含用户信息的 URL
            URI cleanUri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
            URL fileUrl = cleanUri.toURL();

            connection = (HttpURLConnection) fileUrl.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);

            if (username != null && password != null) {
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                log.info("使用 Basic Auth 进行认证，用户：{}", username);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP 响应码：" + responseCode);
            }

            long contentLength = connection.getContentLengthLong();
            progress.setTotal(contentLength);
            log.info("开始下载文件：{}, 大小：{} bytes", url, contentLength);

            progress.setContentType(connection.getContentType());

            in = connection.getInputStream();
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
            }

            log.info("下载完成：{} bytes", totalRead);

        } catch (URISyntaxException e) {
            throw new IOException(String.format("url %s 不是合法的 url", url));
        } finally {
            IoUtil.close(in);
            IoUtil.close(out);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
