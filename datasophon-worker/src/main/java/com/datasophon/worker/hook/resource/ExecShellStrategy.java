package com.datasophon.worker.hook.resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.hook.HookAction;
import com.datasophon.worker.hook.HookContext;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ruanzhiming
 */
@Data
public class ExecShellStrategy implements HookAction {

    public static final String EXEC_SHELL_TYPE = "execShell";

    private List<String> commands;

    @Override
    public String getType() {
        return EXEC_SHELL_TYPE;
    }

    @Override
    public ExecResult invoke(HookContext context) {
        BeanUtil.fillBeanWithMap(context.getParams(), this, CopyOptions.create().ignoreError());
        Logger logger = LoggerFactory.getLogger(
                TaskConstants.createLoggerName(context.getServiceName(), context.getServiceRoleName(), getClass()));
        String basePath = PkgInstallPathUtils.getInstallHome(context);
        String service = context.getServiceName();
        String serviceRole = context.getServiceRoleName();

        logger.info("开始执行资源策略:{}...", getType());
        Map<String, String> variables = new HashMap<>(context.getGlobalVariables());
        variables.put("${" + service + "." + serviceRole + ".INSTALL_PATH}", basePath);
        variables.put("${ROOT." + service + ".INSTALL_PATH}", basePath);
        for (String command : commands) {
            command = PlaceholderUtils.replacePlaceholdersRecursive(command, variables, Constants.REGEX_VARIABLE);

            ExecResult result = ShellUtils.execShell(command);
            logger.info(" {} result {} ", command, result.getExecResult() ? "success" : "fail");
            return result;
        }
        return ExecResult.success();
    }
}
