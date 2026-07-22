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
import cn.hutool.core.util.ObjectUtil;
import lombok.Data;

@Data
public class AppendLineStrategy implements HookAction {

    public static final String APPEND_LINE_TYPE = "append_line";

    private String source;

    private Integer line;

    private String text;

    @Override
    public String getType() {
        return APPEND_LINE_TYPE;
    }

    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);

        logger.info("开始执行资源策略:{}...", getType());
        File file = new File(basePath + Constants.SLASH + source);
        if (file.exists() && ObjectUtil.isNotNull(line)) {
            List<String> lines = FileUtil.readLines(file, StandardCharsets.UTF_8);
            if (lines.size() >= line && !lines.contains(text)) {
                String lineText = lines.get(line - 1);
                if (!lineText.equals(text)) {
                    lines.add(line - 1, text);
                    FileUtil.writeLines(lines, file, StandardCharsets.UTF_8, false);
                }
            }
        }
        return ExecResult.success();
    }
}
