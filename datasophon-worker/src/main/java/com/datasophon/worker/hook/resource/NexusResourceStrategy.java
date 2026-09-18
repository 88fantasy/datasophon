package com.datasophon.worker.hook.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.DownloadResult;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class NexusResourceStrategy implements HookAction {

    private String from;

    private String to;

    private String md5;

    @Override
    public String getType() {
        return "nexus";
    }

    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        Map<String, String> variables = context.getGlobalVariables();

        logger.info("开始执行资源策略:{}...", getType());
        String fromPath = PlaceholderUtils.replacePlaceholders(from, variables, Constants.REGEX_VARIABLE);
        String toPath = PlaceholderUtils.replacePlaceholders(to, variables, Constants.REGEX_VARIABLE);
        String targetPath = toPath.startsWith("/") ? toPath : PathUtils.join(basePath, toPath).toString();
        File targetFile = new File(targetPath);
        if (targetFile.exists() && FileUtils.md5(targetFile).equals(md5)) {
            logger.info("资源 {} 已经存在, 无需下载", targetPath);
            return ExecResult.success();
        }
        try {
            PackageStorage storage = StorageUtils.getPackageStorage();
            DownloadResult result = storage.downloadResourceToLocal(fromPath);
            String actualMd5 = FileUtils.md5(new File(result.getTarget()));
            if (md5 == null) {
                logger.warn("资源 {} 未配置 md5，跳过完整性校验", targetPath);
            } else if (!md5.equalsIgnoreCase(actualMd5)) {
                logger.error("下载资源 {} MD5 校验失败，期望值: {}，实际值: {}", fromPath, md5, actualMd5);
                return ExecResult.error(String.format("下载资源 %s MD5 校验失败", fromPath));
            }
            FileUtil.copy(result.getTarget(), targetPath, true);
            return ExecResult.success();
        } catch (Exception e) {
            logger.error("下载nexus资源包{}失败, 原因{}", fromPath, e.getMessage(), e);
            return ExecResult.error(String.format("下载nexus资源包%s失败, 原因%s", fromPath, e.getMessage()));
        }
    }
}
