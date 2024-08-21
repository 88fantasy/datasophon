package com.datasophon.cli.base;

import lombok.Data;

@Data
public class GlobalConfig {
    
    private boolean offline;
    
    private OS os;
    
    private Host nmapServer;
    
    private String ntpServer;
    
    private MysqlConfig mysql;
    
    private String logDir;
    
    @Data
    public static class MysqlConfig {
        
        private Boolean enable;
        
        private String password;
        
        private Host host;
    }
}
