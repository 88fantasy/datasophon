package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
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
@CommandLine.Command(name = "ntpserver", description = "init ntpserver")
public class InitNtpServer extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "ntpserver时钟配置";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        OsType osType = executor.getOs();
        String checkCmd = "rpm -qa | grep chrony";
        String installCmd = "yum -y install chrony";
        String chronyConfPath = "/etc/chrony.conf";
        String mvchronyConfCmd = "mv /etc/chrony.conf /etc/chrony.conf.$(date +%Y%m%d.%H%M%S)";
        String enableCmd = "systemctl enable chronyd";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep chrony";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install chrony -y";
            chronyConfPath = "/etc/chrony/chrony.conf";
            mvchronyConfCmd = "mv /etc/chrony/chrony.conf /etc/chrony/chrony.conf.$(date +%Y%m%d.%H%M%S)";
            enableCmd = "systemctl enable chrony";
        }

        executor.execShell(installCmd);
        ExecResult reResult = executor.execShell(checkCmd);
        if (!reResult.getExecResult()) {
            log.info("install chrony  fail.");
            return false;
        }
        executor.execShell(mvchronyConfCmd);

        List<String> conf = new ArrayList<>();
        conf.add("server 127.0.0.1 iburst");
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

        executor.execShell(enableCmd);
        if (OsType.isUnbuntu(osType)){
            executor.execShell("systemctl restart chronyd");
            executor.execShell("systemctl restart chrony");
        } else {
            executor.execShell("systemctl restart chronyd");
        }
        executor.execShell("chronyc sources");
        
        log.info("init ntpserver sucess.");
        return true;
    }
}
