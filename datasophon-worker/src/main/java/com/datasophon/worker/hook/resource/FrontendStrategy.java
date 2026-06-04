package com.datasophon.worker.hook.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.JuicefsUtil;
import com.datasophon.worker.utils.TaskConstants;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

@Data
public class FrontendStrategy implements HookAction {
    
    public static final String FRONTEND_TYPE = "frontend";
    
    private String meta;
    
    private String source;
    
    private String target;
    
    @Override
    public String getType() {
        return FRONTEND_TYPE;
    }
    
    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        Map<String, String> variables = context.getGlobalVariables();
        String service = context.getServiceName();
        String serviceRole = context.getServiceRoleName();
        
        logger.info("开始执行资源策略:{}...", getType());
        ExecResult execResult = new ExecResult();
        if (StringUtils.isNotEmpty(meta)) {
            String metaUrl = variables.get(meta);
            if (StringUtils.isEmpty(metaUrl)) {
                logger.error("{} {} 的变量{}未找到juicefs元数据地址", service, serviceRole, meta);
                execResult.setExecErrOut("缺少juicefs元数据地址");
                return execResult;
            }
            metaUrl = PlaceholderUtils.replacePlaceholders(metaUrl, variables, Constants.REGEX_VARIABLE);
            try {
                JuicefsUtil.installFrontend(logger, metaUrl, basePath + Constants.SLASH + source, target);
                execResult.setExecResult(true);
            } catch (Throwable e) {
                logger.error("上传{}到{}失败: {}", source, target, e.getMessage(), e);
                execResult.setExecErrOut(e.getMessage());
            }
        } else {
            execResult.setExecErrOut("未配置juicefs元数据变量");
        }
        return execResult;
    }
}
