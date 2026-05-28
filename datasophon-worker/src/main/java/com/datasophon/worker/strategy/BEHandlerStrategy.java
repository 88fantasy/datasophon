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
import com.datasophon.common.utils.ThrowableUtils;
import com.datasophon.grpc.api.OlapNodeType;
import com.datasophon.worker.grpc.MasterCallbackClient;
import com.datasophon.worker.handler.ServiceHandler;

import cn.hutool.core.net.NetUtil;

public class BEHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public BEHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult = new ExecResult();
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            logger.info("add  be to cluster");
            
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                    command, command.getRunAs(), command.isCheckStatus());
            if (startResult.getExecResult()) {
                try {
                    String rootPassword = command.getVariables()
                            .getOrDefault("${DORIS.root_password}", "");
                    MasterCallbackClient callbackClient = MasterCallbackClient.getInstance();
                    if (callbackClient != null) {
                        callbackClient.registerOlapNode(
                                command.getMasterHost(), NetUtil.getLocalhostStr(),
                                OlapNodeType.ADD_BE, rootPassword);
                    } else {
                        logger.warn("MasterCallbackClient not initialized, skipping BE registration");
                    }
                } catch (Exception e) {
                    logger.error("add backend failed {}", ThrowableUtils.getStackTrace(e));
                }
                logger.info("slave be start success");
            } else {
                logger.error("slave be start failed");
            }
        } else {
            startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                    command, command.getRunAs(), command.isCheckStatus());
        }
        return startResult;
    }
}
