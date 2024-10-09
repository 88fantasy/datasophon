package com.datasophon.cli.init;

import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.Executor;

import picocli.CommandLine;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * 初始化hostname
 */
@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "hostname", description = "init hostname")
public class InitHostname extends InitBase {
    
    @CommandLine.Option(arity = "1", names = {"-h", "--hostname"}, description = "hostname", required = true)
    private String hostname;
    
    @Override
    public String name() {
        return "初始化hostname";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        executor.execShell(String.format("echo %s >/etc/hostname", hostname));
        executor.execShell(String.format("echo HOSTNAME=%s >/etc/sysconfig/network", hostname));
        executor.execShell("echo NOZEROCONF=yes >>/etc/sysconfig/network");
        executor.execShell(String.format("hostnamectl set-hostname %s", hostname));
        executor.execShell(String.format("hostnamectl set-hostname --static %s", hostname));
        
        String host = executor.execShell("hostname").getExecOut();
        if (!host.equals(hostname)) {
            log.error("init hostname failed.");
            return false;
        }
        log.info("init hostname finished.");
        
        log.info("start to modify all host relation.");
        executor.execShell("sed -i '/#modify etc hosts start/,/#modify etc hosts end/d' /etc/hosts");
        executor.execShell("echo '#modify etc hosts start' >>/etc/hosts");
        ClusterConfig config = getConfig();
        config.getNodes().forEach(node -> {
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
