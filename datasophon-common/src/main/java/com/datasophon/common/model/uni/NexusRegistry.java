package com.datasophon.common.model.uni;

import java.util.List;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class NexusRegistry {
    
    private boolean enable;
    
    private String type;
    
    private Config config;
    
    private String node;
    
    @Data
    @Getter
    public static class Config {
        
        private String webPort;
        
        private String user;
        
        private String password;
        
        private Integer dockerHttpPort;
        
        private List<String> repositories;
    }
    
}
