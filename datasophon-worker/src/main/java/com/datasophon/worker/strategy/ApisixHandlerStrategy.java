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

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
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
        String workPath = Constants.INSTALL_PATH + Constants.SLASH + command.getDecompressPackageName();
        if (command.getCommandType().equals(CommandType.INSTALL_SERVICE)) {
            OsType os = ShellUtils.getOs();
            ArchType arch = ShellUtils.getArch();

            ArrayList<String> commands = new ArrayList<>();
            sudo(command, commands);
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
                commands.add(workPath + "/" + arch.getArch() + "/" + os.getDesc() + "/*.rpm");
            }
            ExecResult execResult = ShellUtils.execShell(String.join(" ", commands));
            logger.info("install output: {}", execResult.getExecOut());

            if (!execResult.getExecResult()) {
                return execResult;
            }

//            logger.info("link config to /usr/local/apisix/conf/config.yaml");
//            commands.clear();
//            if (Objects.nonNull(command.getRunAs()) && StringUtils.isNotBlank(command.getRunAs().getUser())) {
//                commands.add("sudo");
//                commands.add("-u");
//                commands.add(command.getRunAs().getUser());
//            }
//            commands.add("/bin/hdfs");
//            commands.add("dfs");
//            commands.add("-put");
//            commands.add("./share/tez.tar.gz");
//            commands.add(tezLibParentDir);
//            execResult = ShellUtils.execWithStatus(workPath, commands, 90, logger);
//            logger.info("upload tez.tar.gz to {} output: {}", tezLibParentDir, execResult.getExecOut());
        }
        // 启动前需要先删除配置备份文件,否则会启动失败
        if (CommandType.INSTALL_SERVICE.equals(command.getCommandType())
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
                command.getDecompressPackageName(), command.getRunAs());
    }


    private void sudo(ServiceRoleOperateCommand command, List<String> commands) {
        if (Objects.nonNull(command.getRunAs()) && StringUtils.isNotBlank(command.getRunAs().getUser())) {
            commands.add("sudo");
            commands.add("-u");
            commands.add(command.getRunAs().getUser());
        }
    }
}
