package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "offlineSlave", description = "offlineSlave")
public class InitOfflineSlave extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-er", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;
    @CommandLine.Option(names = {"-ip", "--serverIp"}, description = "httpd服务ip", required = true)
    String serverIp;
    
    @CommandLine.Option(names = {"-port", "--serverPort"}, description = "httpd服务端口", required = true)
    String serverPort;

    @CommandLine.Option(names = {"-rPath", "--registryPath"}, description = "制品库路径", required = false)
    String registryPath;
    
    @Override
    public String name() {
        return "离线源slave配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        String repoOsSuffix = String.format("%s/%s/", archType.getArch(), osType.getDesc());
        if(OsType.isUnbuntu(osType)) {
            if(enableRegistry) {
                repoOsSuffix = String.format("repository/apt/%s/%s/", archType.getArch(), osType.getDesc());
            }
            executor.execShell("dpkg --configure -a");

            String httpRepoUrl = String.format("http://%s:%s/%s", serverIp, serverPort, repoOsSuffix);
            InitOfflineServer.aptRepoConfFile(executor, httpRepoUrl);
            executor.execShell("apt clean");
            ExecResult execResult = executor.execShell("apt update");
            if(execResult.getExecResult()) {
                log.info("init aptConf succes.");
            } else {
                throw new RuntimeException("init aptConf fail.");
            }
        } else if (OsType.isCentos(osType)) {
            if(enableRegistry) {
                repoOsSuffix = String.format("repository/yum/%s/%s/", archType.getArch(), osType.getDesc());
            }
            String httpRepoUrl = String.format("http://%s:%s/%s", serverIp, serverPort, repoOsSuffix);
            InitOfflineServer.yumRepoConfFile(executor, httpRepoUrl);
            executor.execShell("yum clean all");
            ExecResult execResult = executor.execShell("yum makecache");
            if(execResult.getExecResult()) {
                log.info("init yumConf succes.");
            } else {
                throw new RuntimeException("init yumConf fail.");
            }
        } else {
            throw new RuntimeException("os不支持,os=" + osType.getDesc());
        }
        return true;
    }
}
