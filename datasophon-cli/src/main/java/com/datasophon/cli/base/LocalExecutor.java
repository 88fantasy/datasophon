package com.datasophon.cli.base;

import cn.hutool.core.io.FileUtil;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

public class LocalExecutor implements Executor {

    @Override
    public ExecResult execShell(String cmd) {
        return ShellUtils.execShell(cmd);
    }

    @Override
    public List<String> getFileLines(String path) {
        return FileUtil.readLines(path, Charset.defaultCharset());
    }

    @Override
    public void writeLines(List<String> lines, String path) {
        FileUtil.writeLines(lines, path, Charset.defaultCharset());
    }

    @Override
    public ArchType getArch() {
        String cpuArchitecture = ShellUtils.getCpuArchitecture();
        return ArchType.of(cpuArchitecture);
    }

    @Override
    public OsType getOs() {
        return ShellUtils.getOs();
    }
}
