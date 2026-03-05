package com.datasophon.worker.strategy.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ruanzhiming
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ExecShellStrategy extends ResourceStrategy {

    public static final String EXEC_SHELL_TYPE = "execShell";

    private List<String> commands;

    @Override
    public String type() {
        return EXEC_SHELL_TYPE;
    }

    @Override
    public ExecResult exec() {
        logger.info("开始执行资源策略:{}...", type());
        Map<String, String> variables = new HashMap<>(this.variables);
        variables.put("${" + this.getService() + "." + this.getServiceRole() + ".INSTALL_PATH}", basePath);
        variables.put("${ROOT." + this.getService() + ".INSTALL_PATH}", basePath);
        for (String command : commands) {
            command = PlaceholderUtils.replacePlaceholdersRecursive(command, variables, Constants.REGEX_VARIABLE);

            ExecResult result = ShellUtils.execShell(command);
            logger.info(" {} result {} ", command, result.getExecResult() ? "success" : "fail");
            return result;
        }
        return ExecResult.success();
    }

}
