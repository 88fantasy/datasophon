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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "docker", description = "init docker")
public class InitDocker extends InitBase implements InitNodeHandler {

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

    @CommandLine.Option(names = {"-dp", "--dockerHttpPort"}, description = "http端口", required = true)
    Integer dockerHttpPort;
    
    @Override
    public String name() {
        return "安装docker";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        if (!enableKubernetesCluster) {
            log.info("k8s集群安装未开启，跳过");
            return true;
        }

        Boolean isInstalled;
        isInstalled = executor.execShell("docker version").getExecResult();
        if(isInstalled) {
            log.info("docker is installed");
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
        String softPath = String.format("%s/docker", installPath);
        executor.execShell(String.format("mkdir -p %s", softPath));
        executor.execShell(String.format("tar -xvf %s -C %s", tarPath, softPath));
        executor.execShell(String.format("cp -rf %s/docker/* /usr/bin/", softPath));

        executor.writeLines(getDockerServiceConf(), "/etc/systemd/system/docker.service");
        executor.execShell("chmod +x /etc/systemd/system/docker.service");

        executor.writeLines(getDaemonConf(), "/etc/docker/daemon.json");
        String base64 = executor.execShell(String.format("echo -n '%s:%s' | base64", registryUsername, registryPassword)).getExecOut();
        executor.writeLines(getAuthsConf(base64), "/root/.docker/config.json");

        executor.execShell("systemctl daemon-reload");
        executor.execShell("systemctl restart docker");
        executor.execShell("systemctl enable docker.service");
        log.info("docker install success");
        return true;
    }

    private List<String> getDaemonConf(){
        List<String> conf = new ArrayList<>();
        conf.add("{");
        conf.add(String.format("\"insecure-registries\": [\"%s:%s\"]", registryIp, dockerHttpPort));
        conf.add("}");
        return conf;
    }

    private List<String> getAuthsConf(String auths){
        List<String> conf = new ArrayList<>();
        conf.add("{");
        conf.add(String.format("\"auths\": {\"http://%s:%s\": {\"auth\": \"%s\"}}", registryIp, dockerHttpPort, auths));
        conf.add("}");
        return conf;
    }

    private List<String> getDockerServiceConf(){
        List<String> conf = new ArrayList<>();
        conf.add("[Unit]");
        conf.add("Description=Docker Application Container Engine");
        conf.add("Documentation=https://docs.docker.com");
        conf.add("After=network-online.target firewalld.service");
        conf.add("Wants=network-online.target");
        conf.add("");
        conf.add("[Service]");
        conf.add("Type=notify");
        conf.add("# the default is not to use systemd for cgroups because the delegate issues still");
        conf.add("# exists and systemd currently does not support the cgroup feature set required");
        conf.add("# for containers run by docker");
        conf.add("ExecStart=/usr/bin/dockerd");
        conf.add("ExecReload=/bin/kill -s HUP $MAINPID");
        conf.add("# Having non-zero Limit*s causes performance problems due to accounting overhead");
        conf.add("# in the kernel. We recommend using cgroups to do container-local accounting.");
        conf.add("LimitNOFILE=infinity");
        conf.add("LimitNPROC=infinity");
        conf.add("LimitCORE=infinity");
        conf.add("# Uncomment TasksMax if your systemd version supports it.");
        conf.add("# Only systemd 226 and above support this version.");
        conf.add("#TasksMax=infinity");
        conf.add("TimeoutStartSec=0");
        conf.add("# set delegate yes so that systemd does not reset the cgroups of docker containers");
        conf.add("Delegate=yes");
        conf.add("# kill only the docker process, not all processes in the cgroup");
        conf.add("KillMode=process");
        conf.add("# restart the docker process if it exits prematurely");
        conf.add("Restart=on-failure");
        conf.add("StartLimitBurst=3");
        conf.add("StartLimitInterval=60s");
        conf.add("");
        conf.add("[Install]");
        conf.add("WantedBy=multi-user.target");
        return conf;
    }

}
