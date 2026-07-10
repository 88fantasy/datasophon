package com.datasophon.worker.hook.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import lombok.Data;

@Data
public class ReplaceStrategy implements HookAction {

    public static final String REPLACE_TYPE = "replace";

    private String source;

    private String regex;

    private String replacement;

    @Override
    public String getType() {
        return REPLACE_TYPE;
    }

    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);

        logger.info("开始执行资源策略:{}...", getType());
        File file = new File(basePath + Constants.SLASH + source);
        if (file.exists()) {
            List<String> lines = FileUtil.readLines(file, StandardCharsets.UTF_8)
                    .stream().map(line -> line.replaceAll(regex, replacement)).toList();
            FileUtil.writeLines(lines, file, StandardCharsets.UTF_8, false);
        }
        return ExecResult.success();
    }
}
