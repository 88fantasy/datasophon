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

package com.datasophon.api.master.handler.service;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ServiceStatusHandler extends ServiceHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceStatusHandler.class);
    
    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        logger.info("start to check service status {} in {}", serviceRoleInfo.getName(), serviceRoleInfo.getHostname());
        // 启动
        Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName(serviceRoleInfo.getParentName());
        cmd.setPackageName(serviceRoleInfo.getPackageName());
        cmd.setServiceRoleName(serviceRoleInfo.getName());
        cmd.setStartRunner(serviceRoleInfo.getStartRunner());
        cmd.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        cmd.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        cmd.setStatusRunner(serviceRoleInfo.getStatusRunner());
        cmd.setSlave(serviceRoleInfo.isSlave());
        cmd.setCommandType(CommandType.CHECK_STATUS);
        cmd.setMasterHost(serviceRoleInfo.getMasterHost());
        cmd.setManagerHost(CacheUtils.getString(Constants.HOSTNAME));
        cmd.setVariables(GlobalVariables.getVariables(serviceRoleInfo.getClusterId()));
        
        logger.info("service master host is {}", serviceRoleInfo.getMasterHost());
        
        cmd.setEnableRangerPlugin(serviceRoleInfo.getEnableRangerPlugin());
        cmd.setRunAs(serviceRoleInfo.getRunAs());
        Boolean enableKerberos = Boolean.parseBoolean(globalVariables.get("${enable" + serviceRoleInfo.getParentName() + "Kerberos}"));
        logger.info("{} enable kerberos is {}", serviceRoleInfo.getParentName(), enableKerberos);
        cmd.setEnableKerberos(enableKerberos);
        if (serviceRoleInfo.getRoleType() == ServiceRoleType.CLIENT) {
            return invokeNext(serviceRoleInfo, ExecResult.success());
        } else {
            ActorSelection startActor = ActorUtils.actorSystem.actorSelection(
                    "akka.tcp://datasophon@" + serviceRoleInfo.getHostname() + ":2552/user/worker/serviceStatusActor");
            Timeout timeout = new Timeout(Duration.create(180, TimeUnit.SECONDS));
            Future<Object> startFuture = Patterns.ask(startActor, cmd, timeout);
            try {
                ExecResult statusResult = (ExecResult) Await.result(startFuture, timeout.duration());
                statusResult = invokeNext(serviceRoleInfo, statusResult);
                return statusResult;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return ExecResult.error(e.getMessage());
            }
        }
    }
}
