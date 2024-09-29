package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "os", description = "init os")
public class InitOsUser extends InitBase {
    
    private static final String GROUP = "hadoop";
    
    private static final String USER = "hadoop";
    
    @Override
    public boolean doRun(Executor executor) {
        ExecResult groupExec = executor.execShell(String.format("egrep '^%s' /etc/group >&/dev/null", GROUP));
        if (!groupExec.getExecResult()) {
            ExecResult groupAddExec = executor.execShell(String.format("groupadd %s", GROUP));
            if (groupAddExec.getExecResult()) {
                log.info("Successfully added GROUP: {}", GROUP);
            } else {
                log.error("create group {} failed", GROUP);
                return false;
            }
        }
        
        ExecResult userExec = executor.execShell(String.format("egrep \"^%s\" /etc/passwd >&/dev/null", USER));
        if (!userExec.getExecResult()) {
            ExecResult userAddExec = executor.execShell(String.format("useradd -g %s %s", USER, USER));
            if (userAddExec.getExecResult()) {
                log.info("Successfully added USER: {} PASSWD: {}", USER, USER);
            } else {
                log.error("create USER {} failed", USER);
                return false;
            }
        }
        
        log.info("init add hadoop user, Done.");
        
        // TODO 这里待定
        executor.execShell(String.format("mkdir -p /home/%s/", USER));
        executor.execShell(String.format("cp -r /root/.ssh /home/%s/", USER));
        executor.execShell(String.format("chown -R %s:%s /home/%s/.ssh/", USER, GROUP, USER));
        
        log.info("repair init ssh hadoop finished, Done.");
        return true;
    }
    
    @Override
    public String name() {
        return "初始化用户";
    }
}
