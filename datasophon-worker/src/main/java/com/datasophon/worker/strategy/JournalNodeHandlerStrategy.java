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
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import com.datasophon.worker.utils.KerberosUtils;

import java.sql.SQLException;
import java.util.ArrayList;

import cn.hutool.core.io.FileUtil;

public class JournalNodeHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public JournalNodeHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) throws SQLException, ClassNotFoundException {
        ExecResult startResult = new ExecResult();
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getEnableKerberos()) {
            logger.info("start to get journalnode keytab file");
            String hostname = CacheUtils.getString(Constants.HOSTNAME);
            KerberosUtils.createKeytabDir();
            String hadoopConfDir = PkgInstallPathUtils.getInstallHome(command) + "/etc/hadoop/";
            if (!FileUtil.exist(hadoopConfDir + "ssl-server.xml")) {
                ShellUtils.execShell(
                        "cp " + hadoopConfDir + "ssl-server.xml.template " + hadoopConfDir + "ssl-server.xml");
            }
            if (!FileUtil.exist(hadoopConfDir + "ssl-client.xml")) {
                ShellUtils.execShell(
                        "cp " + hadoopConfDir + "ssl-client.xml.template " + hadoopConfDir + "ssl-client.xml");
            }
            if (!FileUtil.exist("/etc/security/keytab/jn.service.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("jn/" + hostname, "jn.service.keytab");
            }
            if (!FileUtil.exist("/etc/security/keytab/keystore")) {
                ArrayList<String> commands = new ArrayList<>();
                commands.add("sh");
                commands.add("keystore.sh");
                commands.add(hostname);
                ExecResult execResult = ShellUtils.execWithStatus(Constants.WORKER_SCRIPT_PATH, commands, 30L, logger);
                if (!execResult.getExecResult()) {
                    logger.info("generate keystore file failed");
                    return execResult;
                }
            }
        }
        startResult = serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
        return startResult;
    }
}
