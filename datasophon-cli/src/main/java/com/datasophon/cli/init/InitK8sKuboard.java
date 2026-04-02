package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
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
@CommandLine.Command(name = "kuboard", description = "init kuboard")
public class InitK8sKuboard extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-kc", "--kubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean kubernetesCluster = true;

    @CommandLine.Option(names = {"-ns", "--namespaces"}, description = "命名空间", required = false)
    List<String> namespaces;

    @CommandLine.Option(names = {"-kt", "--kt"}, description = "kubernetes包", required = true)
    String kuboardTar;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @Override
    public String name() {
        return "安装kuboard";
    }

    @Override
    public boolean doRun(Executor executor) {
        String kuboardPath = String.format("%s/%s", packagePath, kuboardTar);
        if (isKubernetesCluster()) {
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, kuboardTar, kuboardPath);
        } else {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }
        log.info("开始安装kuboard...");
        String cmd = String.format("/usr/bin/sealos run kuboard-v3-x86.tar");
        if (!executor.execShell(cmd).isSuccess()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "安装kuboard失败");
        } else {
            log.info("成功安装kuboard");
        }
        log.info("k8s kuboard 安装成功");
        return true;
    }
}
