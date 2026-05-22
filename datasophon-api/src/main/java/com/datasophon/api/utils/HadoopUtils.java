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

package com.datasophon.api.utils;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HadoopUtils {
    
    public static ExecResult configQueueProp(ClusterInfoEntity clusterInfo,
                                             HashMap<Generators, List<ServiceConfig>> configFileMap,
                                             ClusterServiceRoleInstanceEntity roleInstanceEntity) throws Exception {
        ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
        serviceRoleInfo.setName("ResourceManager");
        serviceRoleInfo.setParentName("YARN");
        serviceRoleInfo.setConfigFileMap(configFileMap);
        serviceRoleInfo.setDecompressPackageName(PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN"));
        serviceRoleInfo.setHostname(roleInstanceEntity.getHostname());
        ServiceConfigureHandler configureHandler = new ServiceConfigureHandler();
        ExecResult execResult = configureHandler.handlerRequest(serviceRoleInfo);
        return execResult;
    }
    
    public static ExecResult refreshQueuePropToYarn(ClusterInfoEntity clusterInfo, String hostname) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add(Constants.INSTALL_PATH + Constants.SLASH
                + PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN") + "/bin/yarn");
        commands.add("rmadmin");
        commands.add("-refreshQueues");
        WorkerCommandClient client = SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
        return client.executeCmd(hostname, commands);
    }
}
