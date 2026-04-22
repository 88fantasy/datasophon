package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.RepositoriesType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * 初始化jdk
 */
@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "jdk17", description = "init jdk17")
public class InitJdk17 extends InitBase {

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    private Executor executor;
    @Override
    public String name() {
        return "初始化jdk17";
    }

    @Override
    public boolean doRun(Executor executor) {
        String jdkFolderPath = "/usr/local";
        executor.execShell("source /etc/profile");
        String jdkPathName = "jdk-17.0.1";
        String bashProfilePath="/root/.bash_profile";
        String bashrcPath="/root/.bashrc";
        String etcProfilePath="/etc/profile";
        String jdkTarName = "openjdk-17.0.1_linux-x64_bin.tar.gz";
        if (ArchType.AARCH64.equals(executor.getArch())) {
            jdkTarName = "openjdk-17.0.1_linux-aarch64_bin.tar.gz";
        }
        String javaBinPath = String.format("%s/%s/bin/java", jdkFolderPath, jdkPathName);
        if (executor.exists(javaBinPath).getExecResult()) {
            log.info("JDK installed. java path is {}", javaBinPath);
        } else {
            log.info("JDK not installed, start to install");
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword,
                    jdkTarName, String.format("%s/%s", packagePath, jdkTarName), true);

            executor.execShell("sed -i '/export JAVA17_HOME/d' /etc/profile");
            executor.execShell("sed -i '/export CLASSPATH/d' /etc/profile");
            executor.execShell("sed -i '/export PATH=$PATH:$JAVA17_HOME/d' /etc/profile");
            executor.execShell("sed -i '/source \\/etc\\/profile/d' /root/.bash_profile");
            executor.execShell("sed -i '/source \\/etc\\/profile/d' /root/.bashrc");
            log.info("Prepare to Install JDK...");
            executor.execShell(String.format("mkdir -p %s", jdkFolderPath));
            executor.execShell(String.format("tar -zxf %s/%s -C %s", packagePath, jdkTarName, jdkFolderPath));
            String javaHome = jdkFolderPath + "/" + jdkPathName;
            String javaSourceEnv="source /etc/profile";
            executor.execShell(String.format("echo 'export JAVA17_HOME=%s' >>/etc/profile", javaHome));
            executor.execShell(String.format("echo %s >>~/.bash_profile", javaSourceEnv));
            executor.execShell(String.format("echo %s >>~/.bashrc", javaSourceEnv));

            executor.execShell(String.format("source %s", bashProfilePath));
            executor.execShell(String.format("source %s", bashrcPath));
            executor.execShell(String.format("source %s", etcProfilePath));
            executor.execShell(javaSourceEnv);
            log.info("JDK17 install successfully");
            log.info("INIT JDK17 finished");
        }

        return true;
    }
}
