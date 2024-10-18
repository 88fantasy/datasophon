package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
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
@CommandLine.Command(name = "yum_conf", description = "init yumConf")
public class InitYumConf extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-ip", "--serverIp"}, description = "httpd服务ip", required = true)
    String serverIp;
    
    @CommandLine.Option(names = {"-port", "--serverPort"}, description = "httpd服务端口", required = true)
    String serverPort;
    
    @Override
    public String name() {
        return "yum离线仓库配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        String repoOsSuffix = String.format("offline-repos/%s/%s", archType.getArch(), osType.getDesc());
        String httpRepoUrl = String.format("http://%s:%s/%s", serverIp, serverPort, repoOsSuffix);
        yumRepoConf(executor, httpRepoUrl);
        executor.execShell("yum clean all");
        ExecResult execResult = executor.execShell("yum makecache");
        if(execResult.getExecResult()) {
            log.info("init yumConf succes.");
        } else {
            throw new RuntimeException("init yumConf fail.");
        }
        return true;
    }

    private void yumRepoConf(Executor executor, String baseurl) {
        executor.execShell("mv /etc/yum.repos.d /etc/yum.repos.d.$(date +%Y%m%d.%H%M%S)");
        executor.execShell("mkdir /etc/yum.repos.d");

        String localBaseRepoPath = "/etc/yum.repos.d/local_base.repo";
        List<String> conf = new ArrayList<>();
        conf.add("[LOCAL-REPO]");
        conf.add("name=LOCAL-REPO");
        conf.add(String.format("baseurl=%s", baseurl));
        conf.add("enabled=1");
        conf.add("gpgcheck=0");
        executor.writeLines(conf, localBaseRepoPath);
        log.info("baseUrl:{}", baseurl);
    }

}
