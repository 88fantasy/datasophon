package com.datasophon.worker.hook.resource;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.worker.utils.TaskConstants;

import java.util.List;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

@Data
public class ShellStrategy implements HookAction {
    
    public static final String SHELL_TYPE = "sh";
    
    private List<List<String>> commands;
    
    @Override
    public String getType() {
        return SHELL_TYPE;
    }
    
    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        
        logger.info("开始执行资源策略:{}...", getType());
        for (List<String> command : commands) {
            ExecResult result = ShellUtils.exec(basePath, command, 60L);
            if (result.isSuccess()) {
                logger.info("执行命令：{}，执行结果：success，详细信息：{} ", command, result.getErrorTraceMessage());
            } else {
                logger.error("执行命令：{}，执行结果： fail，详细信息：{} ", command, result.getErrorTraceMessage());
            }
            
            if (!result.isSuccess()) {
                return result;
            }
        }
        return ExecResult.success();
    }
}
