package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "ntpslave", description = "init ntpslave")
public class InitNtpSlave extends InitBase implements InitNodeHandler {
    
    @CommandLine.Option(names = {"-ip", "--ntpServerIp"}, description = "ntpServer Ip", required = true)
    String ntpServerIp;
    
    @Override
    public String name() {
        return "ntp slave配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        String cmd = "rpm -qa | grep chrony-";
        System.out.println(cmd);
        ExecResult reResult = executor.execShell(cmd);
        if (reResult.getExecResult()) {
            log.info("ntp-chrony exists");
        } else {
            cmd = "yum -y install chrony";
            System.out.println(cmd);
            executor.execShell(cmd);
        }
        
        cmd = "mv /etc/chrony.conf /etc/chrony.conf.$(date +%Y%m%d.%H%M%S)";
        System.out.println(cmd);
        executor.execShell(cmd);
        
        String chronyConfPath = "/etc/chrony.conf";
        List<String> conf = new ArrayList<>();
        conf.add(String.format("server %s iburst", ntpServerIp));
        conf.add("driftfile /var/lib/chrony/drift");
        conf.add("makestep 1.0 3");
        conf.add("rtcsync");
        conf.add("allow all");
        conf.add("local stratum 10");
        conf.add("keyfile /etc/chrony.keys");
        conf.add("leapsectz right/UTC");
        conf.add("logdir /var/log/chrony");
        executor.writeLines(conf, chronyConfPath);
        log.info("/etc/chrony.conf overwrite sucess.");
        
        cmd = "systemctl enable chronyd";
        System.out.println(cmd);
        executor.execShell(cmd);
        
        cmd = "systemctl restart chronyd";
        System.out.println(cmd);
        executor.execShell(cmd);
        
        log.info("init ntpSlave sucess.");
        return true;
    }
}
