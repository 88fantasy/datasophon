package com.datasophon.cli.init;

import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.io.File;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "yumpackage", description = "init YumPackage")
public class InitYumPackage implements Runnable {
    
    @CommandLine.Option(arity = "1", names = {"-c", "--config"}, description = "配置文件", required = true)
    String configFilePath;
    
    @CommandLine.Option(names = {"-rp", "--httpdRootPath"}, description = "httpd根路径")
    String httpdRootPath;
    
    @CommandLine.Option(names = {"-f", "--reposTarFilePath"}, description = "repos离线压缩包", required = true)
    String reposTarFilePath;
    
    @Override
    public void run() {
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
        System.out.println(cmd);
        ExecResult result = ShellUtils.execWithStatus("/", Arrays.asList(cmd.split("\\s+")), 60);
        if (result.getExecResult()) {
            log.info("init sucess.");
        } else {
            log.info("init failed.");
        }
    }
}
