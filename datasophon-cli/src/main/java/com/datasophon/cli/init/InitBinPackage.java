package com.datasophon.cli.init;

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

    @CommandLine.Option(names = {"-pf", "initPathOverwriteForce"}, description = "initPath目录存在是否覆盖")
    boolean initPathOverwriteForce = false;
    
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
        String dataInstallDatasophon = "/data/install_datasophon";
        if(!installPathF.exists()) {
            ShellUtils.execShell(String.format("mkdir -p %s", dataInstallDatasophon));
            ShellUtils.execShell(String.format("ln -s %s %s", dataInstallDatasophon, Constants.INSTALL_PATH));
        }

        File installPackagePathF = new File(Constants.MASTER_MANAGE_PACKAGE_PATH);
        if(!installPackagePathF.exists()) {
            ShellUtils.execShell(String.format("mkdir -p %s/DDP", Constants.INSTALL_PATH));
            ShellUtils.execShell(String.format("ln -s %s/packages %s", initPath, Constants.MASTER_MANAGE_PACKAGE_PATH));
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
        if(!executor.exists(Constants.INSTALL_PATH).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s", dataInstallDatasophon));
            executor.execShell(String.format("ln -s %s %s", dataInstallDatasophon, Constants.INSTALL_PATH));
        }
        if(!executor.exists(Constants.MASTER_MANAGE_PACKAGE_PATH).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s/DDP", Constants.INSTALL_PATH));
            executor.execShell(String.format("ln -s %s/packages %s", initPath, Constants.MASTER_MANAGE_PACKAGE_PATH));
        }
        return flag;
    }
}
