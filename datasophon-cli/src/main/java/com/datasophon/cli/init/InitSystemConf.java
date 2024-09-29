package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.cli.handler.InitNodeHandler;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;

import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CommandLine.Command(name = "system-conf", description = "init system config")
public class InitSystemConf extends InitBase implements InitNodeHandler {

    @Override
    public String name() {
        return "设置操作系统配置";
    }

    public boolean doRun(Executor executor) {
        ExecResult systemConfResult = executor.getFileString("/etc/systemd/system.conf");
        if (systemConfResult.getExecResult()) {
            List<String> systemConf = Arrays.stream(systemConfResult.getExecOut().split("\n")).collect(Collectors.toList());
            systemConf.removeIf(s -> s.contains("DefaultLimitNOFILE"));
            systemConf.removeIf(s -> s.contains("DefaultLimitNPROC"));
            systemConf.add("DefaultLimitNOFILE=1024000");
            systemConf.add("DefaultLimitNPROC=1024000");
            executor.writeLines(systemConf, "/etc/systemd/system.conf");
        }

        ExecResult limitsConfResult = executor.getFileString("/etc/security/limits.conf");
        if (limitsConfResult.getExecResult()) {
            List<String> limitsConf = Arrays.stream(limitsConfResult.getExecOut().split("\n")).collect(Collectors.toList());
            limitsConf.removeIf(s -> s.contains("soft    fsize"));
            limitsConf.removeIf(s -> s.contains("hard    fsize"));
            limitsConf.removeIf(s -> s.contains("soft    cpu"));
            limitsConf.removeIf(s -> s.contains("hard    cpu"));
            limitsConf.removeIf(s -> s.contains("soft    as"));
            limitsConf.removeIf(s -> s.contains("hard    as"));
            limitsConf.removeIf(s -> s.contains("soft    nofile"));
            limitsConf.removeIf(s -> s.contains("hard    nofile"));
            limitsConf.removeIf(s -> s.contains("soft    nproc"));
            limitsConf.removeIf(s -> s.contains("hard    nproc"));

            limitsConf.add("*            soft    fsize           unlimited");
            limitsConf.add("*            hard    fsize           unlimited");
            limitsConf.add("*            soft    cpu             unlimited");
            limitsConf.add("*            hard    cpu             unlimited");
            limitsConf.add("*            soft    as              unlimited");
            limitsConf.add("*            hard    as              unlimited");
            limitsConf.add("*            soft    nofile          1048576");
            limitsConf.add("*            hard    nofile          1048576");
            limitsConf.add("*            soft    nproc           unlimited");
            limitsConf.add("*            hard    nproc           unlimited");

            executor.writeLines(limitsConf, "/etc/security/limits.conf");
        }


        OsType os = executor.getOs();
        if (OsType.CentOS7 == os) {
            List<String> limits = Arrays.asList(
                    "*          soft    nproc     unlimited",
                    "root       soft    nproc     unlimited");
            executor.writeLines(limits, "/etc/security/limits.conf");
        }


        ExecResult sysctlConfResult = executor.getFileString("/etc/sysctl.conf");
        if (sysctlConfResult.getExecResult()) {
            List<String> sysctlConf = Arrays.stream(sysctlConfResult.getExecOut().split("\n")).collect(Collectors.toList());
            sysctlConf.removeIf(s -> s.contains("kernel.pid_max"));
            sysctlConf.add("kernel.pid_max=1000000");
            executor.writeLines(sysctlConf, "/etc/sysctl.conf");
        }
        ExecResult load = executor.execShell("sysctl -p");
        if (!load.getExecResult()) {
            return false;
        }
        log.info("system is configured.");
        return true;
    }
}
