package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "yumconf", description = "init yumConf")
public class InitYumConf extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-ip", "--httpdServerIp"}, description = "httpd服务ip", required = true)
    String httpdServerIp;
    
    @CommandLine.Option(names = {"-port", "--httpdListenPort"}, description = "httpd服务端口", required = true)
    String httpdListenPort;
    
    @Override
    public String name() {
        return "离线yum仓库配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        String localRepoUrl = String.format("http://%s:%s/offline-repos/%s/%s", httpdServerIp, httpdListenPort, archType.name(), osType.name());


        executor.execShell("mv /etc/yum.repos.d /etc/yum.repos.d.$(date +%Y%m%d.%H%M%S)");
        executor.execShell("mkdir /etc/yum.repos.d");
        
        String localBaseRepoPath = "/etc/yum.repos.d/local_base.repo";
        List<String> conf = new ArrayList<>();
        conf.add("[LOCAL-REPO]");
        conf.add("name=LOCAL-REPO");
        conf.add(String.format("baseurl=%s", localRepoUrl));
        conf.add("enabled=1");
        conf.add("gpgcheck=0");
        executor.writeLines(conf, localBaseRepoPath);

        executor.execShell("yum clean all");
        executor.execShell("yum makecache");

        log.info("baseurl:{}", localRepoUrl);
        log.info("/etc/yum.repos.d/local_base.repo init sucess.");
        return true;
    }
}
