package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Accessors(chain = true)
@Data
@CommandLine.Command(name = "nmap", description = "init nmap")
public class InitNmap extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "nmap安装";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        OsType osType = executor.getOs();
        log.info("install nmap.");
        String checkCmd = "rpm -qa | grep nmap";
        String installCmd = "yum install nmap -y";
        if(OsType.isUnbuntu(osType)) {
            checkCmd = "dpkg --list|grep nmap";
            installCmd = "DEBIAN_FRONTEND=noninteractive apt install nmap -y";
        }
        ExecResult nmapExec = executor.execShell(checkCmd);
        if (!nmapExec.getExecResult()) {
            log.info("nmap not installed");
            executor.execShell(installCmd);
            nmapExec = executor.execShell(checkCmd);
            if (!nmapExec.getExecResult()) {
                log.info("nmap install failed.");
                System.exit(1);
            }
        }
        log.info("install nmap finished.");
        return true;
    }
}
