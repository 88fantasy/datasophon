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

public class HiveServer2HandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public HiveServer2HandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ExecResult startResult = new ExecResult();
        final String workPath = PkgInstallPathUtils.getInstallHome(command);
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getEnableRangerPlugin()) {
            logger.info("start to enable hive hdfs plugin");
            ArrayList<String> commands = new ArrayList<>();
            commands.add("sh");
            commands.add("./enable-hive-plugin.sh");
            String installHome = PkgInstallPathUtils.getInstallHome(command);
            if (!FileUtil.exist(installHome + "/ranger-hive-plugin/success.id")) {
                ExecResult execResult = ShellUtils.execWithStatus(installHome + "/ranger-hive-plugin", commands, 30L, logger);
                if (execResult.getExecResult()) {
                    logger.info("enable ranger hive plugin success");
                    FileUtil.writeUtf8String("success", installHome + "/ranger-hive-plugin/success.id");
                } else {
                    logger.info("enable ranger hive plugin failed");
                    return execResult;
                }
            }
        }
        if (command.getEnableKerberos()) {
            logger.info("start to get hive keytab file");
            String hostname = CacheUtils.getString(Constants.HOSTNAME);
            KerberosUtils.createKeytabDir();
            if (!FileUtil.exist("/etc/security/keytab/hive.service.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("hive/" + hostname, "hive.service.keytab");
            }
        }
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            String hadoopHome = PropertyUtils.getString("HADOOP_HOME");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /user/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /user/hive/warehouse");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /tmp/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /tmp/hadoop-yarn/staging/history/done");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /tmp/hadoop-yarn/staging/hive");

            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hdfs:hadoop /tmp");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chmod -R 775 /tmp");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hive:hadoop /user/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hive:hadoop /user/hive/warehouse");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hive:hadoop /tmp/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hive:hadoop /tmp/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chmod -R 777 /tmp/hive");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown -R hive:hadoop /tmp/hadoop-yarn/staging/hive");
            
            // 存在 tez 则创建软连接
            final String tezHomePath = Constants.INSTALL_PATH + Constants.SLASH + "tez";
            if (FileUtil.exist(tezHomePath)) {
                ShellUtils.execShell("ln -s " + tezHomePath + "/conf/tez-site.xml " + workPath + "/conf/tez-site.xml");
            }
        }
        
        startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(),command.isCheckStatus());
        return startResult;
    }
}
