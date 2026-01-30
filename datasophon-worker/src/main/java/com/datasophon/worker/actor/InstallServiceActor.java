/*
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
 */

package com.datasophon.worker.actor;

import cn.hutool.core.util.ServiceLoaderUtil;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.handler.InstallServiceHandler;
import com.datasophon.worker.utils.TaskConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class InstallServiceActor extends HookTypedActor<InstallServiceRoleCommand> {

    private static final Logger logger = LoggerFactory.getLogger(InstallServiceActor.class);

    @Override
    protected void doOnReceive(InstallServiceRoleCommand command) throws Throwable {
        ExecResult installResult = new ExecResult();

        Logger log = LoggerFactory.getLogger(TaskConstants.createLoggerName(command.getServiceName(), command.getServiceRoleName(), this.getClass()));
        try {
            log.info("开始安装服务:{} {}", command.getServiceName(), command.getServiceRoleName());
            installResult = invokeFunctions(
                    () -> invokeHook(command.getHooks(), HookType.PRE_INSTALL, command, command.getVariables()),
                    () -> doInstall(command, log),
                    () -> invokeHook(command.getHooks(), HookType.POST_INSTALL, command, command.getVariables())
            );
        } catch (Exception e) {
            installResult = ExecResult.error(String.format("安装%s失败，%s", command.getServiceName(), e.getMessage()));
            logger.error("安装{}{}失败, {}", command.getServiceName(), command.getServiceRoleName(), e.getMessage(), e);
            log.error("安装{}{}失败, {}", command.getServiceName(), command.getServiceRoleName(), e.getMessage(), e);
        } finally {
            getSender().tell(installResult, getSelf());
            logger.info("Install {} {}, message: {}", command.getPackageName(), installResult.getExecResult() ? "success" : "failed", installResult.getExecOut());
            log.info("安装 {} {}, 信息: {}", command.getPackageName(), installResult.getExecResult() ? "成功" : "失败", installResult.getExecOut());
        }
    }


    private ExecResult doInstall(InstallServiceRoleCommand command, Logger log) {
        logger.info("Start install package {}", command.getPackageName());
        String normalPkgDir = PkgInstallPathUtils.getInstallHomeName(command);
        command.setNormalPkgDir(normalPkgDir);

        InstallServiceHandler serviceHandler = getInstallHandler(command);
        ExecResult installResult = serviceHandler.install(command);
        if (installResult.getExecResult()) {
            installResult = serviceHandler.createLink(command);
        }

        return installResult;
    }


    private InstallServiceHandler getInstallHandler(InstallServiceRoleCommand command) {
        List<InstallServiceHandler> handlers = ServiceLoaderUtil.loadList(InstallServiceHandler.class);
        handlers.sort(Comparator.comparing(InstallServiceHandler::getOrder));
        for (InstallServiceHandler handler : handlers) {
            handler.init(command);
            if (handler.match(command)) {
                return handler;
            }
        }
//        兜底安装逻辑
        InstallServiceHandler handler = new InstallServiceHandler();
        handler.init(command);
        return handler;
    }

}
