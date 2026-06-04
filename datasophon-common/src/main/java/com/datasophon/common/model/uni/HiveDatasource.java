package com.datasophon.common.model.uni;

import java.io.Serializable;

import lombok.Data;

import com.alibaba.fastjson2.JSONObject;

@Data
public class HiveDatasource implements Serializable {
    
    private String userName;
    
    private String port;
    
    private String password;
    
    private String host;
    
    private JSONObject other;
    
    private String defaultFs;
    
    private String hadoopConfig;
    
    private int isKerberos;
    
    private String thriftUrls;
    
    private String warehouse;
    
    private String authentication;
}
