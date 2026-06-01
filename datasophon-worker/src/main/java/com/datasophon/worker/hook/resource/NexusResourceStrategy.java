package com.datasophon.worker.hook.resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.DownloadResult;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Data
public class NexusResourceStrategy implements HookAction {

    private String from;

    private String to;

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
        try {
            PackageStorage storage = StorageUtils.getPackageStorage();
            DownloadResult result = storage.downloadResourceToLocal(fromPath);
            FileUtil.copy(result.getTarget(), targetPath, true);
            return ExecResult.success();
        } catch (Exception e) {
            logger.error("下载nexus资源包{}失败, 原因{}", fromPath, e.getMessage(), e);
            return ExecResult.error(String.format("下载nexus资源包%s失败, 原因%s", fromPath, e.getMessage()));
        }
    }
}
