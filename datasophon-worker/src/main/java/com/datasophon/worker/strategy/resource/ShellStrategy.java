package com.datasophon.worker.strategy.resource;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
public class ShellStrategy extends ResourceStrategy {

    public static final String SHELL_TYPE = "sh";

    private List<List<String>> commands;

    @Override
    public String type() {
        return SHELL_TYPE;
    }

    @Override
    public ExecResult exec() {
        logger.info("开始执行资源策略:{}...", type());
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
