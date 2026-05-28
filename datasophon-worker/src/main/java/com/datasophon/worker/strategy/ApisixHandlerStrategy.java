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
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.ServiceHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


public class ApisixHandlerStrategy extends AbstractHandlerStrategy implements ServiceRoleStrategy {

    public ApisixHandlerStrategy(String serviceName, String serviceRoleName) {
        super(serviceName, serviceRoleName);
    }

    @Override
    public ExecResult handler(ServiceRoleOperateCommand command) {
        ServiceHandler serviceHandler = new ServiceHandler(command.getServiceName(), command.getServiceRoleName());
        String workPath = PkgInstallPathUtils.getInstallHome(command);
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            OsType os = ShellUtils.getOs();
            ArchType arch = ShellUtils.getArch();

            ArrayList<String> commands = new ArrayList<>();
            sudo(command, commands);
            String rpmPath = workPath + Constants.SLASH + arch.getArch() + Constants.SLASH + os.getDesc();
            ExecResult rpmResult = ShellUtils.execShell(String.format(" ls %s", rpmPath));
            if (!rpmResult.getExecResult()) {
                logger.error(String.format("apisix安装包目录%s不存在", rpmPath));
                return rpmResult;
            }
            if (!OsType.isUnbuntu(os)) {
                logger.info("Start to yum install apisix with os={}, arch={}", os.getDesc(), arch.getArch());
                commands.add("yum");
                commands.add("remove");
                commands.add("-y");
                commands.add("apisix*");
                commands.add("&&");
                sudo(command, commands);
                commands.add("yum");
                commands.add("localinstall");
                commands.add("-y");
                commands.add(rpmPath + Constants.SLASH + "*.rpm");
            }
            ExecResult execResult = ShellUtils.execShell(String.join(" ", commands));
            logger.info("install output: {}", execResult.getExecOut());

            if (!execResult.getExecResult()) {
                return execResult;
            }
        }
        // 启动前需要先删除配置备份文件,否则会启动失败
        if (CommandType.INSTALL_SERVICE.equals(command.getCommandType())
                || CommandType.UPGRADE_SERVICE.equals(command.getCommandType())
                || CommandType.START_SERVICE.equals(command.getCommandType())
                || CommandType.START_WITH_CONFIG.equals(command.getCommandType())
                || CommandType.RESTART_SERVICE.equals(command.getCommandType())
                || CommandType.RESTART_WITH_CONFIG.equals(command.getCommandType())
        ) {
            ArrayList<String> delConfigCommands = new ArrayList<>();
            sudo(command, delConfigCommands);
            delConfigCommands.add("rm");
            delConfigCommands.add("-f");
            delConfigCommands.add("/usr/local/apisix/conf/config.yaml.bak");
            ExecResult execResult = ShellUtils.execShell(String.join(" ", delConfigCommands));
            logger.info("delete bak config file : {}", execResult.getExecOut());
        }
        return serviceHandler.start(command.getStartRunner(), command.getStatusRunner(),
                command, command.getRunAs(), command.isCheckStatus());
    }


    private void sudo(ServiceRoleOperateCommand command, List<String> commands) {
        if (Objects.nonNull(command.getRunAs()) && StringUtils.isNotBlank(command.getRunAs().getUser())) {
            commands.add("sudo");
            commands.add("-u");
            commands.add(command.getRunAs().getUser());
        }
    }
}
