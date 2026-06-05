package com.datasophon.dao.model.extrepo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DeploySrvRoleModel {
    
    private String name;
    
    private List<String> deployHosts;
}
