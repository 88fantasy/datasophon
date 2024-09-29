package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.jcraft.jsch.Session;

@Slf4j
public class JschExecutor implements Executor {

    private final Session session;

    public JschExecutor(Session session) {
        this.session = session;
    }

    @Override
    public ExecResult execShell(String cmd) {
        ExecResult execResult = new ExecResult();
        try {
            String result = JschUtils.shellForStr(session, cmd, 5, 3);
            execResult.setExecOut(result);
            execResult.setExecResult(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            execResult.setExecOut(e.getMessage());
            execResult.setExecErrOut(e.getMessage());
        }
        return execResult;
    }

    @Override
    public ExecResult exists(String path) {
        return null;
    }

    @Override
    public ExecResult sendFile(String src, String dest) {
        try (FileInputStream fis = new FileInputStream(src)) {
            return JschUtils.sendInputStream(session, fis, dest, 5, false);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public ExecResult getFileString(String path) {
        ExecResult execResult = new ExecResult();
        try {
            String string = JschUtils.getFileString(session, path, 5);
            execResult.setExecOut(string);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            execResult.setExecErrOut(e.getMessage());

        }
        return execResult;
    }

    @Override
    public ExecResult writeLines(List<String> lines, String path) {
        ByteArrayInputStream bais = new ByteArrayInputStream(String.join("\n", lines).getBytes());
        return JschUtils.sendInputStream(session, bais, path, 5, true);
    }

    @Override
    public ArchType getArch() {
        return JschUtils.getArch(session);
    }

    @Override
    public OsType getOs() {
        return JschUtils.getOs(session);
    }
}
