package com.datasophon.cli.base;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public class LocalExecutor implements Executor {

    @Override
    public ExecResult execShell(String cmd) {
        return ShellUtils.execShell(cmd);
    }

    // TODO交互式输入，尚未完善
    @Override
    public ExecResult execShellExp(String cmd, Map<String, String> expects) {
        return null;
    }

    @Override
    public ExecResult exists(String path) {
        ExecResult execResult = new ExecResult();
        execResult.setExecResult(FileUtil.exist(path));
        return execResult;
    }

    @Override
    public ExecResult sendFile(String src, String dest, boolean override) {
        ExecResult execResult = new ExecResult();
        try {
            FileUtil.copy(src, dest, override);
            execResult.setExecResult(true);
        } catch (IORuntimeException exception) {
            execResult.setExecErrOut(exception.getMessage());
        }
        return execResult;
    }

    @Override
    public ExecResult sendDir(String srcDir, String destDir, boolean isVisual) {
        return null;
    }

    /*@Override
    public ExecResult createDir(String destDir) {
        return ShellUtils.execShell(String.format("mkdir -p %s", destDir));
    }*/

    @Override
    public ExecResult getFileString(String path) {
        ExecResult execResult = new ExecResult();
        try {
            String string = FileUtil.readString(path, Charset.defaultCharset());
            execResult.setExecOut(string);
            execResult.setExecResult(true);
        } catch (IORuntimeException exception) {
            execResult.setExecErrOut(exception.getMessage());
        }
        return execResult;
    }

    @Override
    public ExecResult writeFromStream(InputStream in, String path) {
        ExecResult execResult = new ExecResult();
        try {
            FileUtil.writeFromStream(in, path);
            execResult.setExecResult(true);
        } catch (IORuntimeException exception) {
            execResult.setExecErrOut(exception.getMessage());
        }
        return execResult;
    }

    @Override
    public ExecResult writeLines(List<String> lines, String path) {
        ExecResult execResult = new ExecResult();
        try {
            FileUtil.writeLines(lines, path, Charset.defaultCharset());
            execResult.setExecResult(true);
        } catch (IORuntimeException exception) {
            execResult.setExecErrOut(exception.getMessage());
        }
        return execResult;
    }

    @Override
    public ArchType getArch() {
        return ShellUtils.getArch();
    }

    @Override
    public OsType getOs() {
        return ShellUtils.getOs();
    }
}
