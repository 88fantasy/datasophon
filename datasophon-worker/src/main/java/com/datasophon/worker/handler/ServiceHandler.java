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

package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.BaseCommand;
import com.datasophon.common.model.RunAs;
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class ServiceHandler {
    
    private String serviceName;
    
    private String serviceRoleName;
    
    private Logger logger;
    
    public ServiceHandler(String serviceName, String serviceRoleName) {
        this.serviceName = serviceName;
        this.serviceRoleName = serviceRoleName;
        String loggerName = TaskConstants.createLoggerName(serviceName, serviceRoleName, this.getClass());
        logger = LoggerFactory.getLogger(loggerName);
    }


    private String getLinkDirName() {
        BaseCommand cmd = new BaseCommand();
        cmd.setServiceName(serviceName);
        cmd.setServiceRoleName(serviceRoleName);
        return PkgInstallPathUtils.getLinkDirName(cmd);
    }

    public ExecResult start(ServiceRoleRunner startRunner, ServiceRoleRunner statusRunner, String decompressPackageName, RunAs runAs, boolean checkStatus) {
        String linkName = getLinkDirName();
        ExecResult statusResult = execRunner(statusRunner, linkName, null);
        if (statusResult.getExecResult()) {
            logger.info("{} already started", linkName);
            ExecResult execResult = new ExecResult();
            execResult.setExecResult(true);
            return execResult;
        }

        // start service
        ExecResult startResult = execRunner(startRunner, linkName, runAs);

        // check start result
        if (startResult.getExecResult() && checkStatus) {
            int times = PropertyUtils.getInt("times");
            int count = 0;
            while (count < times) {
                logger.info("check start result at times {}", count + 1);
                ExecResult result = execRunner(statusRunner, linkName, runAs);
                if (result.getExecResult()) {
                    logger.info("start success in {}", linkName);
                    break;
                } else {
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException ignored) {

                    }
                }
                count++;
            }
            if (count == times) {
                logger.info(" start {} timeout", linkName);
                startResult.setExecResult(false);
            }
        }

        return startResult;
    }

    public ExecResult start(ServiceRoleRunner startRunner, ServiceRoleRunner statusRunner, String decompressPackageName,
                            RunAs runAs) {
        return start(startRunner, statusRunner, decompressPackageName, runAs, true);
    }
    
    public ExecResult stop(ServiceRoleRunner runner, ServiceRoleRunner statusRunner, String decompressPackageName,
                           RunAs runAs) {
        String linkName = getLinkDirName();
        ExecResult statusResult = execRunner(statusRunner, linkName, runAs);
        ExecResult execResult = new ExecResult();
        if (statusResult.getExecResult()) {
            execResult = execRunner(runner, linkName, runAs);
            // 检测是否停止成功
            if (execResult.getExecResult()) {
                int times = PropertyUtils.getInt("times");
                int count = 0;
                while (count < times) {
                    logger.info("check stop result at times {}", count + 1);
                    ExecResult result = execRunner(statusRunner, linkName, runAs);
                    if (!result.getExecResult()) {
                        logger.info("stop success in {}", linkName);
                        break;
                    } else {
                        try {
                            Thread.sleep(5 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    count++;
                }
                if (count == times) {// 超时，置为失败
                    execResult.setExecResult(false);
                }
            }
        } else {// 已经是停止状态，直接返回
            logger.info("{} already stopped", linkName);
            execResult.setExecResult(true);
        }
        return execResult;
    }
    
    public ExecResult reStart(ServiceRoleRunner runner, String decompressPackageName) {
        ExecResult result = execRunner(runner, decompressPackageName, null);
        return result;
    }
    
    public ExecResult status(ServiceRoleRunner runner, String decompressPackageName) {
        ExecResult result = execRunner(runner, decompressPackageName, null);
        return result;
    }
    
    public ExecResult execRunner(ServiceRoleRunner runner, String installHome, RunAs runAs) {
        String shell = runner.getProgram();
        List<String> args = runner.getArgs();
        long timeout = Long.parseLong(runner.getTimeout());
        ArrayList<String> command = new ArrayList<>();
        if (Objects.nonNull(runAs) && StringUtils.isNotBlank(runAs.getUser())) {
            command.add("sudo");
            command.add("-u");
            command.add(runAs.getUser());
        }
        if (runner.getProgram().contains(Constants.TASK_MANAGER)
                || runner.getProgram().contains(Constants.JOB_MANAGER)) {
            logger.info("do not use sh");
        } else {
            File shellFile = new File(
                    Constants.INSTALL_PATH + Constants.SLASH + installHome + Constants.SLASH + shell);
            if (shellFile.exists()) {
                try {
                    // 读取第一行，检查采用的 shell 是哪个，bash、sh ？
                    final String firstLine = StringUtils.trimToEmpty(FileUtils.readFirstLine(shellFile));
                    if (firstLine.contains("bash")) {
                        command.add("bash");
                    } else if (firstLine.contains("sh")) {
                        command.add("sh");
                    } else {
                        command.add("sh");
                    }
                } catch (Exception e) {
                    logger.warn("read shell script file: " + shell + " error, reason: " + e.getMessage());
                    command.add("sh");
                }
            } else {
                command.add("sh");
            }
        }
        command.add(shell);
        command.addAll(args);
        logger.info("execute shell command : {}", command);
        return ShellUtils.execWithStatus(Constants.INSTALL_PATH + Constants.SLASH + installHome, command,
                timeout, logger);
    }
    
}
