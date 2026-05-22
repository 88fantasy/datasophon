/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.master.service;

import com.datasophon.api.master.MasterNodeProcessingActor;
import com.datasophon.common.command.OlapSqlExecCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.OlapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OLAP 节点注册 Spring Service，业务逻辑来自 {@link MasterNodeProcessingActor}。
 */
@Service
public class MasterNodeProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(MasterNodeProcessingService.class);

    @Async("masterExecutor")
    public void processOlapNode(OlapSqlExecCommand command) {
        ExecResult execResult = new ExecResult();
        String tip = command.getOpsType().getDesc();
        Map<String, String> globalVariables = command.getVariables();
        String rootPassword = globalVariables.getOrDefault("${DORIS.root_password}", "");

        switch (command.getOpsType()) {
            case ADD_BE:
                execResult = OlapUtils.addBackend(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            case ADD_FE_FOLLOWER:
                execResult = OlapUtils.addFollower(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            case ADD_FE_OBSERVER:
                execResult = OlapUtils.addObserver(command.getFeMaster(), command.getHostName(), rootPassword);
                break;
            default:
                break;
        }

        if (execResult.getExecResult()) {
            logger.info("{} {} added success", command.getHostName(), tip);
        } else {
            logger.info("{} {} added failed", command.getHostName(), tip);
        }

        int tryTimes = 0;
        while (!execResult.getExecResult() && tryTimes < 3) {
            try {
                TimeUnit.SECONDS.sleep(10L);
                switch (command.getOpsType()) {
                    case ADD_BE:
                        execResult = OlapUtils.addBackendBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                    case ADD_FE_FOLLOWER:
                        execResult = OlapUtils.addFollowerBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                    case ADD_FE_OBSERVER:
                        execResult = OlapUtils.addObserverBySqlClient(command.getFeMaster(), command.getHostName());
                        break;
                    default:
                        break;
                }
                if (execResult.getExecResult()) {
                    logger.info("{} {} added success", command.getHostName(), tip);
                    break;
                } else {
                    logger.info("{} {} added failed", command.getHostName(), tip);
                }
                tryTimes++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("The SR operate be sleep operation failed");
            }
        }
    }
}
