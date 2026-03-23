package com.datasophon.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private List<Host> nodes = new ArrayList<>();
    
    private List<Host> addNodes = new ArrayList<>();
    
}
