package com.datasophon.cli.base;

import com.datasophon.common.model.Host;

import java.util.List;

import lombok.Data;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private List<Host> nodes;
    
}
