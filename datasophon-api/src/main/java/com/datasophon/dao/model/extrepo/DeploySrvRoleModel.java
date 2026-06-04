package com.datasophon.dao.model.extrepo;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploySrvRoleModel {
    
    private String name;
    
    private List<String> deployHosts;
}
