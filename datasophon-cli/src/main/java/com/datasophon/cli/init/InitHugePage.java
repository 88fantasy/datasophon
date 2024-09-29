package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;
import lombok.extern.slf4j.Slf4j;

/**
 * 关闭透明大页
 */
@Slf4j
@CommandLine.Command(name = "hugePage", description = "init hugePage")
public class InitHugePage extends InitBase {
    @Override
    public String name() {
        return "关闭透明大页";
    }
    
    @Override
    public boolean doRun(Executor executor) {
        log.info("start to close transparent_hugepage.");
        executor.execShell("echo never > /sys/kernel/mm/transparent_hugepage/enabled");
        executor.execShell("echo never > /sys/kernel/mm/transparent_hugepage/defrag");
        ExecResult exec = executor.execShell("egrep 'echo never > /sys/kernel/mm/transparent_hugepage/defrag' /etc/rc.d/rc.local >&/dev/null");
        if (!exec.getExecResult()) {
            executor.execShell("echo 'echo never > /sys/kernel/mm/transparent_hugepage/defrag' >>/etc/rc.d/rc.local");
            executor.execShell("echo 'echo never > /sys/kernel/mm/transparent_hugepage/enabled' >>/etc/rc.d/rc.local");
        }
        log.info("transparent_hugepage is closed.");
        log.info("init close transparent hugepage finished.");
        log.info("Done.");
        return true;
    }
}