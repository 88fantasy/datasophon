/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
