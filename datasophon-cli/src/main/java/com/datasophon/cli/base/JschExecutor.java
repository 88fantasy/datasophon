package com.datasophon.cli.base;

import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.JschUtils;
import com.datasophon.common.utils.OsUtils;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
public class JschExecutor implements Executor {
    
    private final Session session;
    
    public JschExecutor(Session session) {
        this.session = session;
    }
    
    @Override
    public ExecResult execShell(String cmd) {
        log.info("command:{}", cmd);
        ExecResult execResult = new ExecResult();
        try {
            return JschUtils.execForStr(session, cmd);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            execResult.setExecOut(e.getMessage());
            execResult.setExecErrOut(e.getMessage());
        }
        return execResult;
    }
    
    @Override
    public ExecResult exists(String path) {
        return JschUtils.exists(session, path, 5);
    }
    
    @Override
    public ExecResult sendFile(String src, String dest, boolean override) {
        try (FileInputStream fis = new FileInputStream(src)) {
            return JschUtils.sendInputStream(session, fis, dest, 5, override);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public ExecResult sendDir(String srcDir, String destDir, boolean isVisual) {
        return JschUtils.sendDir(session, srcDir, destDir, 5, isVisual);
    }
    
    @Override
    public ExecResult createDir(String destDir) {
        return JschUtils.createDir(session, destDir, 5);
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
        String result = JschUtils.execForStr(session, Constants.OS_ARCH_CMD).getExecOut();
        return OsUtils.getArch(result);
    }
    
    @Override
    public OsType getOs() {
        String result = JschUtils.execForStr(session, Constants.OS_VERSION_CMD).getExecOut();
        return OsUtils.getOs(result);
    }
}
