package com.datasophon.worker.strategy.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.utils.JuicefsUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * @author ruanzhiming
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InitStrategy extends ResourceStrategy {

    public static final String INIT_TYPE = "init";

    private List<String> commands;

    @Override
    public String type() {
        return INIT_TYPE;
    }

    @Override
    public ExecResult exec() {
//        String template = "juicefs format --storage s3 --bucket http://${minioHost}:${minioApiPort} --access-key ${minioAccessKey} --secret-key ${minioSecretKey} ${} ${juicefsMeta}";
        for (String command : commands) {
            command = PlaceholderUtils.replacePlaceholders(command, this.variables, Constants.REGEX_VARIABLE);

            ExecResult result = ShellUtils.execShell(command);
            logger.info(" {} result {} ", command, result.getExecResult() ? "success" : "fail");
        }
        return ExecResult.success();
    }

}
