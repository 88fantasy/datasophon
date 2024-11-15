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

import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.model.ArchInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import cn.hutool.core.io.FileUtil;

public class ServiceInstallHandler extends ServiceHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceInstallHandler.class);
    
    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ExecResult execResult = new ExecResult();
        ClusterServiceRoleInstanceService roleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterHostService clusterHostService = SpringTool.getApplicationContext().getBean(ClusterHostService.class);
        ClusterServiceRoleInstanceEntity serviceRole = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(),
                serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
        ClusterHostDO hostEntity = clusterHostService.getClusterHostByHostname(serviceRoleInfo.getHostname());
        if (Objects.nonNull(serviceRole)) {
            execResult.setExecResult(true);
            execResult.setExecOut("already installed");
            return execResult;
        }
        InstallServiceRoleCommand installServiceRoleCommand = new InstallServiceRoleCommand();
        installServiceRoleCommand.setFrameCode(serviceRoleInfo.getFrameCode());
        installServiceRoleCommand.setServiceName(serviceRoleInfo.getParentName());
        installServiceRoleCommand.setServiceRoleName(serviceRoleInfo.getName());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        installServiceRoleCommand.setRunAs(serviceRoleInfo.getRunAs());
        installServiceRoleCommand.setServiceRoleType(serviceRoleInfo.getRoleType());
        installServiceRoleCommand.setResourceStrategies(serviceRoleInfo.getResourceStrategies());
        
        String arch = hostEntity.getCpuArchitecture();
        Map<String, ArchInfo> archInfoMap = serviceRoleInfo.getArchInfoMap();
        if (archInfoMap.containsKey(arch)) {
            String packageName = archInfoMap.get(arch).getPackageName();
            String packageFilePath = Constants.MASTER_MANAGE_PACKAGE_PATH + Constants.SLASH + packageName;
            String md5FilePath = packageFilePath + ".md5";
            logger.info("package path: {}, md5FilePath: {}", packageFilePath, md5FilePath);
            if (FileUtil.exist(packageFilePath) && FileUtil.exist(md5FilePath)) {
                installServiceRoleCommand.setPackageName(packageName);
                installServiceRoleCommand.setPackageMd5(FileUtil.readString(md5FilePath, Charset.defaultCharset()));
            } else {
                execResult.setExecOut("file or md5 not exist !");
                return execResult;
            }
        } else {
            execResult.setExecOut("arch [" + arch + "] is undefined !");
            return execResult;
        }
        
        ActorSelection actorSelection = ActorUtils.actorSystem.actorSelection(
                "akka.tcp://datasophon@" + serviceRoleInfo.getHostname() + ":2552/user/worker/installServiceActor");
        Timeout timeout = new Timeout(Duration.create(180, TimeUnit.SECONDS));
        Future<Object> future = Patterns.ask(actorSelection, installServiceRoleCommand, timeout);
        try {
            ExecResult installResult = (ExecResult) Await.result(future, timeout.duration());
            if (Objects.nonNull(installResult) && installResult.getExecResult()) {
                if (Objects.nonNull(getNext())) {
                    return getNext().handlerRequest(serviceRoleInfo);
                }
            }
            return installResult;
        } catch (Exception e) {
            return new ExecResult();
        }
    }
}
