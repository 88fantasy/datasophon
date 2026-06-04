package com.datasophon.common.model;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class Host {
    
    private Long projectEnvDetailId;
    
    private String ip;
    
    private Integer port;
    
    private String user;
    
    private String password;
    
    private String hostname;
    
}
