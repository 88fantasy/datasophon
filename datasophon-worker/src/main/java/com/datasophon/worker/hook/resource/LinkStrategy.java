package com.datasophon.worker.hook.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import java.io.File;
import java.util.Map;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

@Data
public class LinkStrategy implements HookAction {
    
    public static final String LINK_TYPE = "link";
    
    private String source;
    
    private String target;
    
    @Override
    public String getType() {
        return LINK_TYPE;
    }
    
    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        Map<String, String> variables = context.getGlobalVariables();
        
        logger.info("开始执行资源策略:{}...", getType());
        if (StrUtil.isNotBlank(source)) {
            source = PlaceholderUtils.replacePlaceholders(source, variables, Constants.REGEX_VARIABLE);
        }
        if (StrUtil.isNotBlank(target)) {
            target = PlaceholderUtils.replacePlaceholders(target, variables, Constants.REGEX_VARIABLE);
        }
        
        String realTarget = basePath + Constants.SLASH + target;
        if (target.startsWith(Constants.SLASH)) {
            // 兼容绝对路径
            realTarget = target;
        }
        File sourceFile = new File(source);
        File targetFile = new File(realTarget);
        logger.info("link. sourceFile[{}] exist is {},targetFile[{}] exist is {}", source, sourceFile.exists(), realTarget, targetFile.exists());
        if (!FileUtil.exist(targetFile.getParent())) {
            FileUtil.mkdir(targetFile.getParent());
        }
        if (!targetFile.exists() && sourceFile.exists()) {
            ShellUtils.execShell("ln -s " + source + " " + realTarget);
            logger.info("Create symbolic dir: {} to {}", source, realTarget);
        }
        return ExecResult.success();
    }
}
