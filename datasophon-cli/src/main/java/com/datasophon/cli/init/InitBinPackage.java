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
    
    @CommandLine.Option(names = {"-i", "--datasophonInitPath"}, description = "datasophonInitPath", required = true)
    private String datasophonInitPath;

    @CommandLine.Option(names = {"-in", "--installPath"}, description = "安装路径", required = true)
    String installPath;

    @CommandLine.Option(names = {"-pf", "--initPathOverwriteForce"}, description = "initPath目录存在是否覆盖")
    boolean initPathOverwriteForce = false;
    
    @Override
    public String name() {
        return "分发datasophon-init资源包";
    }

    @Override
    public boolean doRun(Executor executor) {
        Boolean flag = true;
        // 本地datasophon-init
        File initPathF = new File(datasophonInitPath);
        if (!initPathF.exists() || !initPathF.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "local dir not found : " + datasophonInitPath);
        }

        File installPathF = new File(installPath);
        if(!FileUtil.exist(installPathF)) {
            ShellUtils.execShell(String.format("mkdir -p %s", installPath));
        }

        // 远程datasophon-init
        ExecResult remoteResult = executor.exists(datasophonInitPath);
        if(remoteResult.getExecResult() && !initPathOverwriteForce){
            log.info("远程datasophon-init目录已存在,且overwrite={},跳过)", initPathOverwriteForce);
        } else {
            ExecResult createResult = executor.execShell(String.format("mkdir -p %s", datasophonInitPath));
            if (!createResult.getExecResult()) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "dist createDir fail : " + datasophonInitPath);
            }
            log.info("分发资源包路径:{} start", datasophonInitPath);
            long ts = System.currentTimeMillis();
            ExecResult execResult = executor.sendDir(datasophonInitPath, datasophonInitPath, true);
            log.info("分发资源包路径:{} end,耗时:{}s", datasophonInitPath, (System.currentTimeMillis() - ts) / 1000.0);
            if (execResult.getExecResult()) {
                log.info("{} distribution sucess.", datasophonInitPath);
            } else {
                throw new CommandLine.ExecutionException(new CommandLine(this), String.format("%s 分发资源包失败.", datasophonInitPath));
            }
        }
        if(!executor.exists(installPath).getExecResult()) {
            executor.execShell(String.format("mkdir -p %s", installPath));
        }
        if(!executor.exists(Constants.MASTER_MANAGE_PACKAGE_PATH).getExecResult()) {
            ShellUtils.execShell(String.format("mkdir -p %s", Constants.MASTER_MANAGE_PACKAGE_PATH));
        }
        return flag;
    }
}
