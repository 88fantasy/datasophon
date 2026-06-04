package com.datasophon.api.service.log.impl;

import com.datasophon.api.service.log.MasterLogService;

import org.springframework.stereotype.Service;

/**
 * @author zhanghuangbin
 */
@Service("masterLogService")
public class MasterLogServiceImpl implements MasterLogService {
    
    @Override
    public String getMasterLog(int rows) {
        return LogSupport.getMasterLog("logs/datasophon-api.log", rows);
    }
    
}
