package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "selinux", description = "stop selinux")
public class InitSelinux extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "关闭安全策略";
    }

    @Override
    public boolean doRun(Executor executor) {
        ExecResult exec = executor.execShell("getenforce");
        if (exec.getExecResult()) {
            String state = exec.getExecOut();
            if ("Enforcing".equals(state)) {
                log.info("Disabling SELINUX.");
                ExecResult stopResult = executor.execShell("setenforce 0");
                if (stopResult.getExecResult()) {
                    ExecResult disableResult = executor.execShell("sed -i \"s/SELINUX=enforcing/SELINUX=disabled/g\" /etc/selinux/config");
                    if (!disableResult.getExecResult()) {
                        log.info("SELINUX close failed.");
                    } else {
                        return true;
                    }
                } else {
                    log.info("SELINUX disable failed.");
                }
            }
            log.info("SELINUX closed.");
            return true;
        } else {
            return false;
        }
    }
    
}
