package com.datasophon.dao.model.extrepo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sDdLServiceMeta {
    
    private String frameCode;
    
    private String manifest;
    
    private String name;
    
    private String version;
    
    private List<String> dependencies;
    
    private List<String> charts = new ArrayList<>();
    
}
