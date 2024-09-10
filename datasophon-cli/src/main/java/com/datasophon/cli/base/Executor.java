package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;

import java.util.List;

public interface Executor {
    ExecResult execShell(String cmd);

    List<String> getFileLines(String path);

    void writeLines(List<String> lines, String path);

    ArchType getArch();

    OsType getOs();
}
