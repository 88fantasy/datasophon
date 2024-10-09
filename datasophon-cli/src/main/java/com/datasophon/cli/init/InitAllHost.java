package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * 初始化/etc/hosts
 */
@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "allHost", description = "init allHost")
public class InitAllHost extends InitBase{
    @Override
    public String name() {
        return "初始化hosts";
    }

    @Override
    public boolean doRun(Executor executor) {
        log.info("start to modify all host relation.");
        executor.execShell("sed -i '/#modify etc hosts start/,/#modify etc hosts end/d' /etc/hosts");
        executor.execShell("echo '#modify etc hosts start' >>/etc/hosts");
        ClusterConfig config = getConfig();
        config.getNodes().forEach(node -> {
            String ip = node.getIp();
            String hostname = node.getHostname();
            executor.execShell(String.format("echo %s %s >>/etc/hosts", ip, hostname));
        });
        config.getAddNodes().forEach(node -> {
            String ip = node.getIp();
            String hostname = node.getHostname();
            executor.execShell(String.format("echo %s %s >>/etc/hosts", ip, hostname));
        });

        executor.execShell("echo '#modify etc hosts end' >>/etc/hosts");
        executor.execShell("sed -i 's/^[^#].*[0-9]-[0-9]/#&/g' /etc/hosts");
        log.info("modify all host relation finished.");

        log.info("init source SSH hostname");
        executor.execShell("echo 'StrictHostKeyChecking no' >~/.ssh/config");
        return true;
    }
}
