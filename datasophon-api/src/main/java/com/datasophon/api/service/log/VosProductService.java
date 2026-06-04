package com.datasophon.api.service.log;

import com.datasophon.api.dto.log.ServiceRoleLogQueryDTO;

/**
 * vos制品日志服务
 * @author zhanghuangbin
 */
public interface VosProductService {
    
    String getVosServiceRoleRuntimeLog(ServiceRoleLogQueryDTO dto) throws Exception;
    
}
