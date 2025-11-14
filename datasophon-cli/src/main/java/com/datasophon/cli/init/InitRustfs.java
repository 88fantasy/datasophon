package com.datasophon.cli.init;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "rustfs", description = "init rustfs")
public class InitRustfs extends InitBase {

    @CommandLine.Option(names = {"-e", "--enable"}, description = "是否安装")
    boolean enable = false;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-i", "--installPath"}, description = "installPath", required = true)
    String installPath;

    @CommandLine.Option(names = {"-x", "--x86Tar"}, description = "x86_64包", required = true)
    String x86Tar;

    @CommandLine.Option(names = {"-a", "--aarch64Tar"}, description = "aarch64包", required = true)
    String aarch64Tar;

    @CommandLine.Option(names = {"-wh", "--webHost"}, description = "webHost", required = true)
    String webHost;
    @CommandLine.Option(names = {"-wp", "--webPort"}, description = "webPort", required = true)
    String webPort;

    @CommandLine.Option(names = {"-ap", "--apiPort"}, description = "apiPort", required = true)
    String apiPort;

    @CommandLine.Option(names = {"-u", "--username"}, description = "username", required = true)
    String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "password", required = true)
    String password;

    @Override
    public String name() {
        return "安装rustfs";
    }

    @Override
    public boolean doRun(Executor executor) {
        if(!enable) {
            log.info("rustfs enable is: {}, skip", enable);
            return true;
        }

        if(!FileUtil.exist(installPath)) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + installPath);
        }
        String home = String.format("%s/rustfs", installPath);
        String dataPath = String.format("%s/data", home);
        String logsPath = String.format("%s/logs", home);
        if(FileUtil.exist(home)) {
            log.info("rusfs path exist: {}", home);
        } else {
            String tarPath = String.format("%s/%s", packagePath, x86Tar);
            if (ArchType.AARCH64 == executor.getArch()) {
                tarPath = String.format("%s/%s", packagePath, aarch64Tar);
            }
            if (!FileUtil.exist(tarPath)) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + tarPath);
            }
            executor.execShell(String.format("tar xvz -f %s -C %s", tarPath, installPath));
            executor.execShell(String.format("mv %s/rustfs-* %s", installPath, home));
            executor.createDir(dataPath);
            executor.createDir(logsPath);
        }

        if(!checkStart(executor)) {
            start(executor, home, dataPath, logsPath);
            executor.execShell("sleep 3");
        }

        //检测启动
        if(checkStart(executor)) {
            log.info("rusfs install sucess. path:{}", home);
            return true;
        } else {
            log.info("rusfs install failed.");
            return false;
        }
    }

    public boolean checkStart(Executor executor) {
        ExecResult execResult = executor.execShell("ps -ef | grep rustfs  | grep -v datasophon-cli | grep -v grep");
        if(execResult.getExecResult()) {
            log.info("rustfs has started.");
            return true;
        } else {
            log.info("rustfs has not started.");
            return false;
        }
    }

    public boolean start(Executor executor, String home, String data, String logs) {
        ExecResult execResult = executor.execShell(String.format("%s/rustfs --address %s:%s --console-enable --console-address %s:%s  --access-key %s --secret-key %s %s > %s/rustfs.log 2>&1 &",
                home, webHost, apiPort, webHost, webPort, username, password, data, logs));
        return execResult.getExecResult();
    }
}
