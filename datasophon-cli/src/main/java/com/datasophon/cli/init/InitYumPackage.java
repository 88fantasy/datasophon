package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "yumpackage", description = "init YumPackage")
public class InitYumPackage extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;
    
    @CommandLine.Option(names = {"-f", "--reposTarFilePath"}, description = "repos离线压缩包", required = true)
    String reposTarFilePath;
    
    @Override
    public String name() {
        return "yum安装包解压";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        if(!executor.exists(packagePath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
        }
        if(!executor.exists(reposTarFilePath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + reposTarFilePath);
        }
        if(!executor.exists(reposTarFilePath).getExecResult()) {
            executor.createDir(Constants.INSTALL_PATH);
        }
        ExecResult result = executor.execShell(String.format("tar -zxf %s -C %s/httpd-root", reposTarFilePath, Constants.INSTALL_PATH));
        if (result.getExecResult()) {
            log.info("init sucess.");
        } else {
            log.info("init failed.");
        }
        return true;
    }
}
