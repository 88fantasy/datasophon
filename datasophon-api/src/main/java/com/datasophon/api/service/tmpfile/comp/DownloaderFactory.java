package com.datasophon.api.service.tmpfile.comp;

import java.util.Arrays;
import java.util.List;

/**
 * 下载器工厂
 * @author zhanghuangbin
 */
public class DownloaderFactory {
    
    private static final List<RemoteFileDownloader> DOWNLOADERS = Arrays.asList(
            new HttpFileDownloader(),
            new ScpFileDownloader(),
            new CpFileDownloader());
    
    /**
     * 根据 URL 获取合适的下载器
     * @param url 文件 URL
     * @return 支持的下载器
     * @throws IllegalArgumentException 没有支持的下载器
     */
    public static RemoteFileDownloader getDownloader(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        
        for (RemoteFileDownloader downloader : DOWNLOADERS) {
            if (downloader.supports(url)) {
                return downloader;
            }
        }
        
        throw new IllegalArgumentException("不支持的 URL 协议：" + url + "，支持的协议：http, https, scp, cp");
    }
}
