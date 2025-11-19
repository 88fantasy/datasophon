package com.datasophon.api.job;

import com.datasophon.api.service.extrepo.ExtRepoMetaService;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Component
public class CacheClearJob {


    @Autowired
    private UploadTempFileService uploadTempFileService;

    @Autowired
    private ExtRepoMetaService extRepoMetaService;

    /**
     * 每隔两分钟执行一次
     */
    @Scheduled(cron = "0 0/2 * * * ? ")
    public void clearMergeFileProgress() {
        uploadTempFileService.clearProgressCache();
    }

    /**
     * 每隔5分钟执行一次
     */
    @Scheduled(cron = "0 0/5 * * * ? ")
    public void clearCache() {
        extRepoMetaService.clearProgressCache();
    }

    /**
     * 凌晨执行，每天一次
     */
    @Scheduled(cron = "0 5 0 1/1 * ? ")
    public void removeTempFile() {
        uploadTempFileService.removeTempFile();
    }
}
