package com.datasophon.dao.model.extrepo;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploySrvModel implements ServiceResource<DeploySrvModel>{

    private String name;

    private String version;

    private String type;

    private String desc;

    private List<DeploySrvRoleModel> roles;

//    private List<DeploySrvConfig> config;

}
