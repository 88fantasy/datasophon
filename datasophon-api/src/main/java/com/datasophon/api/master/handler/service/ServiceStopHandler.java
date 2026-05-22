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

import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.HookType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;

import java.util.Objects;

public class ServiceStopHandler extends ServiceHandler {
    
    @Override
    public ExecResult handlerRequest(ServiceRoleInfo serviceRoleInfo) throws Exception {
        // 停止
        ServiceRoleOperateCommand cmd = new ServiceRoleOperateCommand();
        cmd.setServiceName(serviceRoleInfo.getParentName());
        cmd.setServiceRoleName(serviceRoleInfo.getName());
        cmd.setStopRunner(serviceRoleInfo.getStopRunner());
        cmd.setStatusRunner(serviceRoleInfo.getStatusRunner());
        cmd.setRunAs(serviceRoleInfo.getRunAs());
        cmd.setPackageName(serviceRoleInfo.getPackageName());
        cmd.setDecompressPackageName(serviceRoleInfo.getDecompressPackageName());
        cmd.setCreateDecompressDir(serviceRoleInfo.getCreateDecompressDir());
        cmd.setHooks(serviceRoleInfo.getMatchedHooks(HookType.PRE_STOP, HookType.POST_STOP));
        if (serviceRoleInfo.getRoleType() == ServiceRoleType.CLIENT) {
            ExecResult execResult = new ExecResult();
            execResult.setExecResult(true);
            if (Objects.nonNull(getNext())) {
                return getNext().handlerRequest(serviceRoleInfo);
            }
            return execResult;
        }
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        ExecResult execResult = adapter.stopServiceRole(serviceRoleInfo.getHostname(), cmd);
        if (Objects.nonNull(execResult) && execResult.getExecResult()) {
            if (Objects.nonNull(getNext())) {
                return getNext().handlerRequest(serviceRoleInfo);
            }
        }
        return execResult;
    }
}
