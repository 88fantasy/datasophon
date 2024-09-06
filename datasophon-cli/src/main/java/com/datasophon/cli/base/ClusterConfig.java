package com.datasophon.cli.base;

import java.util.Collection;

import com.datasophon.common.model.Host;
import lombok.Data;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private Collection<Host> nodes;
    
}
