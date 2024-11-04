package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.Constants;
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
@CommandLine.Command(name = "offlineServer", description = "offlineServer")
public class InitOfflineServer extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-p", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-f", "--reposTarName"}, description = "repos离线压缩包名", required = true)
    String reposTarName;

    @CommandLine.Option(names = {"-ip", "--serverIp"}, description = "httpd服务ip", required = true)
    String serverIp;

    @CommandLine.Option(names = {"-port", "--serverPort"}, description = "httpd服务端口", required = true)
    String serverPort;

    
    @Override
    public String name() {
        return "离线源Server配置";
    }

    @Override
    public boolean doRun(Executor executor) {
        String reposTarPath = String.format("%s/%s", packagePath, reposTarName);
        String httpRootPath = String.format("%s/httpd-root", Constants.INSTALL_PATH);
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        String repoOsSuffix = String.format("offline-repos/%s/%s", archType.getArch(), osType.getDesc());
        String repoOsPath = String.format("%s/%s", httpRootPath, repoOsSuffix);

        if(!executor.exists(packagePath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + packagePath);
        }
        if(!executor.exists(reposTarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + reposTarPath);
        }
        if(executor.exists(httpRootPath).getExecResult()) {
            executor.execShell(String.format("rm -rf %s/httpd-root", Constants.INSTALL_PATH));
        }
        executor.createDir(httpRootPath);
        executor.execShell(String.format("tar -zxf %s -C %s", reposTarPath, httpRootPath));
        if(!executor.exists(repoOsPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "dir not found : " + repoOsPath);
        }
        if(OsType.isUnbuntu(osType)) {
            String defaultConfPath = "/etc/apache2/sites-available/000-default.conf";
            String apache2ConfPath = "/etc/apache2/apache2.conf";
            String portsConfPath = "/etc/apache2/ports.conf";
            // apt离线源配置
            String fileRepoUrl = String.format("file://%s ./", repoOsPath);
            aptRepoConfFile(executor, fileRepoUrl);
            executor.execShell("apt clean");
            ExecResult aptResult = executor.execShell("apt update");
            if (!aptResult.getExecResult()) {
                throw new RuntimeException("apt update fail");
            }
            executor.execShell("apt -y install apache2");
            ExecResult aResult = executor.execShell("apache2 -v");
            if (aResult.getExecResult()) {
                executor.execShell(String.format("sed -i 's|DocumentRoot /var/www/html|DocumentRoot %s|g' %s", httpRootPath, defaultConfPath));
                executor.execShell(String.format("sed -i 's|<VirtualHost \\*:80>|<VirtualHost *:%s>|g' %s", serverPort, defaultConfPath));
                executor.execShell(String.format("sed -i 's|<Directory /var/www/>|<Directory %s>|g' %s", httpRootPath, apache2ConfPath));
                executor.execShell(String.format("sed -i 's|Listen 80|Listen %s|g' %s", serverPort, portsConfPath));
            }
            ExecResult result = executor.execShell("systemctl restart apache2");
            if (!result.getExecResult()) {
                throw new RuntimeException("apache2 server fail");
            }
        } else if (OsType.isCentos(osType)) {
            // yum离线源配置
            String fileRepoUrl = String.format("file://%s", repoOsPath);
            yumRepoConfFile(executor, fileRepoUrl);
            executor.execShell("yum clean all");
            ExecResult fileResult = executor.execShell("yum makecache");
            if (!fileResult.getExecResult()) {
                throw new RuntimeException("file yum make cache fail");
            }
            executor.execShell("yum install -y httpd");
            ExecResult vResult = executor.execShell("httpd -v");
            if (!vResult.getExecOut().equals("127")) {
                String httpdConfPath = "/etc/httpd/conf/httpd.conf";
                executor.execShell(String.format("sed -i 's/^DocumentRoot \"/var/www/html\"/DocumentRoot \"'%s'\"/g' %s", httpRootPath, httpdConfPath));
                executor.execShell(String.format("sed -i 's/^<Directory \"/var/www/html\">/<Directory \"'%s'\">/g' %s", httpRootPath, httpdConfPath));
                executor.execShell(String.format("sed -i 's/^Listen 80/Listen '%s'/g' %s", serverPort, httpdConfPath));
            }
            ExecResult result = executor.execShell("systemctl restart httpd");
            if (!result.getExecResult()) {
                throw new RuntimeException("httpd server fail");
            }
        } else {
            throw new RuntimeException("os不支持,os=" + osType.getDesc());
        }
        return true;
    }

    public static void yumRepoConfFile(Executor executor, String baseurl) {
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

    public static void aptRepoConfFile(Executor executor, String baseurl) {
        executor.execShell("mv /etc/apt/sources.list /etc/apt/sources.list.bak");
        executor.execShell("mv /etc/apt/sources.list.d/rightscale_extra.sources.list /etc/apt/sources.list.d/rightscale_extra.sources.list.bak");

        String localBaseRepoPath = "/etc/apt/sources.list";
        List<String> conf = new ArrayList<>();
        conf.add(String.format("deb [trusted=yes] %s", baseurl));
        executor.writeLines(conf, localBaseRepoPath);
        log.info("baseUrl:{}", baseurl);
    }
}
