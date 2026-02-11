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
import cn.hutool.crypto.digest.MD5;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.Constants;
import com.datasophon.common.command.InstallServiceRoleCommand;
import com.datasophon.common.storage.DownloadResult;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.common.utils.ZipUtils;
import com.datasophon.worker.strategy.resource.EmptyStrategy;
import com.datasophon.worker.strategy.resource.ResourceStrategy;
import com.datasophon.worker.utils.TaskConstants;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        logger = LoggerFactory.getLogger(TaskConstants.createLoggerName(serviceName, serviceRoleName, InstallServiceHandler.class));
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
            logger.info("开始下载安装包{}....", command.getPackageName());
            DownloadResult downloadResult = PackageStorageUtils.getStorage().downloadPackageToLocal(command.getPackageName());

            boolean goon = true;
            boolean unpackPkg = needDecompressPkg(command, downloadResult);
            if (unpackPkg) {
                logger.info("开始解压安装包{}->{}", command.getPackageName(), command.getNormalPkgDir());
                goon = decompressPkg(command, downloadResult);
                if (!goon) {
                    execResult.setExecOut("解压安装包失败，请查看日志");
                }
            } else {
                logger.info("安装包{}没有变更，无需解压", command.getPackageName());
            }
            if (goon) {
                String normalPkgDir = PkgInstallPathUtils.getInstallHomeName(command);
                if (command.getRunAs() != null && command.getRunAs().hasOwner()) {
                    ExecResult chownResult = ShellUtils.execShell(" chown -R " + command.getRunAs().getOwner() + " " + Constants.INSTALL_PATH + Constants.SLASH + normalPkgDir);
                    if (chownResult.isSuccess()) {
                        logger.info("chown {} success", normalPkgDir);
                    } else {
                        logger.warn("chown {} fail", normalPkgDir);
                    }
                }
                ExecResult chmodResult = ShellUtils.execShell(" chmod -R 775 " + Constants.INSTALL_PATH + Constants.SLASH + normalPkgDir);
                logger.info("chmod {} {}", normalPkgDir, chmodResult.getExecResult() ? "success" : "fail");

                if (CollUtil.isNotEmpty(command.getResourceStrategies())) {
                    logger.info("开始执行资源策略，总共需要执行{}个策略", command.getResourceStrategies().size());
                    for (int i = 0; i < command.getResourceStrategies().size(); i++) {
                        Map<String, Object> strategy = command.getResourceStrategies().get(i);
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
                            logger.error("执行第{}个资源策略失败, {}", i + 1, exec.getExecOut());
                            return exec;
                        }
                    }
                }
                execResult.setExecResult(true);
            }
        } catch (Exception e) {
            logger.error("安装服务{} {}失败,  {}", command.getServiceName(), command.getServiceRoleName(), e.getMessage(), e);
            execResult.setExecOut(e.getMessage());
        }
        return execResult;
    }

    private boolean needDecompressPkg(InstallServiceRoleCommand command, DownloadResult downloadResult) {
        if (downloadResult.isChange()) {
            return true;
        }
        File installHome = new File(Constants.INSTALL_PATH + Constants.SLASH + command.getNormalPkgDir());
        if (!installHome.exists()) {
            return true;
        }
        File metaFile = getMetaFile(command);
        if (!metaFile.exists()) {
            return true;
        }
        String content = FileUtil.readString(metaFile, StandardCharsets.UTF_8);
        String md5 = getInstallMetaSign(downloadResult, command);
        return !md5.equalsIgnoreCase(content);
    }

    private File getMetaDir() {
        File dir = new File(Constants.INSTALL_PATH + Constants.SLASH + ".install_meta");
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
        }
        return dir;
    }

    protected File getMetaFile(InstallServiceRoleCommand command) {
        return new File(getMetaDir(), command.getNormalPkgDir() + ".md5");
    }

    protected String getInstallMetaSign(DownloadResult result, InstallServiceRoleCommand command) {
//        mete表示会影响解压文件内容的配置项
        PackageMeta meta = new PackageMeta(result, command);
        return MD5.create().digestHex(JSONObject.toJSONString(meta), StandardCharsets.UTF_8);
    }


    protected boolean decompressPkg(InstallServiceRoleCommand instCmd, DownloadResult downloadResult) {
        String packageName = instCmd.getPackageName();
        String decompressPackageName = instCmd.getDecompressPackageName();

        String sourceFile = downloadResult.getTarget();
        String suffix = FileUtil.getSuffix(packageName);
        boolean success;
//           安装软件的临时解压目录
        String serviceDecompressDir = null;
        try {
            ArrayList<String> command = new ArrayList<>();

            boolean needParentDir = BooleanUtil.isTrue(instCmd.getCreateDecompressDir());

            String baseTempDir = Constants.INSTALL_PATH + Constants.SLASH + "temp";
            serviceDecompressDir = baseTempDir + Constants.SLASH + decompressPackageName;
            checkIfPathOutOfBox(baseTempDir, serviceDecompressDir);

            FileUtil.del(serviceDecompressDir);
            FileUtil.mkdir(new File(serviceDecompressDir));

            boolean needTrimDirName = !needParentDir;
            ExecResult execResult = new ExecResult();
            if ("tar.gz".equals(suffix) || "tgz".equals(suffix)) {
                command.add("tar");
                command.add("-zxf");
                command.add(sourceFile);
                command.add("-C");
                command.add(serviceDecompressDir);
                if (needTrimDirName) {
                    command.add("--strip-components=1");
                }
                logger.info("exec decompress cmd :{}", StrUtil.join(" ", command));
                execResult = ShellUtils.execWithStatus(Constants.INSTALL_PATH, command, 120, logger);
            } else if ("zip".equals(suffix)) {
                try {
                    ZipUtils.unzip(sourceFile, serviceDecompressDir, needTrimDirName ? 1 : 0);
                    execResult.setExecResult(true);
                } catch (Exception e) {
                    logger.error("解压文件{}失败，{}", sourceFile, e.getMessage(), e);
                    execResult.setExecOut(String.format("解压文件%s失败，失败原因：%s，请检查ddl配置的文件解压结构是否正确", sourceFile, e.getMessage()));
                }
            } else {
                throw new UnsupportedOperationException(String.format("unsupported file type %s", suffix));
            }


            success = execResult.getExecResult();
            if (success) {
                String targetDir = Constants.INSTALL_PATH + Constants.SLASH + instCmd.getNormalPkgDir();
                FileUtil.mkdir(targetDir);
//                    将临时目录，重命名为安装目录
                FileUtil.moveContent(new File(serviceDecompressDir), new File(targetDir), true);

//                写入本次安装的信息，用于下一次检测安装包是否发生变更
                FileUtil.writeString(getInstallMetaSign(downloadResult, instCmd), getMetaFile(instCmd), StandardCharsets.UTF_8);
            }
        } finally {
//                删除临时解压目录
            if (serviceDecompressDir != null) {
                FileUtil.del(serviceDecompressDir);
            }
        }
        return success;
    }

    /**
     * 检查是否越权，防止innerDir存在 ../../之类的路径，造成越权
     *
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
        return doCreateLink(appLinkHome, appHome);
    }

    protected ExecResult doCreateLink(String linkPath, String targetPath) {
        File linkFile = new File(linkPath);
        if (linkFile.exists()) {
            if (Files.isSymbolicLink(linkFile.toPath())) {
                ShellUtils.execShell("unlink " + linkPath);
            } else {
                throw new IllegalStateException(String.format(" %s exist but  not a link file, it is that true?", linkPath));
            }
        }
        ExecResult execResult = ShellUtils.execShell("ln -s " + targetPath + " " + linkPath);
        logger.info("创建软链：{} -> {} {}", linkPath, targetPath, execResult.isSuccess() ? "成功" : "失败");
        return execResult;
    }


    protected String getLinkName(InstallServiceRoleCommand command) {
        return Constants.INSTALL_PATH + Constants.SLASH + PkgInstallPathUtils.getLinkDirName(command);
    }


    @Data
    protected static class PackageMeta {
        private String md5;
        private Object createDecompressDir;
        private Object runAs;
        private Object resourceStrategies;
        private Object hooks;

        protected PackageMeta() {
        }


        protected PackageMeta(DownloadResult result, InstallServiceRoleCommand command) {
            md5 = result.getMd5();
            createDecompressDir = command.getCreateDecompressDir();
            runAs = command.getRunAs();
            resourceStrategies = command.getResourceStrategies();
            hooks = command.getHooks();
        }

    }
}
