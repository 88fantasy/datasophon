package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.util.CliUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.RepositoriesType;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "k8sBaseServices", description = "init k8sBaseServices")
public class InitK8sBaseServices extends InitBase{

    @CommandLine.Option(names = {"-kc", "--enableKubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean enableKubernetesCluster = true;

    @CommandLine.Option(names = {"-fk8s", "--forceK8s"}, description = "k8s存在是否覆盖安装")
    boolean forceK8s = false;

    @CommandLine.Option(names = {"-ns", "--namespaces"}, description = "命名空间", required = false)
    List<String> namespaces;

    @CommandLine.Option(names = {"-ma", "--masters"}, description = "主节点", required = true)
    List<String> masters;

    @CommandLine.Option(names = {"-no", "--nodes"}, description = "计算节点", split = ",", required = true)
    List<String> nodes;

    @CommandLine.Option(names = {"-sea", "--sealos"}, description = "是否安装sealos", required = true)
    boolean sealos = true;

    @CommandLine.Option(names = {"-spx", "--sealosX86Tar"}, description = "sealos包", required = true)
    String sealosX86Tar;

    @CommandLine.Option(names = {"-spa", "--sealosArmTar"}, description = "sealos包", required = true)
    String sealosArmTar;

    @CommandLine.Option(names = {"-kub", "--kubernetes"}, description = "是否安装kubernetes", required = true)
    boolean kubernetes = true;

    @CommandLine.Option(names = {"-ktx", "--kubernetesX86Tar"}, description = "kubernetes包", required = true)
    String kubernetesX86Tar;

    @CommandLine.Option(names = {"-kta", "--kubernetesArmTar"}, description = "kubernetes包", required = true)
    String kubernetesArmTar;

    @CommandLine.Option(names = {"-h", "--helm"}, description = "是否安装helm", required = true)
    boolean helm = true;

    @CommandLine.Option(names = {"-hx", "--helmTX86ar"}, description = "helm包", required = true)
    String helmTX86ar;

    @CommandLine.Option(names = {"-ha", "--helmArmTar"}, description = "helm包", required = true)
    String helmArmTar;

    @CommandLine.Option(names = {"-calico", "--calico"}, description = "是否安装calico", required = true)
    boolean calico = true;

    @CommandLine.Option(names = {"-cx", "--calicoX86"}, description = "calico包", required = true)
    String calicoX86Tar;

    @CommandLine.Option(names = {"-ca", "--calicoArm"}, description = "calico包", required = true)
    String calicoArmTar;

    @CommandLine.Option(names = {"-ig", "--ingress"}, description = "是否安装ingress", required = true)
    boolean ingress = true;

    @CommandLine.Option(names = {"-igx", "--ingressX86"}, description = "ingress包", required = true)
    String ingressX86Tar;

    @CommandLine.Option(names = {"-iga", "--ingressArm"}, description = "ingress包", required = true)
    String ingressArmTar;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @CommandLine.Option(names = {"-spo", "--sshPort"}, description = "ssh访问端口", required = false)
    Integer sshPort;

    @CommandLine.Option(names = {"-spw", "--sshPasswd"}, description = "ssh访问密码", required = false)
    String sshPasswd;

    @Override
    public String name() {
        return "安装k8s集群";
    }

    @Override
    public boolean doRun(Executor executor) {
        Boolean isX86 = true;
        if (ArchType.AARCH64 == executor.getArch()) {
            isX86 = false;
        }
        String sealosPath = String.format("%s/%s", packagePath, isX86 ? sealosX86Tar : sealosArmTar);
        String kubernetesPath = String.format("%s/%s", packagePath, isX86 ? kubernetesX86Tar : kubernetesArmTar);
        String helmPath = String.format("%s/%s", packagePath, isX86 ? helmTX86ar : helmArmTar);
        String calicoPath = String.format("%s/%s", packagePath, isX86 ? calicoX86Tar : calicoArmTar);
        String ingressPath = String.format("%s/%s", packagePath, isX86 ? ingressX86Tar : ingressArmTar);

        if (!enableKubernetesCluster) {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }

        boolean installed = executor.execShell("kubectl version").getExecResult();
        if (installed)  {
            if(forceK8s) {
                log.info("k8s集群安装已安装，先删除k8s集群");
                executor.execShell(String.format("sealos delete --nodes %s", String.join(",", nodes)));
                executor.execShell(String.format("sealos delete --nodes %s", String.join(",", masters)));
                executor.execShell("sealos reset");
                log.info("删除k8s集群完成");
            } else {
                log.info("k8s集群安装已安装，跳过");
            }

        }

        if (nodes.size() < 3) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "nodes节点不能少于3");
        }

        if (isSealos()) {
            log.info("开始安装sealos...");
            String sealosCmd = String.format("tar zxvf %s sealos && chmod +x sealos && mv sealos /usr/bin", sealosPath);
            if (!executor.execShell(sealosCmd).isSuccess()) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "安装sealos失败");
            } else {
                log.info("成功安装sealos");
            }
        }

        if (isKubernetes()) {
            log.info("开始安装kubernetes...");
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? sealosX86Tar : sealosArmTar, sealosPath, true);
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? kubernetesX86Tar : kubernetesArmTar, kubernetesPath, true);
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? helmTX86ar : helmArmTar, helmPath, true);
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? calicoX86Tar : calicoArmTar, calicoPath, true);
            CliUtil.downRegistryFile(executor, enableRegistry, RepositoriesType.RAW, registryIp, registryPort, registryUsername, registryPassword, isX86 ? ingressX86Tar : ingressArmTar, ingressPath, true);

            String k8sCmd = String.format("/usr/bin/sealos run %s %s %s %s --masters %s --nodes %s --port=%s",
                    kubernetesPath,
                    helmPath,
                    calicoPath,
                    ingressPath,
                    String.join(",", masters), String.join(",", nodes),
                    sshPort);
            if(StringUtils.isNoneBlank(sshPasswd)){
                k8sCmd = k8sCmd + " --passwd=" + sshPasswd;
            }

            if (!executor.execShell(k8sCmd).isSuccess()) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "安装kubernetes失败");
            }
        }

        int count = 60;
        boolean ready = false;
        while (!ready) {
            ready = isKubernetesReady(executor);
            if (!ready) {
                log.info("等待k8s集群就绪...");
                try {
                    count = count - 1;
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if(count < 0) {
                throw new CommandLine.ExecutionException(new CommandLine(this), "k8s集就绪超时");
            }
        }

        for(String namespace : namespaces) {
            String namespaceCmd = String.format("/usr/bin/kubectl create namespace %s", namespace);
            executor.execShell(namespaceCmd);
        }

        log.info("k8s集群安装成功");
        return true;
    }

    private Boolean isKubernetesReady(Executor executor) {
        String cmd = "/usr/bin/kubectl get nodes | grep -v NAME | awk '{print $2}' | xargs echo";
        String result = executor.execShell(cmd).getExecOut();
        List<String> statusList = Arrays.stream(result.split(" ")).map(x -> x.trim()).collect(Collectors.toList());
        for(String statusItem: statusList) {
            if (!statusItem.equals("Ready")) {
                return false;
            }
        }
        return true;
    }
}

