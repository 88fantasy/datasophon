package com.datasophon.cli.base;


import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

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
}
