package com.datasophon.common.model.uni;

import java.io.Serializable;

import lombok.Data;

import com.alibaba.fastjson2.JSONObject;

@Data
public class KafkaDatasource implements Serializable {
    
    private String userName;
    
    private String password;
    
    private String host;
    
    private JSONObject other;
    
    private int isKerberos;
    
    private String bootstrapServers;
    
    private String securityProtocol;
}
