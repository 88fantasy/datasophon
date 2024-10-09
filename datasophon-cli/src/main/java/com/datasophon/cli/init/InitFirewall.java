package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "firewall", description = "init firewall")
public class InitFirewall extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "防火墙策略";
    }

    @Override
    public boolean doRun(Executor executor) {
        ExecResult exec = executor.execShell("firewall-cmd --state");
        if (exec.getExecResult()) {
            String state = exec.getExecOut();
            if ("running".equals(state)) {
                log.info("Closing firewall.");
                ExecResult stopResult = executor.execShell("systemctl stop firewalld.service");
                if (stopResult.getExecResult()) {
                    ExecResult disableResult = executor.execShell("systemctl disable firewalld.service");
                    if (!disableResult.getExecResult()) {
                        log.info("Firewall disable failed.");
                        return false;
                    }
                } else {
                    log.info("Firewall stop failed.");
                    return false;
                }
            }
            log.info("Firewall closed.");
            return true;
        } else {
            return false;
        }
    }
}
