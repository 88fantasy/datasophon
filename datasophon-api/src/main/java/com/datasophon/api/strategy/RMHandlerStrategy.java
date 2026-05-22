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

package com.datasophon.api.strategy;

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.ClusterYarnSchedulerService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterYarnScheduler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RMHandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {

  private static final Logger logger = LoggerFactory.getLogger(RMHandlerStrategy.class);

  private static final String ACTIVE = "active";

  @Override
  public void handler(Integer clusterId, List<String> hosts, String serviceName) {
    ProcessUtils.generateClusterVariable(clusterId, serviceName, "rm1", hosts.get(0));
    ProcessUtils.generateClusterVariable(clusterId, serviceName, "rm2", hosts.get(1));
    ProcessUtils.generateClusterVariable(clusterId, serviceName, "rmHost", String.join(",", hosts));
  }

  @Override
  public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
    ClusterYarnSchedulerService schedulerService =
        SpringTool.getApplicationContext().getBean(ClusterYarnSchedulerService.class);
    Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
    ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);
    boolean enableKerberos = false;
    Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);
    for (ServiceConfig config : list) {
      if ("yarn.resourcemanager.scheduler.class".equals(config.getName())) {
        ClusterYarnScheduler scheduler = schedulerService.getScheduler(clusterId);
        if ("org.apache.hadoop.yarn.server.resourcemanager.scheduler.fair.FairScheduler".equals(config.getValue())) {
          if ("capacity".equals(scheduler.getScheduler())) {
            scheduler.setScheduler("fair");
            schedulerService.updateById(scheduler);
          }
        } else {
          if ("fair".equals(scheduler.getScheduler())) {
            scheduler.setScheduler("capacity");
            schedulerService.updateById(scheduler);
          }
        }
      }
      if ("enableKerberos".equals(config.getName())) {
        enableKerberos = decideEnableKerberos(clusterId, enableKerberos, config, "YARN");
      }
    }
    String key = clusterInfo.getClusterFrame() + Constants.UNDERLINE + "YARN" + Constants.CONFIG;
    List<ServiceConfig> configs = ServiceConfigMap.get(key);
    ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
    if (enableKerberos) {
      addConfigWithKerberos(globalVariables, map, configs, kbConfigs);
    } else {
      removeConfigWithKerberos(list, map, configs);
    }
    list.addAll(kbConfigs);
  }

  @Override
  public void handlerServiceRoleCheck(ClusterServiceRoleInstanceEntity roleInstanceEntity,
                                      Map<String, ClusterServiceRoleInstanceEntity> map) {
    Integer clusterId = roleInstanceEntity.getClusterId();
    String commandLine;
    String yarnAclAdminUser = GlobalVariables.getValueByService(clusterId,roleInstanceEntity.getServiceName(), "yarn.admin.acl");
    String rm2 = GlobalVariables.getValueByService(clusterId, roleInstanceEntity.getServiceName(),"rm2");

//  TODO 使用 {ROOT.XXServiceName.xx}。HADOOP_HOME使用比较多
    String hadoopHome = GlobalVariables.getValue(clusterId, "HADOOP_HOME");
    String curRm = roleInstanceEntity.getHostname().equals(rm2) ? "rm2" : "rm1";

    if (StringUtils.isNotEmpty(yarnAclAdminUser)) {
      commandLine = String.format("sudo -u %s %s/bin/yarn rmadmin -getServiceState %s",
          yarnAclAdminUser, hadoopHome, curRm);
    } else {
      commandLine = String.format("%s/bin/yarn rmadmin -getServiceState %s",
          hadoopHome, curRm);
    }
    getRMState(roleInstanceEntity, commandLine);
  }



  private void getRMState(ClusterServiceRoleInstanceEntity roleInstanceEntity, String commandLine) {
    ClusterServiceRoleInstanceWebuisService webuisService =
        SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceWebuisService.class);
    try {
      WorkerCommandClient workerCommandClient =
          SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
      ExecResult execResult = workerCommandClient.executeCmdLine(roleInstanceEntity.getHostname(), commandLine);
      if (execResult.getExecResult()) {
        if (execResult.getExecOut().contains(ACTIVE)) {
          webuisService.updateWebUiToActive(roleInstanceEntity.getId());
        } else {
          webuisService.updateWebUiToStandby(roleInstanceEntity.getId());
        }
      } else {
        webuisService.updateWebUiToStandby(roleInstanceEntity.getId());
      }
    } catch (Exception e) {
      logger.info(e.getMessage());
    }
  }
}
