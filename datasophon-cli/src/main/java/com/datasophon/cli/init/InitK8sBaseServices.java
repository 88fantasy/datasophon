package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.cli.util.CliUtil;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "k8sBaseServices", description = "init k8sBaseServices")
public class InitK8sBaseServices extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-kc", "--kubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean kubernetesCluster = true;

    @CommandLine.Option(names = {"-ns", "--namespaces"}, description = "命名空间", required = false)
    List<String> namespaces;

    @CommandLine.Option(names = {"-m", "--masters"}, description = "主节点", required = true)
    List<String> masters;

    @CommandLine.Option(names = {"-n", "--nodes"}, description = "计算节点", split = ",", required = true)
    List<String> nodes;

    @CommandLine.Option(names = {"-s", "--sealos"}, description = "是否安装sealos", required = true)
    boolean sealos = true;

    @CommandLine.Option(names = {"-sp", "sealosTar"}, description = "sealos包", required = true)
    String sealosTar;

    @CommandLine.Option(names = {"-k", "--kubernetes"}, description = "是否安装kubernetes", required = true)
    boolean kubernetes = true;

    @CommandLine.Option(names = {"-kt", "--kt"}, description = "kubernetes包", required = true)
    String kubernetesTar;

    @CommandLine.Option(names = {"-h", "--helm"}, description = "是否安装helm", required = true)
    boolean helm = true;

    @CommandLine.Option(names = {"-h", "--helm"}, description = "helm包", required = true)
    String helmTar;

    @CommandLine.Option(names = {"-c", "--calico"}, description = "是否安装calico", required = true)
    boolean calico = true;

    @CommandLine.Option(names = {"-c", "--calico"}, description = "calico包", required = true)
    String calicoTar;

    @CommandLine.Option(names = {"-c", "--ingress"}, description = "是否安装ingress", required = true)
    boolean ingress = true;

    @CommandLine.Option(names = {"-c", "--ingress"}, description = "ingress包", required = true)
    String ingressTar;

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @Override
    public String name() {
        return "安装k8s集群";
    }

    @Override
    public boolean doRun(Executor executor) {

        String sealosPath = String.format("%s/%s", packagePath, sealosTar);
        String kubernetesPath = String.format("%s/%s", packagePath, kubernetesTar);
        String helmPath = String.format("%s/%s", packagePath, helmTar);
        String calicoPath = String.format("%s/%s", packagePath, calicoTar);
        String ingressPath = String.format("%s/%s", packagePath, ingressTar);

        if (nodes.size() < 3) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "nodes节点不能少于3");
        }

        if (isKubernetesCluster()) {
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, sealosTar, sealosPath);
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, kubernetesTar, kubernetesPath);
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, helmTar, helmPath);
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, calicoTar, calicoPath);
            CliUtil.downRegistryFile(executor, enableRegistry, registryIp, registryPort, registryUsername, registryPassword, ingressTar, ingressPath);
        } else {
            log.info("k8s集群安装未开启，跳过");
            return true;
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
            String k8sCmd = String.format("/usr/bin/sealos run %s %s %s %s --masters %s --nodes %s",
                    kubernetesTar, helmTar, calicoTar, ingressTar, masters, nodes);
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

