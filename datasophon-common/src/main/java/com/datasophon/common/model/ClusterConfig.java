package com.datasophon.common.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private List<Host> nodes = new ArrayList<>();
    
    private List<Host> addNodes = new ArrayList<>();
    
}
