package com.datasophon.worker.strategy.resource;

import cn.hutool.core.io.FileUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.DownloadResult;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zhanghuangbin
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NexusResourceStrategy extends ResourceStrategy {

    private String from;

    private String to;

    @Override
    public String type() {
        return "nexus";
    }

    @Override
    public ExecResult exec() {
        String fromPath = PlaceholderUtils.replacePlaceholders(from, getVariables(), Constants.REGEX_VARIABLE);
        String toPath = PlaceholderUtils.replacePlaceholders(to, getVariables(), Constants.REGEX_VARIABLE);
        String targetPath = toPath.startsWith("/") ? toPath : PathUtils.join(basePath, toPath).toString();
        try {
            PackageStorage storage = PackageStorageUtils.getStorage();
            DownloadResult result = storage.downloadResourceToLocal(fromPath);
            FileUtil.copyFile(result.getTarget(), targetPath);
            return ExecResult.success();
        } catch (Exception e) {
            logger.error("下载nexus资源包{}失败, 原因{}", fromPath, e.getMessage(), e);
            return ExecResult.error(String.format("下载nexus资源包%s失败, 原因%s", fromPath, e.getMessage()));
        }
    }

}
