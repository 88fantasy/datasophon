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

package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.model.RunAs;
import com.datasophon.common.model.ServiceRoleRunner;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.utils.TaskConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ServiceHandler {
    
    private String serviceName;
    
    private String serviceRoleName;
    
    private Logger logger;
    
    public ServiceHandler(String serviceName, String serviceRoleName) {
        this.serviceName = serviceName;
        this.serviceRoleName = serviceRoleName;
        String loggerName = TaskConstants.createLoggerName(serviceName, serviceRoleName, ServiceHandler.class);
        logger = LoggerFactory.getLogger(loggerName);
    }
    
    public ExecResult start(ServiceRoleRunner startRunner, ServiceRoleRunner statusRunner, ServiceRoleResource resource,
                            RunAs runAs, boolean checkStatus) {
        logger.info("开始执行服务{} {}的启动命令", resource.getServiceName(), resource.getServiceRoleName());
        String linkName = PkgInstallPathUtils.getLinkDirName(resource);
        ExecResult statusResult = execRunner(statusRunner, linkName, null);
        if (statusResult.getExecResult()) {
            logger.info("服务{} {} 已经处于运行状态，无需执行启动命令", resource.getServiceName(), resource.getServiceRoleName());
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
                    logger.info("服务{} {}启动成功", resource.getServiceName(), resource.getServiceRoleName());
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
                logger.error("服务{} {}启动超时，请查看应用的启动日志", resource.getServiceName(), resource.getServiceRoleName());
                startResult.setExecResult(false);
            }
        }
        
        return startResult;
    }
    
    public ExecResult start(ServiceRoleRunner startRunner, ServiceRoleRunner statusRunner, ServiceRoleResource resource, RunAs runAs) {
        return start(startRunner, statusRunner, resource, runAs, true);
    }
    
    public ExecResult stop(ServiceRoleRunner runner, ServiceRoleRunner statusRunner, ServiceRoleResource resource,
                           RunAs runAs) {
        String linkName = PkgInstallPathUtils.getLinkDirName(resource);
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
                        logger.info("服务{} {}关闭成功", resource.getServiceName(), resource.getServiceRoleName());
                        break;
                    } else {
                        try {
                            Thread.sleep(5 * 1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    count++;
                }
                if (count == times) {// 超时，置为失败
                    execResult.setExecResult(false);
                }
            }
        } else {// 已经是停止状态，直接返回
            logger.info("服务{} {}已经停止，无需执行停止命令", resource.getServiceName(), resource.getServiceRoleName());
            execResult.setExecResult(true);
        }
        return execResult;
    }
    
    public ExecResult restart(ServiceRoleRunner runner, ServiceRoleResource resource) {
        ExecResult result = execRunner(runner, PkgInstallPathUtils.getLinkDirName(resource), null);
        return result;
    }
    
    public ExecResult status(ServiceRoleRunner runner, ServiceRoleResource resource) {
        ExecResult result = execRunner(runner, PkgInstallPathUtils.getLinkDirName(resource), null);
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
