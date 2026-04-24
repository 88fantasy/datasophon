package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "k8sRegistryConf", description = "init k8sRegistryConf")
public class InitK8sRegistryConf extends InitBase implements InitNodeHandler {

    @CommandLine.Option(names = {"-kc", "--enableKubernetesCluster"}, description = "是否安装kubernetes集群")
    boolean enableKubernetesCluster = true;

    @CommandLine.Option(names = {"-dp", "--dockerHttpPort"}, description = "http端口", required = true)
    Integer dockerHttpPort;
    
    @Override
    public String name() {
        return "初始化私有仓库nexus配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        if (!enableKubernetesCluster) {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }

        String certsdPath = "/etc/containerd/certs.d";
        String configTomlPath = "/etc/containerd/config.toml";
        String hostPort = String.format("%s:%s", registryIp, dockerHttpPort);
        String hostPortDir = String.format("%s/%s", certsdPath, hostPort);
        String hostPortFilePath = String.format("%s/hosts.toml", certsdPath);

        if(!executor.exists(certsdPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + certsdPath);
        }
        if(!executor.exists(configTomlPath).getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "file not found : " + configTomlPath);
        }

        executor.execShell(String.format("mkdir -p %s", hostPortDir));
        executor.writeLines(getCertsConf(), hostPortFilePath);
        executor.writeLines(getContainerdConf(), configTomlPath);


        executor.execShell("systemctl restart containerd");
        log.info("k8sRegistryConf init success");
        return true;
    }

    private List<String> getCertsConf(){
        List<String> conf = new ArrayList<>();
        conf.add(String.format("server = \"http://%s:%s\"", registryIp, dockerHttpPort));
        conf.add(String.format("[host.\"http://%s:%s\"]", registryIp, dockerHttpPort));
        conf.add("  capabilities = [\"pull\", \"resolve\", \"push\"]");
        conf.add("  skip_verify = true");
        return conf;
    }

    private List<String> getContainerdConf(){
        List<String> conf = new ArrayList<>();
        conf.add(String.format("         [plugins.\"io.containerd.grpc.v1.cri\".registry.configs.\"%s:%s\".auth]", registryIp, dockerHttpPort));
        conf.add(String.format("          username = \"%s\"", registryUsername));
        conf.add(String.format("          password = \"%s\"", registryPassword));
        return conf;
    }

}
