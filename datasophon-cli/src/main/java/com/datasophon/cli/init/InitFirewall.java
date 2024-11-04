package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.OsType;
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
        OsType osType = executor.getOs();
        String statusCmd = "systemctl status firewalld";
        String stopCmd = "systemctl stop firewalld";
        String disCmd = "systemctl disable firewalld";
        if(OsType.isUnbuntu(osType)) {
            statusCmd = "systemctl status ufw";
            stopCmd = "systemctl stop ufw";
            disCmd = "systemctl disable ufw";
        }
        ExecResult exec = executor.execShell(statusCmd);
        if (exec.getExecResult()) {
            ExecResult stopResult = executor.execShell(stopCmd);
            if (stopResult.getExecResult()) {
                ExecResult disableResult = executor.execShell(disCmd);
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
    }
}
