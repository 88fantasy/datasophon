package com.datasophon.cli.init;

import cn.hutool.core.io.FileUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;

import com.datasophon.common.utils.ShellUtils;
import picocli.CommandLine;

import java.io.File;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "bin_packages", description = "init bin packages")
public class InitBinPackage extends InitBase {
    
    @CommandLine.Option(names = {"-i", "--initPath"}, description = "initPath", required = true)
    private String initPath;

    @CommandLine.Option(names = {"-d", "--installDataDir"}, description = "安装数据目录", required = true)
    private String installDataDir;

    @CommandLine.Option(names = {"-pf", "initPathOverwriteForce"}, description = "initPath目录存在是否覆盖")
    boolean initPathOverwriteForce = false;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-pp", "--registryPath"}, description = "制品安装包路径", required = true)
    String registryPath;
    
    @Override
    public String name() {
        return "分发datasophon-init资源包";
    }

    @Override
    public boolean doRun(Executor executor) {
        Boolean flag = true;
        // 本地datasophon-init
        File initPathF = new File(initPath);
        if (!initPathF.exists() || !initPathF.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "local dir not found : " + initPath);
        }

        File installPathF = new File(Constants.INSTALL_PATH);
        if(!FileUtil.exist(installDataDir)) {
            ShellUtils.execShell(String.format("mkdir -p %s", installDataDir));
        }
        if(!installPathF.exists()) {
            ShellUtils.execShell(String.format("ln -s %s %s", installDataDir, Constants.INSTALL_PATH));
        }

        File installPackagePathF = new File(Constants.MASTER_MANAGE_PACKAGE_PATH);
        if(!installPackagePathF.exists()) {
            ShellUtils.execShell(String.format("mkdir -p %s/DDP", Constants.INSTALL_PATH));
            ShellUtils.execShell(String.format("ln -s %s/packages %s", initPath, Constants.MASTER_MANAGE_PACKAGE_PATH));
        }

        // 制品库基础包
        if(enableRegistry) {
            String registryRawFullDir = String.format("%s/packages/raw", registryPath);
            String initPackagesFullDir = String.format("%s/packages", initPath);
            if(!FileUtil.exist(registryRawFullDir)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "local dir not found : " + registryRawFullDir);
            }
            if(!FileUtil.exist(initPackagesFullDir)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "local dir not found : " + initPackagesFullDir);
            }
            ShellUtils.execShell(String.format("cp -rf %s/jdk-* %s", registryRawFullDir, initPackagesFullDir));
            ShellUtils.execShell(String.format("cp -rf %s/nexus-* %s", registryRawFullDir, initPackagesFullDir));
            ShellUtils.execShell(String.format("cp -rf %s/rustfs-* %s", registryRawFullDir, initPackagesFullDir));
            // 强制覆盖
            initPathOverwriteForce = true;
        }

        // 远程datasophon-init
        ExecResult remoteResult = executor.exists(initPath);
        if(remoteResult.getExecResult() && !initPathOverwriteForce){
            log.info("远程datasophon-init目录已存在,且overwrite={},跳过)", initPathOverwriteForce);
        } else {
            ExecResult createResult = executor.createDir(initPath);
            if (!createResult.getExecResult()) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "dist createDir fail : " + initPath);
            }
            log.info("分发资源包路径:{} start", initPath);
            long ts = System.currentTimeMillis();
            ExecResult execResult = executor.sendDir(initPath, initPath, true);
            log.info("分发资源包路径:{} end,耗时:{}s", initPath, (System.currentTimeMillis() - ts) / 1000.0);
            if (execResult.getExecResult()) {
                log.info("{} distribution sucess.", initPath);
            } else {
                log.info("{} distribution fail.", initPath);
                flag = false;
            }
        }
        if(!executor.exists(installDataDir).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s", installDataDir));
        }
        if(!executor.exists(Constants.INSTALL_PATH).getExecResult()) {
            executor.execShell(String.format("ln -s %s %s", installDataDir, Constants.INSTALL_PATH));
        }
        if(!executor.exists(Constants.MASTER_MANAGE_PACKAGE_PATH).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s/DDP", Constants.INSTALL_PATH));
            executor.execShell(String.format("ln -s %s/packages %s", initPath, Constants.MASTER_MANAGE_PACKAGE_PATH));
        }
        return flag;
    }
}
