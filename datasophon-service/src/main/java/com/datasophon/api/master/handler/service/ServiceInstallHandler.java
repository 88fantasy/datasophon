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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ServiceInstallHandler extends ServiceUpgradeHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstallHandler.class);

    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ClusterServiceRoleInstanceService roleInstanceService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterServiceRoleInstanceEntity serviceRole = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
        if (Objects.nonNull(serviceRole)) {
            logger.info("服务实例{} {}已经安装, 忽略安装动作", serviceRole.getServiceName(), serviceRoleInfo.getName());
            return ExecResult.success(String.format("服务实例%s %s已经安装", serviceRole.getServiceName(), serviceRoleInfo.getName()));
        }

        return super.handlerRequest(serviceRoleInfo);
    }
}
