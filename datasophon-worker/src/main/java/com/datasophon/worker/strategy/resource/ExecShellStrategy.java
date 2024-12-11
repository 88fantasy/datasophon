package com.datasophon.worker.strategy.resource;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

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
        for (String command : commands) {
            command = PlaceholderUtils.replacePlaceholders(command, this.variables, Constants.REGEX_VARIABLE);

            ExecResult result = ShellUtils.execShell(command);
            logger.info(" {} result {} ", command, result.getExecResult() ? "success" : "fail");
        }
        return ExecResult.success();
    }

}
