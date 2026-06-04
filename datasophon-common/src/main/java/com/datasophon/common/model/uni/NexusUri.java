package com.datasophon.common.model.uni;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class NexusUri {
    
    private boolean enabled;
    
    private String user;
    
    private String password;
    
    private String uri;
    
    private String ip;
    
    private Integer port;
    
}
