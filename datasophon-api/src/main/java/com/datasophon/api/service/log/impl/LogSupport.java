package com.datasophon.api.service.log.impl;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.io.File;
import java.util.Arrays;

/**
 * @author zhanghuangbin
 */
public class LogSupport {
    
    public static String getMasterLog(String path, int rows) {
        String realPath = Constants.MASTER_INSTALL_HOME + "/" + path;
        File file = new File(realPath);
        if (file.exists()) {
            ExecResult result = ShellUtils.exec(Constants.MASTER_INSTALL_HOME, Arrays.asList("tail", "-n", rows + "", realPath), 5);
            if (!result.isSuccess()) {
                throw new BusinessException(String.format("从%s获取日志失败, %s", path, result.getErrorTraceMessage()));
            }
            return result.getExecOut();
        }
        throw new BusinessHintException(String.format("日志文件%s不存在", path));
    }
}
