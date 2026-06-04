package com.datasophon.dao.model;

import lombok.Data;

@Data
public class WebuisVO {
    
    private Integer id;
    /**
     * 服务角色id
     */
    private Integer serviceRoleInstanceId;
    /**
     * URL地址
     */
    private String webUrl;
    
    private Integer serviceInstanceId;
    
    private String name;
    
    private String ip;
}
