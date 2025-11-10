package com.datasophon.api.job;

import com.datasophon.api.service.tmpfile.UploadTempFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Component
public class UploadTempFileJob {


    @Autowired
    private UploadTempFileService uploadTempFileService;


    /**
     * 每隔两分钟执行一次
     */
    @Scheduled(cron = "0 0/2 * * * ? ")
    public void clearCache() {
        uploadTempFileService.clearProgressCache();
    }

    /**
     * 凌晨执行，每天一次
     */
    @Scheduled(cron = "0 5 0 1/1 * ? ")
    public void removeTempFile() {
        uploadTempFileService.removeTempFile();
    }
}
