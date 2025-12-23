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

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NameNodeHandlerStrategy extends ServiceHandlerAbstract implements ServiceRoleStrategy {

  private static final Logger logger = LoggerFactory.getLogger(NameNodeHandlerStrategy.class);

  private static final String ENABLE_RACK = "enableRack";

  private static final String ENABLE_KERBEROS = "enableKerberos";

  private static final String ACTIVE = "active";

  @Override
  public void handler(Integer clusterId, List<String> hosts, String serviceName) {

    Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
    ProcessUtils.generateClusterVariable(globalVariables, clusterId, serviceName, "nn1", hosts.get(0));
    ProcessUtils.generateClusterVariable(globalVariables, clusterId, serviceName, "nn2", hosts.get(1));
  }

  @Override
  public void handlerConfig(Integer clusterId, List<ServiceConfig> list, String serviceName) {
    Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
    ClusterInfoEntity clusterInfo = ProcessUtils.getClusterInfo(clusterId);

    boolean enableRack = false;
    boolean enableKerberos = false;
    Map<String, ServiceConfig> map = ProcessUtils.translateToMap(list);

    String key =
        clusterInfo.getClusterFrame() + Constants.UNDERLINE + "HDFS" + Constants.CONFIG;
    List<ServiceConfig> configs = ServiceConfigMap.get(key);

    for (ServiceConfig config : list) {
      if (ENABLE_RACK.equals(config.getName())) {
        if ((Boolean) config.getValue()) {
          enableRack = isEnableRack(enableRack, config);
        }
      }
      if (ENABLE_KERBEROS.equals(config.getName())) {
        enableKerberos =
            isEnableKerberos(
                clusterId, globalVariables, enableKerberos, config, "HDFS");
      }
    }
    List<ServiceConfig> rackConfigs = new ArrayList<>();
    if (enableRack) {
      logger.info("start to add rack config");
      addConfigWithRack(globalVariables, map, configs, rackConfigs);
    } else {
      removeConfigWithRack(list, map, configs);
    }
    list.addAll(rackConfigs);

    ArrayList<ServiceConfig> kbConfigs = new ArrayList<>();
    if (enableKerberos) {
      addConfigWithKerberos(globalVariables, map, configs, kbConfigs);
    } else {
      removeConfigWithKerberos(list, map, configs);
    }
    list.addAll(kbConfigs);
  }

  @Override
  public void handlerServiceRoleInfo(ServiceRoleInfo serviceRoleInfo, String hostname) {
    String nn2 = GlobalVariables.getValue(serviceRoleInfo.getClusterId(), "nn2");
    if (hostname.equals(nn2)) {
      logger.info("set to slave namenode");
      serviceRoleInfo.setSlave(true);
      serviceRoleInfo.setSortNum(5);
    }
  }

  @Override
  public void handlerServiceRoleCheck(
      ClusterServiceRoleInstanceEntity roleInstanceEntity,
      Map<String, ClusterServiceRoleInstanceEntity> map) {
    String nn2 = GlobalVariables.getValue(roleInstanceEntity.getClusterId(), "nn2");
    String hadoopHome = GlobalVariables.getValue(roleInstanceEntity.getClusterId(), "HADOOP_HOME");
    String commandLine = hadoopHome + "/bin/hdfs haadmin -getServiceState nn1";
    if (roleInstanceEntity.getHostname().equals(nn2)) {
      commandLine = hadoopHome + "/bin/hdfs haadmin -getServiceState nn2";
    }
    getNMState(roleInstanceEntity, commandLine);
  }

  private void getNMState(
      ClusterServiceRoleInstanceEntity roleInstanceEntity, String commandLine) {
    ClusterServiceRoleInstanceWebuisService webuisService =
        SpringTool.getApplicationContext()
            .getBean(ClusterServiceRoleInstanceWebuisService.class);
    ActorRef execCmdActor = ActorUtils.getRemoteActor(roleInstanceEntity.getHostname(), "nMStateActor");
    ExecuteCmdCommand cmdCommand = new ExecuteCmdCommand();
    cmdCommand.setCommandLine(commandLine);
    Timeout timeout = new Timeout(Duration.create(30, TimeUnit.SECONDS));
    Future<Object> execFuture = Patterns.ask(execCmdActor, cmdCommand, timeout);
    try {
      ExecResult execResult = (ExecResult) Await.result(execFuture, timeout.duration());
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
      logger.error(e.getMessage());
    }
  }
}
