/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.datasophon.api.master;

import akka.actor.UntypedActor;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.handler.service.ServiceHandler;
import com.datasophon.api.master.handler.service.ServiceStopHandler;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ExecuteServiceRoleCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceExecuteState;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * changelog:
 * 1. 2025/11/19 重构代码
 */
public class MasterServiceActor extends UntypedActor {

    private static final Logger logger = LoggerFactory.getLogger(MasterServiceActor.class);

    @Override
    public void postStop() {
        logger.info("{} service actor stopped after handle message", getSelf().path().toString());
    }

    @Override
    public void onReceive(Object message) {
        logger.info("MasterServiceActor:{} receive message type of {}", getSelf().path().toString(), message.getClass().getSimpleName());

        if (!(message instanceof ExecuteServiceRoleCommand)) {
            logger.warn("unrecognized message type : {}", message.getClass().getSimpleName());
            unhandled(message);
            return;
        }

        ExecuteServiceRoleCommand srvRoleCmd  = (ExecuteServiceRoleCommand) message;

        ClusterServiceRoleGroupConfigService roleGroupConfigService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleGroupConfigService.class);
        ClusterServiceRoleInstanceService roleInstanceService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);

        List<ServiceRoleInfo> masterRoles = srvRoleCmd.getMasterRoles();
        Collections.sort(masterRoles);
        ExecContext ctx = new ExecContext(masterRoles.size());

        for (ServiceRoleInfo serviceRoleInfo : masterRoles) {
            logger.info("{} service role size is {}", serviceRoleInfo.getName(), masterRoles.size());
            if (CancelCommandMap.exists(serviceRoleInfo.getHostCommandId())) {
                logger.warn("cmd {} {} in host {} canceled",  srvRoleCmd.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
                continue;
            }


            Integer serviceInstanceId = serviceRoleInfo.getServiceInstanceId();
            ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(),
                    serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
            boolean enableRangerPlugin = isEnableRangerPlugin(serviceRoleInfo.getClusterId(), serviceRoleInfo.getParentName());
            logger.info("{} enable ranger plugin is {}", serviceRoleInfo.getParentName(), enableRangerPlugin);

            logger.info("{} {} in host {}, begin to generate config map", srvRoleCmd.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
            boolean needReConfig = false;
            if (srvRoleCmd.getCommandType() == CommandType.INSTALL_SERVICE) {
                Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + serviceInstanceId);
                ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
                ProcessUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
            } else if (serviceRoleInstance.getNeedRestart() == NeedRestart.YES) {
                ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(serviceRoleInstance.getRoleGroupId());
                ProcessUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
                needReConfig = true;
            }
            Map<String, String> globalVariables = GlobalVariables.get(serviceRoleInfo.getClusterId());
            for (Generators generators : configFileMap.keySet()) {
                String outputDirectory = generators.getOutputDirectory();
                generators.setOutputDirectory(PlaceholderUtils.replacePlaceholders(outputDirectory, globalVariables,
                        Constants.REGEX_VARIABLE));
            }
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setEnableRangerPlugin(enableRangerPlugin);

            ExecResult execResult = null;
            logger.info("{} {} in host {}", srvRoleCmd.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            switch (srvRoleCmd.getCommandType()) {
                case INSTALL_SERVICE:
                    execResult = doInstallService(serviceRoleInfo, srvRoleCmd, ctx);
                    break;
                case START_SERVICE:
                    execResult = doStartService(serviceRoleInfo, srvRoleCmd, ctx, needReConfig);
                    break;
                case STOP_SERVICE:
                    execResult = doStopService(serviceRoleInfo, srvRoleCmd, ctx);
                    break;
                case RESTART_SERVICE:
                    execResult = doRestartService(serviceRoleInfo, srvRoleCmd, ctx, needReConfig);
                    break;
                case UPGRADE_SERVICE:
                    execResult = doUpgradeService(serviceRoleInfo, srvRoleCmd, ctx);
                    break;
                default:
                    throw new BusinessException(
                            String.format(
                                    "unknown cmd type: %s of srv %s in host %s{}",
                                    srvRoleCmd.getCommandType().getCommandName(Constants.CN),
                                    serviceRoleInfo.getName(), serviceRoleInfo.getHostname()
                                    )
                    );
            }
//            FIXME 按照旧代码的逻辑，execResult可能造成NPE。
            ProcessUtils.handleCommandResult(serviceRoleInfo.getHostCommandId(), execResult.getExecResult(), execResult.getExecOut());
        }
    }


    private boolean isEnableRangerPlugin(Integer clusterId, String serviceName) {
        Map<String, String> globalVariables = GlobalVariables.get(clusterId);
        return "true".equals(globalVariables.get("${enable" + serviceName + "Plugin}"));
    }


    /**
     * 这段代码，是从原来的一坨代码分割处理，后续再优化。后续修改成调用 doServiceAction
     *
     * @param serviceRoleInfo
     * @param executeServiceRoleCommand
     * @param ctx
     * @return
     */
    private ExecResult doInstallService(ServiceRoleInfo serviceRoleInfo, ExecuteServiceRoleCommand executeServiceRoleCommand, ExecContext ctx) {
        ExecResult execResult = null;
        try {
            logger.info("start to install {} in host {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());

            execResult = ProcessUtils.startInstallService(serviceRoleInfo);
            if (Objects.nonNull(execResult) && execResult.getExecResult()) {
                ProcessUtils.saveServiceInstallInfo(serviceRoleInfo);
                ctx.successNumInc();
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType()) && ctx.isAllSuccess()) {
                    logger.info("all master role has installed");
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.SUCCESS);
                }
                logger.info("{} install success in {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            } else {
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    logger.info("{} install failed in {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
                }
            }

        } catch (Exception e) {
            logger.info("{} install failed in {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
            logger.error(ProcessUtils.getExceptionMessage(e));
        }

        return execResult;
    }


    private ExecResult doStartService(ServiceRoleInfo serviceRoleInfo, ExecuteServiceRoleCommand executeServiceRoleCommand, ExecContext ctx, boolean needReConfig) {
        ExecResult execResult = null;
        try {
            logger.info("start  {} in host {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            execResult = ProcessUtils.startService(serviceRoleInfo, needReConfig);
            if (Objects.nonNull(execResult) && execResult.getExecResult()) {
                ctx.successNumInc();
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType()) && ctx.isAllSuccess()) {
                    logger.info("{} start success", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.SUCCESS);
                }
                // update service role state is running
                ProcessUtils.updateServiceRoleState(CommandType.START_SERVICE, serviceRoleInfo.getName(), serviceRoleInfo.getHostname(),
                        executeServiceRoleCommand.getClusterId(), ServiceRoleState.RUNNING);
            } else {
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    logger.info("{} start failed", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
                }
            }
        } catch (Exception e) {
            ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
            logger.error(ProcessUtils.getExceptionMessage(e));
        }
        return execResult;
    }


    private ExecResult doStopService(ServiceRoleInfo serviceRoleInfo, ExecuteServiceRoleCommand executeServiceRoleCommand, ExecContext ctx) {
        ExecResult execResult = null;
        try {
            logger.info("stop {} in host {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
            ServiceHandler serviceStopHandler = new ServiceStopHandler();
            execResult = serviceStopHandler.handlerRequest(serviceRoleInfo);
            if (Objects.nonNull(execResult) && execResult.getExecResult()) { // 执行成功
                ctx.successNumInc();
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType()) && ctx.isAllSuccess()) {
                    logger.info("{} stop success", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.SUCCESS);
                }
                // update service role state is stopped
                ProcessUtils.updateServiceRoleState(CommandType.STOP_SERVICE, serviceRoleInfo.getName(), serviceRoleInfo.getHostname(),
                        executeServiceRoleCommand.getClusterId(), ServiceRoleState.STOP);
            } else {
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    logger.info("{} stop failed", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
                }
            }
        } catch (Exception e) {
            ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
            logger.error(ProcessUtils.getExceptionMessage(e));
        }
        return execResult;
    }


    private ExecResult doRestartService(ServiceRoleInfo serviceRoleInfo, ExecuteServiceRoleCommand executeServiceRoleCommand, ExecContext ctx, boolean needReConfig) {
        ExecResult execResult = null;
        try {
            logger.info("restart {} in host {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());

            execResult = ProcessUtils.restartService(serviceRoleInfo, needReConfig);
            if (Objects.nonNull(execResult) && execResult.getExecResult()) {
                ctx.successNumInc();
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType()) && ctx.isAllSuccess()) {
                    logger.info("{} restart success", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.SUCCESS);
                }
                // update service role state is running
                ProcessUtils.updateServiceRoleState(CommandType.RESTART_SERVICE, serviceRoleInfo.getName(), serviceRoleInfo.getHostname(),
                        executeServiceRoleCommand.getClusterId(), ServiceRoleState.RUNNING);
            } else {
                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    logger.info("{} restart failed", serviceRoleInfo.getParentName());
                    ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
                }
            }
        } catch (Exception e) {
            ProcessUtils.tellCommandActorResult(serviceRoleInfo.getParentName(), executeServiceRoleCommand, ServiceExecuteState.ERROR);
            logger.error(ProcessUtils.getExceptionMessage(e));
        }
        return execResult;
    }

    /**
     * 升级服务
     *
     * @return
     */
    private ExecResult doUpgradeService(ServiceRoleInfo serviceRoleInfo, ExecuteServiceRoleCommand executeServiceRoleCommand, ExecContext ctx) {
        return doServiceAction(serviceRoleInfo, executeServiceRoleCommand, ctx, () -> ProcessUtils.upgradeService(serviceRoleInfo));
    }


    private ExecResult doServiceAction(ServiceRoleInfo srvInfo, ExecuteServiceRoleCommand cmd, ExecContext ctx, Callable<ExecResult> callable) {
        try {
            logger.info("{} {} in host {}", cmd.getCommandType().getCommandName(Constants.CN), srvInfo.getName(), srvInfo.getHostname());
            ExecResult execResult = callable.call();
            if (execResult.isSuccess()) {
                ctx.successNumInc();
                if (ServiceRoleType.MASTER.equals(srvInfo.getRoleType()) && ctx.isAllSuccess()) {
                    logger.info("{} {} success", srvInfo.getParentName(), cmd.getCommandType().getCommandName(Constants.CN));
                    ProcessUtils.tellCommandActorResult(srvInfo.getParentName(), cmd, ServiceExecuteState.SUCCESS);
                }
                // update service role state is running
                ProcessUtils.updateServiceRoleState(cmd.getCommandType(), srvInfo.getName(), srvInfo.getHostname(),
                        cmd.getClusterId(), ServiceRoleState.RUNNING);
            } else {
                if (ServiceRoleType.MASTER.equals(srvInfo.getRoleType())) {
                    logger.info("{} {} failed", srvInfo.getParentName(), cmd.getCommandType().getCommandName(Constants.CN));
                    ProcessUtils.tellCommandActorResult(srvInfo.getParentName(), cmd, ServiceExecuteState.ERROR);
                }
            }
            return execResult;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ProcessUtils.tellCommandActorResult(srvInfo.getParentName(), cmd, ServiceExecuteState.ERROR);

            String error = String.format("%s %s失败, 堆栈信息：%s", srvInfo.getParentName(), cmd.getCommandType().getCommandName(Constants.CN),
                    ProcessUtils.getExceptionMessage(e));
            return ExecResult.error(error);
        }
    }


    private static class ExecContext {

        private final int totalCnt;

        private int successCnt;


        public ExecContext(int totalCnt) {
            successCnt = 0;
            this.totalCnt = totalCnt;
        }

        public void successNumInc() {
            successCnt++;
        }

        public boolean isAllSuccess() {
            return totalCnt == successCnt;
        }
    }
}
