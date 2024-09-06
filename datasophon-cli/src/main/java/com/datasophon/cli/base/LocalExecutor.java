package com.datasophon.cli.base;


import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

public class LocalExecutor implements Executor {

    @Override
    public ExecResult execShell(String cmd) {
        return ShellUtils.execShell(cmd);
    }
}
