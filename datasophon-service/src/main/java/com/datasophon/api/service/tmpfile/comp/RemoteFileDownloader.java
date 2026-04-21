package com.datasophon.api.service.tmpfile.comp;

import cn.hutool.core.util.StrUtil;
import com.datasophon.api.dto.download.DownloadTaskDTO;
import com.datasophon.api.vo.download.DownloadProgressVO;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 远程文件下载器接口
 */
public interface RemoteFileDownloader {

    /**
     * 是否支持该 URL 协议
     */
    boolean supports(String url);

    /**
     * 下载文件
     */
    void download(String url, File destFile, DownloadProgressVO progress) throws IOException;

    /**
     * 确定文件名
     *
     * @param dto 下载任务信息
     * @return 文件名
     */
    default String determineFileName(DownloadTaskDTO dto) {
        if (StrUtil.isNotBlank(dto.getFileName())) {
            return dto.getFileName();
        }
        String url = dto.getUrl();
        try {
            String path = new URI(url).getPath();
            int lastSlash = path.lastIndexOf('/');
            String fileName;
            if (lastSlash != -1 && lastSlash < path.length() - 1) {
                fileName = path.substring(lastSlash + 1);
            } else {
                fileName = "download_" + System.currentTimeMillis();
            }

            // URL 解码
            fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
            return fileName;
        } catch (Exception e) {
            throw new IllegalStateException(String.format("从URL %s解析文件名错误，%s", dto.getUrl(), e.getMessage()), e);
        }
    }
}
