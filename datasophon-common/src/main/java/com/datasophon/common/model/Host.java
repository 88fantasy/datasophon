package com.datasophon.common.model;

import lombok.Data;

@Data
public class Host {
    
    private String ip;
    
    private Integer port;
    
    private String user;
    
    private String password;
    
    private String hostname;
    
}
