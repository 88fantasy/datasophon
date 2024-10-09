package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;

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
        File configFile = new File(configFilePath);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configFilePath);
        }
        
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        ClusterConfig clusterConfig = yaml.loadAs(content, ClusterConfig.class);
        GlobalConfig global = clusterConfig.getGlobal();
        if (ObjectUtil.isNull(global.getOs())) {
            global.setOs(OsType.CentOS7);
        }
        if (ObjectUtil.isNull(global.getArch())) {
            String cpuArchitecture = ShellUtils.getCpuArchitecture();
            global.setArch(ArchType.of(cpuArchitecture));
        }
        String localRepoUrl = String.format("http://%s:%s/offline-repos/%s/centos-7",
                httpdServerIp, httpdListenPort, global.getArch(), global.getOs());
        
        String cmd = "mv /etc/yum.repos.d /etc/yum.repos.d.$(date +%Y%m%d.%H%M%S)";
        System.out.println(cmd);
        executor.execShell(cmd);
        
        cmd = "mkdir /etc/yum.repos.d";
        System.out.println(cmd);
        executor.execShell(cmd);
        
        String localBaseRepoPath = "/etc/yum.repos.d/local_base.repo";
        List<String> conf = new ArrayList<>();
        conf.add("[LOCAL-REPO]");
        conf.add("name=LOCAL-REPO");
        conf.add(String.format("baseurl=%s", localRepoUrl));
        conf.add("enabled=1");
        conf.add("gpgcheck=0");
        executor.writeLines(conf, localBaseRepoPath);
        log.info("baseurl:{}", localRepoUrl);
        log.info("/etc/yum.repos.d/local_base.repo init sucess.");
        return true;
    }
}
