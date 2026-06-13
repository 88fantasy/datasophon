package com.datasophon.dao.model.extrepo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class PhysicalDdlServiceMeta {
    
    private String ddl;
    
    private String name;
    
    private String version;
    
    private List<String> packageNames;
    
    private String frameCode;
    
    private List<String> dependencies;
    
}
