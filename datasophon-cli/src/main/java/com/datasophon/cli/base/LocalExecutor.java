package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import java.nio.charset.Charset;
import java.util.List;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;

public class LocalExecutor implements Executor {
    
    @Override
    public ExecResult execShell(String cmd) {
        return ShellUtils.execShell(cmd);
    }
    
    @Override
    public ExecResult exists(String path) {
        ExecResult execResult = new ExecResult();
        execResult.setExecResult(FileUtil.exist(path));
        return execResult;
    }
    
    @Override
    public ExecResult sendFile(String src, String dest) {
        ExecResult execResult = new ExecResult();
        try {
            FileUtil.copy(src, dest, true);
            execResult.setExecResult(true);
        } catch (IORuntimeException exception) {
            execResult.setExecErrOut(exception.getMessage());
        }
        return execResult;
    }
    
    @Override
    public ExecResult sendDir(String srcDir, String destDir) {
        return null;
    }
    
    @Override
    public ExecResult createDir(String destDir) {
        return null;
    }
    
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
        String cpuArchitecture = ShellUtils.getCpuArchitecture();
        return ArchType.of(cpuArchitecture);
    }
    
    @Override
    public OsType getOs() {
        return ShellUtils.getOs();
    }
}
