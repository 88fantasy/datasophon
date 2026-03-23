package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.utils.ExecResult;
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
@CommandLine.Command(name = "jdk8", description = "init jdk8")
public class InitJdk8 extends InitBase {

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-e", "--enableRegistry"}, description = "是否启动制品库")
    boolean enableRegistry = false;

    @CommandLine.Option(names = {"-ip", "--registryIp"}, description = "制品ip", required = true)
    String registryIp;

    @CommandLine.Option(names = {"-port", "--registryPort"}, description = "制品端口", required = true)
    String registryPort;

    @CommandLine.Option(names = {"-u", "--registryUsername"}, description = "制品用户", required = true)
    String registryUsername;

    @CommandLine.Option(names = {"-p", "--registryPassword"}, description = "制品密码", required = true)
    String registryPassword;

    private Executor executor;
    @Override
    public String name() {
        return "初始化jdk8";
    }

    @Override
    public boolean doRun(Executor executor) {
        String jdkFolderPath = "/usr/local";
        executor.execShell("source /etc/profile");
        String jdkPathName = "jdk1.8.0_333";
        String bashProfilePath="/root/.bash_profile";
        String bashrcPath="/root/.bashrc";
        String etcProfilePath="/etc/profile";
        String jdkTarName = "jdk-8u333-linux-x64.tar.gz";
        if (ArchType.AARCH64.equals(executor.getArch())) {
            jdkTarName = "jdk-8u333-linux-aarch64.tar.gz";
        }
        ExecResult exec = executor.execShell("which java");
        String jdkAvailable = exec.getExecOut().trim();
        String javaBinPath = String.format("%s/%s/bin/java", jdkFolderPath, jdkPathName);
        if (jdkAvailable.equals(javaBinPath)) {
            log.info("JDK installed. java path is {}", javaBinPath);
        } else {
            log.info("JDK not installed, start to install");
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword,
                    jdkTarName, String.format("%s/%s", packagePath, jdkTarName));

            executor.execShell("sed -i '/export JAVA_HOME/d' /etc/profile");
            executor.execShell("sed -i '/export JAVA8_HOME/d' /etc/profile");
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
            executor.execShell(String.format("echo 'export JAVA8_HOME=%s' >>/etc/profile", javaHome));
            executor.execShell("echo 'export PATH=$PATH:$JAVA_HOME/bin' >>/etc/profile");
            executor.execShell(String.format("echo %s >>~/.bash_profile", javaSourceEnv));
            executor.execShell(String.format("echo %s >>~/.bashrc", javaSourceEnv));
            log.info("Prepare to config BCPROV...");
            String javaBcprovDir = javaHome + "/jre/lib/ext/";
            String javaBcprovJarName = "bcprov-jdk15on-1.68.jar";
            String javaBcprovJar = packagePath + "/" + javaBcprovJarName;
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword,
                    javaBcprovJarName, javaBcprovJar);

            executor.execShell(String.format("cp -a %s %s", javaBcprovJar, javaBcprovDir));
            log.info("BCPROV Installed.");
            executor.execShell(String.format("source %s", bashProfilePath));
            executor.execShell(String.format("source %s", bashrcPath));
            executor.execShell(String.format("source %s", etcProfilePath));
            executor.execShell(javaSourceEnv);
            log.info("JDK8 install successfully");
            log.info("INIT JDK8 finished");
        }

        return true;
    }
}
