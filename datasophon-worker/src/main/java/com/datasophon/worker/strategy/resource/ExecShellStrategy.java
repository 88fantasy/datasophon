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
        Map<String, String> variables = new HashMap<>(this.variables);
        variables.put("${" + this.getService() + "." + this.getServiceRole() + "_INSTALL_HOME}", basePath);
        for (String command : commands) {
            command = PlaceholderUtils.replacePlaceholdersRecursive(command, variables, Constants.REGEX_VARIABLE);

            ExecResult result = ShellUtils.execShell(command);
            logger.info(" {} result {} ", command, result.getExecResult() ? "success" : "fail");
        }
        return ExecResult.success();
    }

}
