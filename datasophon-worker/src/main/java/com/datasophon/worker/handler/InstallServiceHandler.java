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
import cn.hutool.core.io.StreamProgress;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ServiceLoaderUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.FileUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.strategy.resource.EmptyStrategy;
import com.datasophon.worker.strategy.resource.ResourceStrategy;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            String destDir = Constants.INSTALL_PATH + Constants.SLASH + "DDP/packages" + Constants.SLASH;
            String packageName = command.getPackageName();
            String packagePath = destDir + packageName;
            String decompressPackageName = command.getDecompressPackageName();
            Boolean createDecompressDir = command.getCreateDecompressDir();

            boolean installPkgChange = isFileContentChange(packagePath, command.getPackageMd5());
            Boolean needDownLoad = !Objects.equals(PropertyUtils.getString(Constants.MASTER_HOST), CacheUtils.get(Constants.HOSTNAME)) && installPkgChange;

            if (Boolean.TRUE.equals(needDownLoad)) {
                downloadPkg(packageName, packagePath);
            }

            boolean result = decompressPkg(packageName, decompressPackageName, createDecompressDir, destDir, installPkgChange);
            if (result) {
                if (Objects.nonNull(command.getRunAs())) {
                    ExecResult chownResult = ShellUtils.execShell(" chown -R " + command.getRunAs().getUser() + ":" + command.getRunAs().getGroup() + " " + Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
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

    private Boolean isFileContentChange(String packagePath, String packageMd5) {
        boolean needDownLoad = true;
        logger.info("Remote package md5 is {}", packageMd5);
        if (FileUtil.exist(packagePath)) {
            // check md5
            String md5 = FileUtils.md5(new File(packagePath));

            logger.info("Local md5 is {}", md5);

            if (StringUtils.isNotBlank(md5) && packageMd5.trim().equals(md5.trim())) {
                needDownLoad = false;
            }
        }
        return needDownLoad;
    }

    private void downloadPkg(String packageName, String packagePath) {
        String masterHost = PropertyUtils.getString(Constants.MASTER_HOST);
        String masterPort = PropertyUtils.getString(Constants.MASTER_WEB_PORT);
        String downloadUrl = "http://" + masterHost + ":" + masterPort + "/ddh/service/install/downloadPackage?packageName=" + packageName;

        logger.info("download url is {}", downloadUrl);

        HttpUtil.downloadFile(downloadUrl, FileUtil.file(packagePath), new StreamProgress() {

            @Override
            public void start() {
                Console.log("start to install。。。。");
            }

            @Override
            public void progress(long progressSize, long l1) {
                Console.log("installed：{} / {} ", FileUtil.readableFileSize(progressSize), FileUtil.readableFileSize(l1));
            }

            @Override
            public void finish() {
                Console.log("install success！");
            }
        });
        logger.info("download package {} success", packageName);
    }

    private boolean decompressPkg(String packageName, String decompressPackageName, Boolean createDecompressDir, String destDir, boolean installPkgChange) {
        boolean decompressResult = true;
        boolean needParentDir = BooleanUtil.isTrue(createDecompressDir);
        // ~/ 开头的包，解压到当前目录下

        boolean fileExist = FileUtil.exist(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
        if (!fileExist || installPkgChange) {
            String sourceFile = destDir + packageName;
            logger.info("Start to decompress {}", sourceFile);
            String suffix = FileUtil.getSuffix(sourceFile);
            String prefix = packageName.substring(0, packageName.length() - suffix.length() - 1);
            boolean success = false;

            try {

                ArrayList<String> command = new ArrayList<>();
                String decompressDir = needParentDir ? Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName : Constants.INSTALL_PATH;
                if(needParentDir && !FileUtil.exist(decompressDir)){
                    FileUtil.mkdir(decompressDir);
                }
                if ("tar.gz".equals(suffix) || "tgz".equals(suffix)) {
                    command.add("tar");
                    command.add("-zxvf");
                    command.add(sourceFile);
                    command.add("-C");
                    command.add(decompressDir);

                    if (installPkgChange) {
                        command.add("--overwrite");
                    }
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
                    // 自动重命名
                    if (FileUtil.exist(Constants.INSTALL_PATH + Constants.SLASH + prefix)) {
                        FileUtil.move(new File(Constants.INSTALL_PATH + Constants.SLASH + prefix), new File(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName), false);
                    }
                }
            } finally {
                if (!success) {
                    FileUtil.del(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName);
                }
            }
            return success;
        }
        return decompressResult;
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
