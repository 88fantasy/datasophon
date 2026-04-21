package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.RepositoriesType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "kubectl", description = "init kubectl")
public class InitKubectl extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-kc", "--enableKubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean enableKubernetesCluster = true;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-in", "installPath"}, description = "安装路径", required = true)
    String installPath;

    @CommandLine.Option(names = {"-x", "--x86Tar"}, description = "x86_64包", required = true)
    String x86Tar;

    @CommandLine.Option(names = {"-a", "--aarch64Tar"}, description = "aarch64包", required = true)
    String aarch64Tar;
    
    @Override
    public String name() {
        return "安装kubectl";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        if (!enableKubernetesCluster) {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }

        Boolean isInstalled;
        isInstalled = executor.execShell("kubectl version").getExecResult();
        if(isInstalled) {
            log.info("kubectl is installed");
            return true;
        }

        String tarName = x86Tar;
        if (ArchType.AARCH64 == executor.getArch()) {
            tarName = aarch64Tar;
        }

        String tarPath = String.format("%s/%s", packagePath, tarName);
        CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, tarName, tarPath, true);

        if(!executor.exists(tarPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + tarPath);
        }
        executor.execShell(String.format("cp %s /usr/bin/kubectl", tarPath));
        log.info("kubectl install success");
        return true;
    }

}
