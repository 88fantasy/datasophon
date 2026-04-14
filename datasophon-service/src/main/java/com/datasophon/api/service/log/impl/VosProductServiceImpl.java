package com.datasophon.api.service.log.impl;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dto.log.ServiceRoleLogQueryDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.log.VosProductService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GetLogCommand;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * vos 制品日志服务
 * @author zhanghuangbin
 */
@Slf4j
@Service("vosProductService")
public class VosProductServiceImpl implements VosProductService {


    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private FrameServiceRoleService frameServiceRoleService;

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
        ActorSelection configActor = ActorUtils.actorSystem.actorSelection("akka.tcp://datasophon@" + dto.getHost() + ":2552/user/worker/logActor");
        Timeout timeout = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        Future<Object> logFuture = Patterns.ask(configActor, command, timeout);
        ExecResult logResult = (ExecResult) Await.result(logFuture, timeout.duration());
        if (logResult == null) {
            throw new BusinessException("获取日志结果为空");
        }
        if (logResult.getExecResult()) {
            return logResult.getExecOut();
        }
        throw new BusinessException(String.format("从%s获取%s日志失败, %s", dto.getHost(), dto.getServiceName(), logResult.getErrorTraceMessage()));
    }
}
