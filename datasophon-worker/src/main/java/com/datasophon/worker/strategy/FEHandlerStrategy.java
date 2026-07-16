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
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ThrowableUtils;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.worker.grpc.MasterCallbackClient;
import com.datasophon.worker.handler.ServiceHandler;

import java.util.ArrayList;

import cn.hutool.core.net.NetUtil;

public class FEHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {

    public FEHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }

    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult;
        logger.info("FEHandlerStrategy start fe, command type is {}", command.getCommandType());
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getCommandType() == CommandType.INSTALL_SERVICE) {
            if (command.isSlave()) {
                logger.info("第一次启动FE, 当前角色为follower");
                ArrayList<String> commands = new ArrayList<>();
                commands.add("--helper");
                commands.add(command.getMasterHost() + ":9010");
                commands.add("--daemon");

                ServiceRoleRunner startRunner = new ServiceRoleRunner();
                startRunner.setProgram(command.getStartRunner().getProgram());
                startRunner.setArgs(commands);
                startRunner.setTimeout("600");
                startResult = serviceHandler.start(startRunner, command.getStatusRunner(),
                        command, command.getRunAs());
                if (startResult.getExecResult()) {
                    // add follower
                    try {
                        String rootPassword = command.getVariables()
                                .getOrDefault("${DORIS.root_password}", "");
                        MasterCallbackClient callbackClient = MasterCallbackClient.getInstance();
                        if (callbackClient != null) {
                            callbackClient.registerOlapNode(
                                    command.getMasterHost(), NetUtil.getLocalhostStr(),
                                    OlapNodeType.ADD_FE_FOLLOWER, rootPassword);
                        } else {
                            logger.warn("MasterCallbackClient not initialized, skipping FE follower registration");
                        }
                        logger.info("slave fe start success");
                    } catch (Exception e) {
                        logger.error("add slave fe failed {}", ThrowableUtils.getStackTrace(e));
                    }
                } else {
                    logger.error("slave fe start failed");
                }
            } else {
                logger.info("第一次启动FE, 当前角色为master");
                startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
            }
        } else {
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(), command, command.getRunAs());
        }
        return startResult;
    }

}
