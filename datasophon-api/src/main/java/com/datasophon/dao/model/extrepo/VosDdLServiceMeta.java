package com.datasophon.dao.model.extrepo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class VosDdLServiceMeta {
    
    private String ddl;
    
    private String name;
    
    private String version;
    
    private List<String> packageNames;
    
    private String frameCode;
    
    private List<String> dependencies;
    
}
