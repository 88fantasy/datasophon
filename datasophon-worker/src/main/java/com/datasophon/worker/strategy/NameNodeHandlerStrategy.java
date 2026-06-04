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

import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.KerberosUtils;

import java.util.ArrayList;

import cn.hutool.core.io.FileUtil;

public class NameNodeHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public NameNodeHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            if (command.isSlave()) {
                // 执行hdfs namenode -bootstrapStandby
                logger.info("Start to execute hdfs namenode -bootstrapStandby");
                ArrayList<String> commands = new ArrayList<>();
                commands.add(workPath + "/bin/hdfs");
                commands.add("namenode");
                commands.add("-bootstrapStandby");
                commands.add("-nonInteractive");
                commands.add("-force");
                ExecResult execResult = ShellUtils.execWithStatus(workPath, commands, 30L, logger);
                if (execResult.getExecResult()) {
                    logger.info("Namenode standby success");
                } else {
                    logger.error("Namenode standby failed");
                    return execResult;
                }
            } else {
                logger.info("Start to execute format namenode");
                ArrayList<String> commands = new ArrayList<>();
                commands.add(workPath + "/bin/hdfs");
                commands.add("namenode");
                commands.add("-format");
                commands.add("-nonInteractive");
                commands.add("-force");
                commands.add("smhadoop");
                // 清空namenode元数据
                FileUtil.del("/data/dfs/nn/current");
                ExecResult execResult = ShellUtils.execWithStatus(workPath, commands, 180L, logger);
                if (execResult.getExecResult()) {
                    logger.info("Namenode format success");
                } else {
                    logger.error("Namenode format failed");
                    return execResult;
                }
            }
        }
        if (command.getEnableRangerPlugin()) {
            logger.info("Start to enable ranger hdfs plugin");
            ArrayList<String> commands = new ArrayList<>();
            commands.add("sh");
            commands.add(workPath + "/ranger-hdfs-plugin/enable-hdfs-plugin.sh");
            if (!FileUtil.exist(workPath + "/ranger-hdfs-plugin/success.id")) {
                ExecResult execResult =
                        ShellUtils.execWithStatus(workPath + "/ranger-hdfs-plugin", commands, 30L, logger);
                if (execResult.getExecResult()) {
                    logger.info("Enable ranger hdfs plugin success");
                    // 写入ranger plugin集成成功标识
                    FileUtil.writeUtf8String("success", workPath + "/ranger-hdfs-plugin/success.id");
                } else {
                    logger.info("Enable ranger hdfs plugin failed");
                    return execResult;
                }
            }
        }
        if (command.getEnableKerberos()) {
            logger.info("Start to get namenode keytab file");
            String hostname = CacheUtils.getString(Constants.HOSTNAME);
            KerberosUtils.createKeytabDir();
            if (!FileUtil.exist("/etc/security/keytab/nn.service.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("nn/" + hostname, "nn.service.keytab");
            }
            if (!FileUtil.exist("/etc/security/keytab/spnego.service.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("HTTP/" + hostname, "spnego.service.keytab");
            }
        }
        ExecResult startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
        
        return startResult;
    }
    
}
