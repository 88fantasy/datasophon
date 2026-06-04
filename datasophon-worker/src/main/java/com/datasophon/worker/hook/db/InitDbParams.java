package com.datasophon.worker.hook.db;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class InitDbParams {
    
    private String url;
    
    private String driver;
    
    private String username;
    
    private String password;
    
    private String resourceKey;
    
    private String scriptPath;
    
    private MetaStorage metaStorage;
    
    private String ddlPattern;
    
    private String dmlPattern;
    
    private String rollbackPattern;
    
}
