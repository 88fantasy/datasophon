package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.Host;

import lombok.Data;

@Data
public class GlobalConfig {
    
    private boolean offline;
    
    private OsType os;
    
    private ArchType arch;
    
    private RegistryConfig registry;
    
    private Host nmapServer;
    
    private String ntpServer;
    
    private MysqlConfig mysql;
    
    private String logDir;
    
    @Data
    public static class RegistryConfig {
        
        private Boolean enable;
        
        private String type;
        
        private Object config;
        
        private Host host;
    }
    
    @Data
    public static class MysqlConfig {
        
        private Boolean enable;
        
        private String password;
        
        private Host host;
    }
}
