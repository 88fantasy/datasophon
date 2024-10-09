package com.datasophon.cli.base;

import com.datasophon.common.model.Host;
import lombok.Data;

import java.util.List;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private List<Host> nodes;

    private List<Host> addNodes;
    
}
