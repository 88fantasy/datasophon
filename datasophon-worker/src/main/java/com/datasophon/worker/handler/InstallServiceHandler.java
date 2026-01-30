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
import com.datasophon.common.storage.DownloadResult;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.strategy.resource.EmptyStrategy;
import com.datasophon.worker.strategy.resource.ResourceStrategy;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class InstallServiceHandler {


    public static final Map<String, Class<? extends ResourceStrategy>> cache = new ConcurrentHashMap<>();

    protected String frameCode;

    protected String serviceName;

    protected String serviceRoleName;

    private Logger logger;

    static {
        List<ResourceStrategy> strategies = ServiceLoaderUtil.loadList(ResourceStrategy.class);
        for (ResourceStrategy strategy : strategies) {
            cache.put(strategy.type(), strategy.getClass());
        }
    }


    public void init(InstallServiceRoleCommand command) {
        this.frameCode = command.getFrameCode();
        this.serviceName = command.getServiceName();
        this.serviceRoleName = command.getServiceRoleName();
        logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(serviceName, serviceRoleName, this.getClass()));
    }

    public boolean match(InstallServiceRoleCommand command) {
        return true;
    }


    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    public ExecResult install(InstallServiceRoleCommand command) {
        String linkName = getLinkName(command);
        if (command.getNormalPkgDir().equals(linkName)) {
            throw new IllegalStateException(String.format("软件%s安装目录和软链目录名字一致，无法解压", command.getServiceName()));
        }

        ExecResult execResult = new ExecResult();
        try {
            DownloadResult downloadResult = PackageStorageUtils.getStorage().downloadPackageToLocal(command.getPackageName());
            boolean result = decompressPkg(command, downloadResult.getTarget(), downloadResult.isChange());
            if (result) {
                String normalPkgDir = PkgInstallPathUtils.getInstallHomeName(command);
                if (command.getRunAs() != null && command.getRunAs().hasOwner()) {
                    ExecResult chownResult = ShellUtils.execShell(" chown -R " + command.getRunAs().getOwner() + " " + Constants.INSTALL_PATH + Constants.SLASH + normalPkgDir);
                    logger.info("chown {} {}", normalPkgDir, chownResult.getExecResult() ? "success" : "fail");
                }
                ExecResult chmodResult = ShellUtils.execShell(" chmod -R 775 " + Constants.INSTALL_PATH + Constants.SLASH + normalPkgDir);
                logger.info("chmod {} {}", normalPkgDir, chmodResult.getExecResult() ? "success" : "fail");

                if (CollUtil.isNotEmpty(command.getResourceStrategies())) {
                    for (Map<String, Object> strategy : command.getResourceStrategies()) {
                        String type = (String) strategy.get(ResourceStrategy.TYPE_KEY);
                        Class<? extends ResourceStrategy> clazz = cache.getOrDefault(type, EmptyStrategy.class);
                        ResourceStrategy rs = BeanUtil.toBean(strategy, clazz, CopyOptions.create().ignoreError());
                        rs.setLogger(logger);
                        rs.setFrameCode(frameCode);
                        rs.setService(serviceName);
                        rs.setServiceRole(serviceRoleName);
                        rs.setBasePath(PkgInstallPathUtils.getInstallHome(command));
                        rs.setVariables(command.getVariables());
                        ExecResult exec = rs.exec();
                        if (!exec.getExecResult()) {
                            return exec;
                        }
                    }
                }

                execResult.setExecResult(true);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            execResult.setExecErrOut(e.getMessage());
        }
        return execResult;
    }

    protected boolean decompressPkg(InstallServiceRoleCommand instCmd, String sourceFile, boolean installPkgChange) {
        String packageName = instCmd.getPackageName();
        String decompressPackageName = instCmd.getDecompressPackageName();

        boolean decompressResult = true;

        boolean fileExist = FileUtil.exist(Constants.INSTALL_PATH + Constants.SLASH + instCmd.getNormalPkgDir());
        if (!fileExist || installPkgChange) {
            logger.info("Start to decompress {}", sourceFile);
            String suffix = FileUtil.getSuffix(packageName);
            boolean success = false;

//           安装软件的临时解压目录
            String serviceDecompressDir = null;
            try {
                ArrayList<String> command = new ArrayList<>();

                boolean needParentDir  =  BooleanUtil.isTrue(instCmd.getCreateDecompressDir());

                String baseTempDir =  Constants.INSTALL_PATH + Constants.SLASH + "temp";
                serviceDecompressDir =  baseTempDir +  Constants.SLASH + decompressPackageName;
                checkIfPathOutOfBox(baseTempDir, serviceDecompressDir);

                FileUtil.del(serviceDecompressDir);
                FileUtil.mkdir(new File(serviceDecompressDir));

                boolean needTrimDirName = !needParentDir;

                if ("tar.gz".equals(suffix) || "tgz".equals(suffix)) {
                    command.add("tar");
                    command.add("-zxf");
                    command.add(sourceFile);
                    command.add("-C");
                    command.add(serviceDecompressDir);
                    if (needTrimDirName) {
                        command.add("--strip-components=1");
                    }
                } else {
                    throw new UnsupportedOperationException(String.format("unsupported file type %s", suffix));
                }

                logger.info("exec decompress cmd :{}", StrUtil.join(" ", command));
                ExecResult execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, command, 120, logger);
                success = execResult.getExecResult();
                if (success) {
                    String targetDir = Constants.INSTALL_PATH + Constants.SLASH + instCmd.getNormalPkgDir();
                    FileUtil.mkdir(targetDir);
//                    将临时目录，重命名为安装目录
                    FileUtil.moveContent(new File(serviceDecompressDir), new File(targetDir),true);
                }
            } finally {
//                删除临时解压目录
                if (serviceDecompressDir != null) {
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
    protected void checkIfPathOutOfBox(String baseTempDir, String innerDir) {
        if (!Paths.get(innerDir).startsWith(Paths.get(baseTempDir))) {
            throw new SecurityException(String.format("can operation dir %s out of %s", innerDir, baseTempDir));
        }
    }



    public ExecResult createLink(InstallServiceRoleCommand command) {
        String appLinkHome = getLinkName(command);
        if (appLinkHome == null) {
            logger.info("服务{} {}无需创建软链...", command.getServiceName(), command.getServiceRoleName());
            return ExecResult.success();
        }

        logger.info("安装服务{} {}成功，准备创建软链...", command.getServiceName(), command.getServiceRoleName());
        String appHome = Constants.INSTALL_PATH + Constants.SLASH + command.getNormalPkgDir();
        File linkFile = new File(appLinkHome);
        if (linkFile.exists()) {
            if (Files.isSymbolicLink(linkFile.toPath())) {
                ShellUtils.execShell("unlink " + appLinkHome);
            } else {
                throw new IllegalStateException(String.format(" %s exist but  not a link file, it is that true?", appLinkHome));
            }
        }
        ExecResult installResult = ShellUtils.execShell("ln -s " + appHome + " " + appLinkHome);
        logger.info("Create symbolic dir: {}", appLinkHome);
        logger.info("创建软链：{} -> {} 成功", appHome, appLinkHome);
        return installResult;
    }


    protected String getLinkName(InstallServiceRoleCommand command) {
        return Constants.INSTALL_PATH + Constants.SLASH + PkgInstallPathUtils.getLinkDirName(command);
    }
}
