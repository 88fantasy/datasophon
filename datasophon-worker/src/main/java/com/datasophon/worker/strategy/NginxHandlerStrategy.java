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

package com.datasophon.worker.strategy;

import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class NginxHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {

  public NginxHandlerStrategy(String serviceName, String serviceRoleName) {
    super(serviceName, serviceRoleName);
  }

  @Override
  public ExecResult handler(ServiceRoleOperateCommand command) {
    ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
    String workPath = PkgInstallPathUtils.getInstallHome(command);
    if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {

      ArrayList<String> commands = new ArrayList<>();
      sudo(command, commands);
      logger.info("Start to source install nginx");
      commands.add("sh");
      commands.add("bin/configure.sh");
      ExecResult execResult = ShellUtils.execWithStatus(workPath, commands, 120L, logger);
      logger.info("./configure output: {}", execResult.getExecOut());

      if (!execResult.getExecResult()) {
        return execResult;
      }

      commands.clear();
      sudo(command, commands);
      commands.add("make");
      execResult = ShellUtils.execWithStatus(workPath, commands, 120L, logger);
      logger.info("make output: {}", execResult.getExecOut());

      if (!execResult.getExecResult()) {
        return execResult;
      }

      commands.clear();
      sudo(command, commands);
      commands.add("make");
      commands.add("install");
      execResult = ShellUtils.execWithStatus(workPath, commands, 120L, logger);
      logger.info("make install output: {}", execResult.getExecOut());

      if (!execResult.getExecResult()) {
        return execResult;
      }

    }
    return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
        command, command.getRunAs());
  }


  private void sudo(ServiceRoleOperateCommand command, List<String> commands) {
    if (Objects.nonNull(command.getRunAs()) && StringUtils.isNotBlank(command.getRunAs().getUser())) {
      commands.add("sudo");
      commands.add("-u");
      commands.add(command.getRunAs().getUser());
    }
  }
}
