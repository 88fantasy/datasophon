package com.datasophon.cli.s3;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;

import java.io.File;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;

@Slf4j
public class MinioS3 implements S3 {

    public final String TYPE = "minio";
    
    // minio 下载目录 https://dl.min.io/server/minio/release/linux-amd64/minio
    private static final String TAR_PATH = "/data/install_datasophon/minio.tar.gz";
    
    private static final String MINIO_PATH = "/data/install_datasophon/minio";
    
    @Override
    public String type() {
        return TYPE;
    }
    
    @Override
    public void setConfig(Object config) {
        log.info(config.toString());
    }
    
    @Override
    public ExecResult install(File file, Executor executor, Host host) {
        ExecResult installResult = new ExecResult();
        ExecResult exists = executor.exists(MINIO_PATH);
        if (!exists.getExecResult()) {
            ExecResult sendResult = executor.sendFile(file.getAbsolutePath(), TAR_PATH, true);
            if (!sendResult.getExecResult()) {
                installResult.setExecErrOut("传输失败");
                return installResult;
            }
            ExecResult checkWorkerMd5Result = executor.execShell("md5sum " + TAR_PATH + " | awk '{print $1}'");
            if (checkWorkerMd5Result.getExecResult()) {
                String md5 = FileUtils.md5(file);
                if (!md5.equals(checkWorkerMd5Result.getExecOut())) {
                    installResult.setExecErrOut("文件校验失败,请重新传输");
                    return installResult;
                }
            }
            executor.execShell("tar -zxvf /data/install_datasophon/minio.tar.gz -C " + MINIO_PATH);
        }
        return installResult;
    }
    
    @Override
    public ExecResult start(Executor executor, Host host) {
        return null;
    }
    
    @Override
    public ExecResult stop(Executor executor, Host host) {
        return null;
    }
    
    @Override
    public ExecResult status(Executor executor, Host host) {
        ExecResult execResult = executor.execShell("ps -ef | grep 'minio server' | grep -v grep | awk '{print $2}'");
        if (execResult.getExecResult() && StrUtil.isNotEmpty(execResult.getExecOut())) {
            return execResult;
        }
        execResult.setExecResult(false);
        return execResult;
    }
    
    @Data
    public static class MinioConfig {
        private int api;
        private int console;
        private String user;
        private String password;
        private String volumes;
        
    }
}
