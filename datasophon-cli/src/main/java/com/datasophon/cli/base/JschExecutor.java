package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
    public List<String> getFileLines(String path) {
        try {
            return JschUtils.getFileLines(session, path, 5);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeLines(List<String> lines, String path) {

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
