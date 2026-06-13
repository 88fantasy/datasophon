package com.datasophon.api.service.log;

import com.datasophon.api.dto.log.ServiceRoleLogQueryDTO;

/**
 * 物理(VOS)制品日志服务
 * @author zhanghuangbin
 */
public interface PhysicalProductService {
    
    String getPhysicalServiceRoleRuntimeLog(ServiceRoleLogQueryDTO dto) throws Exception;
    
}
