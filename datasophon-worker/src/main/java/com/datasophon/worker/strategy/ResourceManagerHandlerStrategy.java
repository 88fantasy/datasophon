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

import java.sql.SQLException;

import cn.hutool.core.io.FileUtil;

public class ResourceManagerHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {
    
    public ResourceManagerHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }
    
    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) throws SQLException, ClassNotFoundException {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        if (command.getEnableKerberos()) {
            logger.info("start to get resourcemanager keytab file");
            String hostname = CacheUtils.getString(Constants.HOSTNAME);
            KerberosUtils.createKeytabDir();
            if (!FileUtil.exist("/etc/security/keytab/rm.service.keytab")) {
                KerberosUtils.downloadKeytabFromMaster("rm/" + hostname, "rm.service.keytab");
            }
        }
        String hadoopHome = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            // create /user/yarn
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -mkdir -p /user/yarn");
            ShellUtils.execShell("sudo -u hdfs " + hadoopHome + "/bin/hdfs dfs -chown yarn:hadoop /user/yarn");
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
    }
}
