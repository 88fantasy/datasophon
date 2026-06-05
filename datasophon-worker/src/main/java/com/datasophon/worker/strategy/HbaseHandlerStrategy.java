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
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.KerberosUtils;

import java.util.ArrayList;

import cn.hutool.core.io.FileUtil;

public class HbaseHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public HbaseHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult;
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getEnableRangerPlugin()) {
            logger.info("start to enable  hbase plugin");
            ArrayList<String> commands = new ArrayList<>();
            commands.add("sh");
            commands.add("./enable-hbase-plugin.sh");
            String installHome = PkgInstallPathUtils.getInstallHome(command);
            if (!FileUtil.exist(installHome + "/ranger-hbase-plugin/success.id")) {
                ExecResult execResult = ShellUtils.execWithStatus(installHome + "/ranger-hbase-plugin", commands, 30L, logger);
                if (execResult.getExecResult()) {
                    logger.info("enable ranger hbase plugin success");
                    FileUtil.writeUtf8String("success", installHome + "/ranger-hbase-plugin/success.id");
                } else {
                    logger.info("enable ranger hbase plugin failed");
                    return execResult;
                }
            }
        }
        if (command.getEnableKerberos()) {
            logger.info("start to get hbase keytab file");
            String hostname = CacheUtils.getString(Constants.HOSTNAME);
            KerberosUtils.createKeytabDir();
            if (!FileUtil.exist("/etc/security/keytab/hbase.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("hbase/" + hostname, "hbase.keytab");
            }
        }
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            String hadoopHome = PropertyUtils.getString("HADOOP_HOME");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /hbase");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown hbase:hadoop /hbase");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chmod 777 /hbase");
        }
        startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
        
        return startResult;
        
    }
}
