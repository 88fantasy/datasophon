package com.datasophon.cli.base;


import com.datasophon.common.utils.ExecResult;

public interface Executor {

    ExecResult execShell(String cmd);

}
