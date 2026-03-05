package com.datasophon.api.service.log.impl;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.log.MasterLogService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;

/**
 * @author zhanghuangbin
 */
@Service("masterLogService")
public class MasterLogServiceImpl implements MasterLogService {


    @Override
    public String getMasterLog(int rows) {
        String path = Constants.MASTER_INSTALL_HOME + "/logs/datasophon-api.log";
        File file = new File(path);
        if (file.exists()) {
            ExecResult result = ShellUtils.exec(Constants.MASTER_INSTALL_HOME, Arrays.asList("tail", "-n", rows + ""), 5);
            if (!result.isSuccess()) {
                throw new BusinessException(String.format("获取日志失败, %s", result.getErrorTraceMessage()));
            }
            return result.getExecOut();
        }
        throw new BusinessException(String.format("日志文件%s不存在", path));
    }
}
