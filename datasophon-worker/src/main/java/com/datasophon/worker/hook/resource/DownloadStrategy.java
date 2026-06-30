package com.datasophon.worker.hook.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

@Data
public class DownloadStrategy implements HookAction {
    
    public static final String DOWNLOAD_TYPE = "download";
    
    private String from;
    
    private String to;
    
    private String md5;
    
    @Override
    public String getType() {
        return DOWNLOAD_TYPE;
    }
    
    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        String frameCode = context.getGlobalVariables().get("${__frameCode__}");
        
        logger.info("开始执行资源策略:{}...", DOWNLOAD_TYPE);
        File file = new File(basePath + Constants.SLASH + to);
        if (file.exists() && FileUtils.md5(file).equals(md5)) {
            logger.info("资源 {} 已经存在, 无需下载", to);
            return ExecResult.success();
        }
        
        logger.info("start to download resource : {}", from);
        Path target = file.toPath();
        Path tempFile = null;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
            
            ServiceMetaItem item = new ServiceMetaItem();
            item.setFramework(frameCode);
            item.setServiceName(context.getServiceName());
            item.setType(MetaStorage.PHYSICAL);
            MetaStorage metaStorage = StorageUtils.getMetaStorage();
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                metaStorage.downResource(item, from, () -> out);
            }
            
            String actualMd5 = FileUtils.md5(tempFile.toFile());
            if (md5 == null) {
                logger.warn("资源 {} 未配置 md5，跳过完整性校验", to);
            } else if (!md5.equalsIgnoreCase(actualMd5)) {
                logger.error("下载资源 {} MD5 校验失败，期望值: {}，实际值: {}", to, md5, actualMd5);
                return ExecResult.error(String.format("下载资源 %s MD5 校验失败", to));
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
            ShellUtils.execShell(String.format("chmod 755 %s", target));
            logger.info("end to download resource {} to {} ", from, to);
            return ExecResult.success();
        } catch (Exception e) {
            logger.error("下载资源 {} 失败，原因: {}", from, e.getMessage(), e);
            return ExecResult.error(String.format("下载资源 %s 失败，原因: %s", from, e.getMessage()));
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    logger.warn("清理资源下载临时文件 {} 失败", tempFile, e);
                }
            }
        }
    }
}
