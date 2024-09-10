package com.datasophon.cli.handler;

import com.jcraft.jsch.Session;

public interface InitNodeHandler {
    
    String name();
    
    boolean handle(Session session);
}
