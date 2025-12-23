package com.datasophon.common.model;

import com.datasophon.common.model.GlobalConfig;
import com.datasophon.common.model.Host;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ClusterConfig {
    
    private GlobalConfig global;
    
    private List<Host> nodes = new ArrayList<>();
    
    private List<Host> addNodes = new ArrayList<>();
    
}
