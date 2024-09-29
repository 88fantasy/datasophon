package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "swap", description = "init swap")
public class InitSwap extends InitBase implements InitNodeHandler {
    
    @Override
    public String name() {
        return "关闭swap分区";
    }
    
    public boolean doRun(Executor executor) {
        ExecResult fstab = executor.execShell("sed -ri 's/.*swap.*/#&/' /etc/fstab");
        if (!fstab.getExecResult()) {
            return false;
        }
        ExecResult swappiness = executor.execShell("echo 0 >/proc/sys/vm/swappiness");
        if (!swappiness.getExecResult()) {
            return false;
        }
        ExecResult sysctlResult = executor.getFileString("/etc/sysctl.conf");
        if (sysctlResult.getExecResult()) {
            List<String> sysctlLines = Arrays.stream(sysctlResult.getExecOut().split("\n")).collect(Collectors.toList());
            if (sysctlLines.stream().noneMatch(s -> s.startsWith("vm.swappiness"))) {
                sysctlLines.add("vm.swappiness=0");
            } else {
                sysctlLines = sysctlLines.stream().map(s -> s.startsWith("vm.swappiness") ? "vm.swappiness=0" : s).collect(Collectors.toList());
            }
            executor.writeLines(sysctlLines, "/etc/sysctl.conf");
        }
        ExecResult set0 = executor.execShell("sysctl vm.swappiness=0");
        if (!set0.getExecResult()) {
            return false;
        }
        ExecResult swapOff = executor.execShell("swapoff -a && swapon -a");
        if (!swapOff.getExecResult()) {
            return false;
        }
        ExecResult load = executor.execShell("sysctl -p");
        if (!load.getExecResult()) {
            return false;
        }
        log.info("Swap is closed.");
        return true;
    }
}
