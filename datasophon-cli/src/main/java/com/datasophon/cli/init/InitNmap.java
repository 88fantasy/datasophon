package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
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
        log.info("install nmap.");
        ExecResult nmapExec = executor.execShell("rpm -qa | grep nmap");
        if (!nmapExec.getExecResult()) {
            log.info("nmap not installed");
            executor.execShell("yum install nmap -y");
            nmapExec = executor.execShell("rpm -qa | grep nmap");
            if (!"0".equals(nmapExec.getExecOut())) {
                log.info("nmap install failed.");
                System.exit(1);
            }
        }
        log.info("install nmap finished.");
        return true;
    }
}
