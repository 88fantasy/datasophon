package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import picocli.CommandLine;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import org.yaml.snakeyaml.Yaml;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "httpd", description = "init httpd")
public class InitHttpd extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-p", "--httpdPkgPath"}, description = "安装包路径", required = true)
    String httpdPkgPath;
    
    @CommandLine.Option(names = {"-rp", "--httpdRootPath"}, description = "httpd根路径", defaultValue = "/data/unimedical/.data/httpd-root")
    String httpdRootPath;
    
    @CommandLine.Option(names = {"-port", "--httpdListenPort"}, description = "服务监听端口", defaultValue = "4080")
    String httpdListenPort;
    
    @CommandLine.Option(names = {"-d", "--templateDir"}, description = "模板目录", required = true)
    String templateDir;
    
    @CommandLine.Option(names = {"-h", "--httpConf"}, description = "httpCon模板名", required = true)
    String httpdConf;
    
    @CommandLine.Option(names = {"-f", "--force"}, description = "强制配置")
    boolean force;
    
    @Override
    public String name() {
        return "httpd服务";
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
        
        // httpd安装
        boolean isInit = false;
        String cmd = "httpd -v";
        ExecResult vResult = executor.execShell(cmd);
        if (vResult.getExecResult()) {
            log.info("httpd have already installed");
        } else {
            String repoPath = String.format("%s/%s/%s", httpdPkgPath, global.getArch().name(), global.getOs().name());
            cmd = String.format("rpm -ivh %s/*.rpm", repoPath);
            ExecResult httpdResult = executor.execShell(cmd);
            if (httpdResult.getExecResult()) {
                log.info("httpd install sucess.");
                isInit = true;
            } else {
                log.info("httpd install failed.");
            }
        }
        
        // 覆盖配置
        if (isInit || force) {
            Map<String, Object> confData = new HashMap<>();
            confData.put("httpdRootPath", httpdRootPath);
            confData.put("httpdListenPort", httpdListenPort);
            try {
                cmd = String.format("mkdir -p %s", httpdRootPath);
                executor.execShell(cmd);
                
                cmd = "mv /etc/httpd/conf/httpd.conf.ftl /etc/httpd/conf/httpd.conf.ftl.bak";
                executor.execShell(cmd);
                
                CliUtil.generateConfigFile(templateDir, httpdConf, confData, "/etc/httpd/conf/httpd.conf");
            } catch (Exception e) {
                log.error("/etc/httpd/conf/httpd.conf.ftl overwrite failed.", e);
            }
            log.info("/etc/httpd/conf/httpd.conf.ftl overwrite sucess");
        }
        
        // httpd restart
        cmd = String.format("systemctl restart httpd");
        ExecResult reResult = executor.execShell(cmd);
        if (reResult.getExecResult()) {
            log.info("restart httpd sucess");
        } else {
            log.info("restart httpd failed");
        }
        return true;
    }
}
