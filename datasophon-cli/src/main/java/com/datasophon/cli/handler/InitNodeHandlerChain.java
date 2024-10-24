package com.datasophon.cli.handler;

import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.Host;
import com.datasophon.common.utils.JschUtils;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@Slf4j
public class InitNodeHandlerChain {
    
    private final Host host;

    private final SSHAuthType sshAuthType;
    
    private final List<InitNodeHandler> handlers = new ArrayList<>();

    public InitNodeHandlerChain(Host host, SSHAuthType sshAuthType) {
        this.host = host;
        this.sshAuthType = sshAuthType;
    }

    public InitNodeHandlerChain(Host host, SSHAuthType sshAuthType, List<InitNodeHandler> handlers) {
        this.host = host;
        this.sshAuthType = sshAuthType;
        this.handlers.addAll(handlers);
    }
    
    public InitNodeHandlerChain(Host host, SSHAuthType sshAuthType, InitNodeHandler handler) {
        this.host = host;
        this.sshAuthType = sshAuthType;
        this.handlers.add(handler);
    }
    
    public void addHandler(InitNodeHandler handler) {
        this.handlers.add(handler);
    }
    
    public void handle() {
        
        Session session = null;
        try {
            session = JschUtils.getJSchSession(sshAuthType, host.getIp(), host.getPort(), host.getUser(), host.getPassword());
            for (InitNodeHandler handler : handlers) {
                String name = handler.name();
                log.info("执行处理器开始[{}]", name);
                boolean handled = handler.handle(session);
                if (!handled) {
                    log.info("执行处理器失败[{}]", name);
                    break;
                }
                log.info("执行处理器结束[{}]", name);
            }
        } catch (JSchException e) {
            throw new RuntimeException(e);
        } finally {
            JschUtils.closeJSchSession(session);
        }
    }
}
