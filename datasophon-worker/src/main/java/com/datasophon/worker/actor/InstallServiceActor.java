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

package com.datasophon.worker.actor;

import akka.actor.UntypedActor;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.handler.InstallServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

public class InstallServiceActor extends UntypedActor {
    
    private static final Logger logger = LoggerFactory.getLogger(InstallServiceActor.class);
    
    @Override
    public void onReceive(Object msg) throws Throwable {
        if (msg instanceof InstallServiceRoleCommand) {
            InstallServiceRoleCommand command = (InstallServiceRoleCommand) msg;
            ExecResult installResult = new ExecResult();

            try {
                InstallServiceHandler serviceHandler = new InstallServiceHandler(command.getFrameCode(), command.getServiceName(), command.getServiceRoleName());

                logger.info("Start install package {}", command.getPackageName());
                if (command.getDecompressPackageName().contains("kerberos")) {
                    ArrayList<String> commands = new ArrayList<>();
                    commands.add("yum");
                    commands.add("install");
                    commands.add("-y");
                    if (ServiceRoleType.MASTER == command.getServiceRoleType()) {
                        logger.info("Start to {}", commands);
                        commands.add("krb5-server");
                        commands.add("krb5-workstation");
                        commands.add("krb5-libs");
                    } else {
                        logger.info("Start to {}", commands);
                        commands.add("krb5-workstation");
                        commands.add("krb5-libs");
                    }
                    ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, commands, 180, logger);
                    if (execResult.getExecResult()) {
                        installResult = serviceHandler.install(command);
                    }
                } else {
                    String normalPkgDir = PkgInstallPathUtils.getInstallHomeName(command);
                    String linkName = PkgInstallPathUtils.getLinkDirName(command);
                    if (linkName.equals(normalPkgDir)) {
                        throw new IllegalStateException(String.format("软件%s安装目录和软链目录名字一致，无法解压", command.getServiceName()));
                    }

                    command.setNormalPkgDir(normalPkgDir);
                    installResult = serviceHandler.install(command);
                    if(installResult.getExecResult()) {
                        // 其他服务创建软连接
                        String appHome = Constants.INSTALL_PATH + Constants.SLASH + normalPkgDir;
                        String appLinkHome = Constants.INSTALL_PATH + Constants.SLASH + linkName;
                        File linkFile = new File(appLinkHome);
                        if (linkFile.exists()) {
                            if (Files.isSymbolicLink(linkFile.toPath())) {
                                ShellUtils.execShell("unlink " + appLinkHome);
                            } else {
                                throw new IllegalStateException(String.format(" %s exist but  not a link file, it is that true?", appLinkHome));
                            }
                        }
                        installResult = ShellUtils.execShell("ln -s " + appHome + " " + appLinkHome);
                        logger.info("Create symbolic dir: {}", appLinkHome);
                    }
                }
            } catch (Exception e) {
                installResult = ExecResult.error(String.format("安装%s失败，%s", command.getServiceName(), e.getMessage()));
                logger.error("安装{}{}失败, {}", command.getServiceName(), command
                        .getServiceRoleName(), e.getMessage(), e);
            } finally {
                getSender().tell(installResult, getSelf());
                logger.info("Install {} {}", command.getPackageName(), installResult.getExecResult() ? "success" : "failed");
            }
        } else {
            unhandled(msg);
        }
    }


}
