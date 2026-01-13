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

import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.strategy.ServiceRoleStrategy;
import com.datasophon.worker.strategy.ServiceRoleStrategyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class StartServiceActor extends HookTypedActor<ServiceRoleOperateCommand> {

    private static final Logger logger = LoggerFactory.getLogger(StartServiceActor.class);


    @Override
    protected void doOnReceive(ServiceRoleOperateCommand command) throws Throwable {
        ExecResult startResult = new ExecResult();
        try {
            logger.info("start to start service role {}", command.getServiceRoleName());
            startResult = invokeFunctions(
                    () -> invokeHook(command.getHooks(), HookType.PRE_START, command, command.getVariables()),
                    () -> {
                        ServiceRoleStrategy serviceRoleHandler = ServiceRoleStrategyContext.getServiceRoleHandler(command.getServiceRoleName());
                        if (Objects.nonNull(serviceRoleHandler)) {
                            return serviceRoleHandler.handler(command);
                        } else {
                            ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
                            return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command.getDecompressPackageName(), command.getRunAs());
                        }
                    },
                    () -> invokeHook(command.getHooks(), HookType.POST_START, command, command.getVariables())
            );
            logger.info("service role {} start result {}", command.getServiceRoleName(), startResult.getExecResult() ? "success" : "failed");
        } finally {
            getSender().tell(startResult, getSelf());
        }
    }
}
