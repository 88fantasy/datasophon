package com.datasophon.api.service.log.impl;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.log.ExecLogConstant;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.log.MasterLogService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;

/**
 * @author zhanghuangbin
 */
@Service("masterLogService")
public class MasterLogServiceImpl implements MasterLogService {

    @Autowired
    private ClusterK8sServiceCommandService clusterK8sServiceCommandService;

    @Override
    public String getMasterLog(int rows) {
        return getLog("logs/datasophon-api.log", rows);
    }

    @Override
    public String getK8sExecLog(String commandId, int rows) {
        ClusterK8sServiceCommandEntity cmd = clusterK8sServiceCommandService.getById(commandId);
        if (cmd == null) {
            throw new BusinessHintException("命令不存在");
        }
        String path =  String.format("logs/%s/%s%s.log", cmd.getServiceName(), ExecLogConstant.LOGGER_FILE_PREFIX, cmd.getNamespace());
        return getLog(path, rows);
    }

    private String getLog(String path, int rows) {
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
