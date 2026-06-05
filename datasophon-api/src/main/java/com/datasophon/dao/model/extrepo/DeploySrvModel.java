package com.datasophon.dao.model.extrepo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DeploySrvModel implements ServiceResource<DeploySrvModel> {
    
    private String name;
    
    private String version;
    
    private String type;
    
    private String desc;
    
    private String deployType;
    
    private String metaFileType;
    
    private String namespace;
    
    private List<DeploySrvRoleModel> roles;
    
}
