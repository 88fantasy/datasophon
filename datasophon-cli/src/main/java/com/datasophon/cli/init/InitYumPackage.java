package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;

import java.io.File;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "yumpackage", description = "init YumPackage")
public class InitYumPackage extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-rp", "--httpdRootPath"}, description = "httpd根路径")
    String httpdRootPath;
    
    @CommandLine.Option(names = {"-f", "--reposTarFilePath"}, description = "repos离线压缩包", required = true)
    String reposTarFilePath;
    
    @Override
    public String name() {
        return "yum安装包解压";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        File configFile = new File(configFilePath);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configFilePath);
        }
        if (!new File(httpdRootPath).exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + httpdRootPath);
        }
        if (!new File(reposTarFilePath).exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + reposTarFilePath);
        }
        String cmd = String.format("tar -zxf %s -C %s", reposTarFilePath, httpdRootPath);
        ExecResult result = executor.execShell(cmd);
        if (result.getExecResult()) {
            log.info("init sucess.");
        } else {
            log.info("init failed.");
        }
        return true;
    }
}
