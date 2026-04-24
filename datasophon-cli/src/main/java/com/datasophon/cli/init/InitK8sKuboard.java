package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.enums.RepositoriesType;
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

    @CommandLine.Option(names = {"-kc", "--enableKubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean enableKubernetesCluster = true;

    @CommandLine.Option(names = {"-ktx", "--kuboardX86Tar"}, description = "kubernetes包", required = false)
    String kuboardX86Tar;

    @CommandLine.Option(names = {"-kta", "--kuboardArmTar"}, description = "kubernetes包", required = false)
    String kuboardArmTar;

    @CommandLine.Option(names = {"-et", "--etcds"}, description = "etcd节点", split = ",", required = true)
    List<String> etcds;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @Override
    public String name() {
        return "安装kuboard";
    }

    @Override
    public boolean doRun(Executor executor) {
        if (!enableKubernetesCluster) {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }

        Boolean isX86 = true;
        if (ArchType.AARCH64 == executor.getArch()) {
            isX86 = false;
        }
        String kuboardPath = String.format("%s/%s", packagePath, isX86 ? kuboardX86Tar : kuboardArmTar);

        log.info("开始安装kuboard...");
        CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? kuboardX86Tar : kuboardArmTar, kuboardPath, true);
        
        // 执行打标签命令
        String etcdsStr = String.join(" ", etcds);
        String labelCmd = String.format("/usr/bin/kubectl label nodes %s k8s.kuboard.cn/role=etcd", etcdsStr);
        executor.execShell(labelCmd);
        
        // 验证标签是否成功
        for (String node : etcds) {
            String checkCmd = String.format("/usr/bin/kubectl get nodes --show-labels | grep %s | grep k8s.kuboard.cn/role=etcd", node);
            ExecResult result = executor.execShell(checkCmd);
            if (!result.isSuccess()) {
                log.error("节点 {} 打标签失败", node);
                throw new CommandLine.ExecutionException(new CommandLine(this), "执行打标签失败。node:" + node);
            }
        }
        
        // 根据标签结果决定是否安装kuboard
        String cmd = String.format("/usr/bin/sealos run %s", kuboardPath);
        if (!executor.execShell(cmd).isSuccess()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "安装kuboard失败");
        }
        log.info("k8s kuboard 安装成功");
        return true;
    }
}
