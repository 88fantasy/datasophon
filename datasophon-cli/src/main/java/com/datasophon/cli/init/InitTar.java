package com.datasophon.cli.init;

import com.datasophon.cli.base.Executor;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * 安装tar
 * TODO,默认已安装tar。废弃
 */
@Slf4j
@Data
@CommandLine.Command(name = "tar", description = "init tar")
public class InitTar extends InitBase {

    @CommandLine.Option(names = {"-pp", "--packagePath"}, description = "安装包目录", required = true)
    String packagePath;

    @Override
    public String name() {
        return "安装tar";
    }

    @Override
    public boolean doRun(Executor executor) {
        ExecResult exec = executor.execShell("which tar");
        if (!exec.getExecResult()) {
            log.info("start to install tar");
            exec = executor.execShell(String.format("rpm -ivh %s/tar-*.rpm", packagePath));
            if (exec.getExecResult()) {
                log.info("tar install success");
            } else {
                log.error("tar install failed");
                System.exit(-1);
            }
        } else {
            log.info("tar already installed");
        }
        return true;
    }
}
