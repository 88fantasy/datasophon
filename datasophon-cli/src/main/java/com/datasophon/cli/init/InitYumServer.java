package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "yum_server", description = "init yumServer")
public class InitYumServer extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-p", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-f", "--reposTarName"}, description = "repos离线压缩包名", required = true)
    String reposTarName;

    @CommandLine.Option(names = {"-ip", "--serverIp"}, description = "httpd服务ip", required = true)
    String serverIp;

    @CommandLine.Option(names = {"-port", "--serverPort"}, description = "httpd服务端口", required = true)
    String serverPort;

    @CommandLine.Option(names = {"-d", "--templateDir"}, description = "模板目录", required = true)
    String templateDir;
    
    @Override
    public String name() {
        return "yum离线服务配置";
    }

    @Override
    public boolean doRun(Executor executor) {
        String reposTarPath = String.format("%s/%s", packagePath, reposTarName);
        String httpRootPath = String.format("%s/httpd-root", Constants.INSTALL_PATH);
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        String repoOsSuffix = String.format("offline-repos/%s/%s", archType.getArch(), osType.getDesc());
        String repoOsPath = String.format("%s/%s", httpRootPath, repoOsSuffix);
        String fileRepoUrl = String.format("file://%s", repoOsPath);
        String templateName = "httpd.conf.ftl";
        String templateFile = String.format("%s/%s", templateDir, templateName);

        if(!executor.exists(packagePath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
        }
        if(!executor.exists(reposTarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + reposTarPath);
        }
        if(!new File(templateDir).exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "local dir not found : " + templateDir);
        }
        if(!new File(templateFile).exists()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "local file not found : " + templateFile);
        }
        if(executor.exists(httpRootPath).getExecResult()) {
            executor.execShell(String.format("rm -rf %s/httpd-root", Constants.INSTALL_PATH));
        }
        executor.createDir(httpRootPath);
        executor.execShell(String.format("tar -zxf %s -C %s", reposTarPath, httpRootPath));
        if(!executor.exists(repoOsPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + repoOsPath);
        }
        yumRepoConf(executor, fileRepoUrl);
        executor.execShell("yum clean all");
        ExecResult fileResult = executor.execShell("yum makecache");
        if (!fileResult.getExecResult()) {
            throw new RuntimeException("file yum make cache fail");
        }
        executor.execShell("yum install -y httpd");
        ExecResult vResult = executor.execShell("httpd -v");
        if (!vResult.getExecOut().equals("127")) {
            Map<String, Object> confData = new HashMap<>();
            confData.put("httpdRootPath", httpRootPath);
            confData.put("httpdListenPort", serverPort);
            try {
                String templateFileTmp = String.format("%s.tmp", templateFile);
                String distPath = "/etc/httpd/conf/httpd.conf";
                CliUtil.generateConfigFile(templateDir, templateName, confData, templateFileTmp);
                executor.execShell("mv /etc/httpd/conf/httpd.conf /etc/httpd/conf/httpd.conf.bak");
                executor.sendFile(templateFileTmp, distPath, true);
                ShellUtils.execShell(String.format("rm -f %s", templateFileTmp));
            } catch (Exception e) {
                throw new RuntimeException("/etc/httpd/conf/httpd.conf.ftl overwrite failed", e);
            }
        }
        ExecResult result = executor.execShell("systemctl restart httpd");
        if (!result.getExecResult()) {
            throw new RuntimeException("httpd server fail");
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
