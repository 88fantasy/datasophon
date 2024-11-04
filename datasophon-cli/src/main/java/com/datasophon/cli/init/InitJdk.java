package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * 初始化jdk
 */
@Slf4j
@Data
@CommandLine.Command(name = "jdk", description = "init jdk")
public class InitJdk extends InitBase {

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    private Executor executor;
    @Override
    public String name() {
        return "初始化jdk";
    }

    @Override
    public boolean doRun(Executor executor) {
        String jdkFolderPath = "/usr/java";
        executor.execShell("source /etc/profile");
        String jdkPathName = "jdk1.8.0_333";
        String jdkVersion = "1.8";
        String bashProfilePath="/root/.bash_profile";
        String bashrcPath="/root/.bashrc";
        String etcProfilePath="/etc/profile";
        String jdkTarName = "jdk-8u333-linux-x64.tar.gz";
        if (ArchType.AARCH64.equals(executor.getArch())) {
            jdkTarName = "jdk-8u333-linux-aarch64.tar.gz";
        }
        ExecResult exec = executor.execShell("java -version 2>&1 | awk 'NR==1{gsub(/\"/,\"\");print $3}'");
        String jdkAvailable = exec.getExecOut();
        exec = executor.execShell(String.format("echo %s | grep %s", jdkAvailable, jdkVersion));
        if (exec.getExecResult()) {
            log.info("JDK installed");
        } else {
            log.info("JDK not installed, start to install");
            executor.execShell("sed -i '/export JAVA_HOME/d' /etc/profile");
            executor.execShell("sed -i '/export CLASSPATH/d' /etc/profile");
            executor.execShell("sed -i '/export PATH=$PATH:$JAVA_HOME/d' /etc/profile");
            executor.execShell("sed -i '/source \\/etc\\/profile/d' /root/.bash_profile");
            executor.execShell("sed -i '/source \\/etc\\/profile/d' /root/.bashrc");
            log.info("Prepare to Install JDK...");
            executor.execShell(String.format("mkdir -p %s", jdkFolderPath));
            executor.execShell(String.format("tar -zxf %s/%s -C %s", packagePath, jdkTarName, jdkFolderPath));
            String javaHome = jdkFolderPath + "/" + jdkPathName;
            String javaSourceEnv="source /etc/profile";
            executor.execShell(String.format("echo 'export JAVA_HOME=%s' >>/etc/profile", javaHome));
            executor.execShell("echo 'export PATH=$PATH:$JAVA_HOME/bin' >>/etc/profile");
            executor.execShell(String.format("echo %s >>~/.bash_profile", javaSourceEnv));
            executor.execShell(String.format("echo %s >>~/.bashrc", javaSourceEnv));
            log.info("Prepare to config BCPROV...");
            String javaBcprovDir = javaHome + "/jre/lib/ext/";
            String javaBcprovJar = packagePath + "/bcprov-jdk15on-1.68.jar";
            executor.execShell(String.format("cp -a %s %s", javaBcprovJar, javaBcprovDir));
            log.info("BCPROV Installed.");
            executor.execShell(String.format("source %s", bashProfilePath));
            executor.execShell(String.format("source %s", bashrcPath));
            executor.execShell(String.format("source %s", etcProfilePath));
            executor.execShell(javaSourceEnv);
            log.info("JDK install successfully");
            log.info("INIT JDK finished");
        }

        return true;
    }
}
