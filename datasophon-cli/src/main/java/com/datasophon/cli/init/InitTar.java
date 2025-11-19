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
 * TODO,默认已安装tar。废弃。在线安装
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
        if(!exec.getExecResult()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "tar command not found. 请手动安装");
        }
        return true;
    }
}
