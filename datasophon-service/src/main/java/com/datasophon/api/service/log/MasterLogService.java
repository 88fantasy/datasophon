package com.datasophon.api.service.log;

/**
 * @author zhanghuangbin
 */
public interface MasterLogService {

    String getMasterLog(int rows);


    String getK8sExecLog(String commandId, int rows);
}
