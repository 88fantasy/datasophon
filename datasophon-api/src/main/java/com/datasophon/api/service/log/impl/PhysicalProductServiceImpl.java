package com.datasophon.api.service.log.impl;

import com.datasophon.api.dto.log.ServiceRoleLogQueryDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.log.PhysicalProductService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GetLogCommand;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.RoleType;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.util.StrUtil;

/**
 * vos 制品日志服务
 * @author zhanghuangbin
 */
@Slf4j
@Service("physicalProductService")
public class PhysicalProductServiceImpl implements PhysicalProductService {
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private FrameServiceRoleService frameServiceRoleService;
    
    @Autowired
    private WorkerCommandClient workerCommandClient;
    
    @Override
    public String getVosServiceRoleRuntimeLog(ServiceRoleLogQueryDTO dto) throws Exception {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        FrameServiceRoleEntity serviceRole = frameServiceRoleService.getServiceRoleByFrameCodeAndServiceRoleName(clusterInfo.getClusterFrame(), dto.getServiceRoleName());
        
        if (serviceRole.getServiceRoleType() == RoleType.CLIENT) {
            return "client service role type does not have any log";
        }
        
        Map<String, String> globalVariables = GlobalVariables.getVariables(dto.getClusterId());
        String logFile = serviceRole.getLogFile();
        if (StringUtils.isNotBlank(logFile)) {
            logFile = PlaceholderUtils.replacePlaceholders(logFile, globalVariables, Constants.REGEX_VARIABLE);
        }
        
        ServiceRoleInfo serviceRoleInfo = JSONObject.parseObject(
                serviceRole.getServiceRoleJson(), ServiceRoleInfo.class);
        String user = serviceRoleInfo.getRunAs() != null ? serviceRoleInfo.getRunAs().getUser() : null;
        if (StrUtil.isBlank(user)) {
            user = "root";
        }
        Map<String, String> params = Collections.singletonMap("user", user);
        if (StringUtils.isNotBlank(logFile)) {
            logFile = PlaceholderUtils.replacePlaceholders(logFile, params, Constants.REGEX_VARIABLE);
            log.info("logFile is {}", logFile);
        }
        GetLogCommand command = new GetLogCommand();
        command.setLogFile(logFile);
        command.setBaseDir(PkgInstallPathUtils.getInstallUniHome(serviceRoleInfo));
        
        log.info("start to get {} log from {}", serviceRole.getServiceRoleName(), dto.getHost());
        ExecResult logResult = workerCommandClient.getLog(dto.getHost(), command.getLogFile(), command.getBaseDir());
        if (logResult == null) {
            throw new BusinessException("获取日志结果为空");
        }
        if (logResult.getExecResult()) {
            return logResult.getExecOut();
        }
        throw new BusinessException(String.format("从%s获取%s日志失败, %s", dto.getHost(), dto.getServiceName(), logResult.getErrorTraceMessage()));
    }
}
