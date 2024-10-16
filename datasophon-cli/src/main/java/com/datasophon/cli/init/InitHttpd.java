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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "httpd", description = "init httpd")
public class InitHttpd extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;
    
    @CommandLine.Option(names = {"-p", "--pkgTarName"}, description = "安装包名称", required = true)
    String pkgTarName;
    
    @CommandLine.Option(names = {"-port", "--httpdListenPort"}, description = "服务监听端口", defaultValue = "4080")
    String httpdListenPort;
    
    @CommandLine.Option(names = {"-d", "--templateDir"}, description = "模板目录", required = true)
    String templateDir;
    
    @CommandLine.Option(names = {"-t", "--templateFile"}, description = "模板配置文件", required = true)
    String templateFile;
    
    @Override
    public String name() {
        return "httpd服务";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        ArchType archType = executor.getArch();
        OsType osType = executor.getOs();
        // httpd安装
        executor.createDir(Constants.INSTALL_PATH);
        executor.execShell(String.format("tar -zxvf %s/%s -C %s", packagePath, pkgTarName, Constants.INSTALL_PATH));
        String repoPath = String.format("%s/httpd-pkg/%s/%s", Constants.INSTALL_PATH, archType.name(), osType.name());
        executor.execShell(String.format("rpm -ivh %s/*.rpm", repoPath));

        ExecResult vResult = executor.execShell("httpd -v");
        if (!vResult.getExecOut().equals("127")) {
            Map<String, Object> confData = new HashMap<>();
            String httpdRootPath = String.format("%s/httpd-root", Constants.INSTALL_PATH);
            confData.put("httpdRootPath", httpdRootPath);
            confData.put("httpdListenPort", httpdListenPort);
            try {
                executor.execShell(String.format("mkdir -p %s", httpdRootPath));
                // 替换 httpd.conf
                String tmpPath = String.format("%s.tmp", templateFile);
                String distPath = "/etc/httpd/conf/httpd.conf";
                CliUtil.generateConfigFile(templateDir, templateFile, confData, tmpPath);
                executor.execShell("mv /etc/httpd/conf/httpd.conf /etc/httpd/conf/httpd.conf.bak");
                executor.sendFile(tmpPath, distPath, true);
                ShellUtils.execShell(String.format("rm -f %s", tmpPath));
            } catch (Exception e) {
                log.error("/etc/httpd/conf/httpd.conf.ftl overwrite failed.", e);
            }
            log.info("/etc/httpd/conf/httpd.conf.ftl overwrite sucess");
            executor.execShell(String.format("rm -rf %s/httpd-pkg", Constants.INSTALL_PATH));

            // httpd restart
            executor.execShell("systemctl restart httpd");
            log.info("httpd install sucess.");

        } else {
            log.info("httpd install failed.");
        }

        return true;
    }
}
