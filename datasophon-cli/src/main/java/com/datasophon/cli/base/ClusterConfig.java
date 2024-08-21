package com.datasophon.cli.base;

import java.util.Collection;

import lombok.Data;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private Collection<Host> nodes;
    
}
