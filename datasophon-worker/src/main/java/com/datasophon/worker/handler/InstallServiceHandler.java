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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ServiceLoaderUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.strategy.resource.EmptyStrategy;
import com.datasophon.worker.strategy.resource.ResourceStrategy;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class InstallServiceHandler {

    private static final String HADOOP = "hadoop";

    public static final Map<String, Class<? extends ResourceStrategy>> cache = new ConcurrentHashMap<>();

    private String frameCode;

    private String serviceName;

    private String serviceRoleName;

    private Logger logger;

    static {
        List<ResourceStrategy> strategies = ServiceLoaderUtil.loadList(ResourceStrategy.class);
        for (ResourceStrategy strategy : strategies) {
            cache.put(strategy.type(), strategy.getClass());
        }
    }

    public InstallServiceHandler(String frameCode, String serviceName, String serviceRoleName) {
        this.frameCode = frameCode;
        this.serviceName = serviceName;
        this.serviceRoleName = serviceRoleName;
        String loggerName = String.format("%s-%s-%s-%s", TaskConstants.TASK_LOG_LOGGER_NAME, frameCode, serviceName, serviceRoleName);
        logger = LoggerFactory.getLogger(loggerName);
    }

    public ExecResult install(InstallServiceRoleCommand command) {
        ExecResult execResult = new ExecResult();
        try {
            String destDir = Constants.MASTER_MANAGE_PACKAGE_PATH + Constants.SLASH;
            String packageName = command.getPackageName();
            String packagePath = destDir + packageName;
            String decompressPackageName = command.getDecompressPackageName();

            boolean installPkgChange = NexusFileUtils.isFileContentChange(packageName, packagePath);

            if (Boolean.TRUE.equals(installPkgChange)) {
                NexusFileUtils.downloadPkg(packageName, packagePath);
            }

            boolean result = decompressPkg(command, destDir, installPkgChange);
            if (result) {
                if (command.getRunAs() != null && command.getRunAs().hasOwner()) {
                    ExecResult chownResult = ShellUtils.execShell(" chown -R " + command.getRunAs().getOwner() + " " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
                    logger.info("chown {} {}", decompressPackageName, chownResult.getExecResult() ? "success" : "fail");
                }
                ExecResult chmodResult = ShellUtils.execShell(" chmod -R 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
                logger.info("chmod {} {}", decompressPackageName, chmodResult.getExecResult() ? "success" : "fail");

                if (CollUtil.isNotEmpty(command.getResourceStrategies())) {
                    for (Map<String, Object> strategy : command.getResourceStrategies()) {
                        String type = (String) strategy.get(ResourceStrategy.TYPE_KEY);
                        Class<? extends ResourceStrategy> clazz = cache.getOrDefault(type, EmptyStrategy.class);
                        ResourceStrategy rs = BeanUtil.toBean(strategy, clazz, CopyOptions.create().ignoreError());
                        rs.setLogger(logger);
                        rs.setFrameCode(frameCode);
                        rs.setService(serviceName);
                        rs.setServiceRole(serviceRoleName);
                        rs.setBasePath(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
                        rs.setVariables(command.getVariables());
                        ExecResult exec = rs.exec();
                        if (!exec.getExecResult()) {
                            return exec;
                        }
                    }
                }

                if (decompressPackageName.contains(Constants.PROMETHEUS)) {
                    String alertPath = Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH + "alert_rules";
                    ShellUtils.execShell("sed -i \"s/clusterIdValue/" + PropertyUtils.getString("clusterId") + "/g\" `grep clusterIdValue -rl " + alertPath + "`");
                }
                if (decompressPackageName.contains(HADOOP)) {
                    changeHadoopInstallPathPerm(decompressPackageName);
                }
                execResult.setExecResult(true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            execResult.setExecErrOut(e.getMessage());
        }
        return execResult;
    }

    private boolean decompressPkg(InstallServiceRoleCommand instCmd, String destDir, boolean installPkgChange) {
        String packageName = instCmd.getPackageName();
        String decompressPackageName = instCmd.getPackageName();

        boolean decompressResult = true;

        boolean fileExist = FileUtil.exist(Constants.INSTALL_PATH + Constants.SLASH + instCmd.getNormalPkgDir());
        if (!fileExist || installPkgChange) {
            String sourceFile = destDir + packageName;
            logger.info("Start to decompress {}", sourceFile);
            String suffix = FileUtil.getSuffix(sourceFile);
            boolean success = false;

//           安装软件的临时解压目录
            String serviceDecompressDir = null;
            try {
                ArrayList<String> command = new ArrayList<>();

                boolean needParentDir  =  BooleanUtil.isTrue(instCmd.getCreateDecompressDir());
                String baseTempDir =  Constants.INSTALL_PATH + Constants.SLASH + "temp";
                FileUtil.mkdir(new File(baseTempDir));

                String decompressDir = null;
                if (needParentDir) {
                    decompressDir = baseTempDir +  Constants.SLASH + decompressPackageName;
//                    检查越权，防止勿删系统文件
                    checkIfPathOutOfBox(baseTempDir, decompressDir);
                    FileUtil.mkdir(new File(decompressDir));
                    FileUtil.cleanEmpty(new File(decompressDir));
                    serviceDecompressDir = decompressDir;
                } else {
                    serviceDecompressDir =  decompressDir +  Constants.SLASH + decompressPackageName;
//                    检查越权，防止勿删系统文件
                    checkIfPathOutOfBox(baseTempDir, serviceDecompressDir);
                    FileUtil.del(new File(serviceDecompressDir));
                }

                if ("tar.gz".equals(suffix) || "tgz".equals(suffix)) {
                    command.add("tar");
                    command.add("-zxvf");
                    command.add(sourceFile);
                    command.add("-C");
                    command.add(decompressDir);
                } else if ("zip".equals(suffix)) {
                    command.add("unzip");
                    if (installPkgChange) {
                        command.add("-o");
                    }
                    command.add("-d");
                    command.add(decompressDir);
                    command.add(sourceFile);
                }

                log.info("exec decompress cmd :{}", StrUtil.join(" ", command));
                ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, command, 120, logger);
                success = execResult.getExecResult();
                if (success) {
                    String targetDir = Constants.INSTALL_PATH + Constants.SLASH + instCmd.getNormalPkgDir();
                    FileUtil.mkdir(targetDir);
//                    将临时目录，重命名为安装目录
                    FileUtil.moveContent(new File(serviceDecompressDir), new File(targetDir),true);
                }
            } finally {
                if (!success && serviceDecompressDir != null) {
                    FileUtil.del(serviceDecompressDir);
                }
            }
            return success;
        }
        return decompressResult;
    }

    /**
     * 检查是否越权，防止innerDir存在 ../../之类的路径，造成越权
     * @param baseTempDir
     * @param innerDir
     */
    private void checkIfPathOutOfBox(String baseTempDir, String innerDir) {
        if (!Paths.get(innerDir).startsWith(Paths.get(baseTempDir))) {
            throw new SecurityException(String.format("can operation dir %s out of %s", innerDir, baseTempDir));
        }
    }

    private void changeHadoopInstallPathPerm(String decompressPackageName) {
        ShellUtils.execShell(" chown -R  root:hadoop " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
        ShellUtils.execShell(" chmod 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
        ShellUtils.execShell(" chmod -R 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/etc");
        ShellUtils.execShell(" chmod 6050 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/bin/container-executor");
        ShellUtils.execShell(" chmod 400 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/etc/hadoop/container-executor.cfg");
        ShellUtils.execShell(" chown -R yarn:hadoop " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/logs/userlogs");
        ShellUtils.execShell(" chmod 775 " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + "/logs/userlogs");
        ShellUtils.execShell(" ln -s " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + " " + Constants.INSTALL_PATH + Constants.SLASH + "hadoop");
    }
}
