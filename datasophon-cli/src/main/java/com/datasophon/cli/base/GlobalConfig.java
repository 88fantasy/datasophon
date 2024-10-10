package com.datasophon.cli.base;

import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.model.Host;

import java.util.List;

import lombok.Data;

@Data
public class GlobalConfig {
    
    private boolean offline;
    
    private OsType os;
    
    private ArchType arch;
    
    private RegistryConfig registry;
    
    private Host nmapServer;
    
    private MysqlConfig mysql;
    
    private HttpdServer httpdServer;
    
    private NtpServer ntpServer;
    
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
        
        private String tarName;
        
        private List<MysqlAppDb> appDbs;
        
        private Host host;
    }
    
    @Data
    public static class MysqlAppDb {
        private String account;
        
        private String password;
        
        private String dbName;
    }
    
    @Data
    public static class HttpdServer {
        private Host host;
        
        private String pkgTarName;
        
        private String rootPathName;
        
        private String reposTarName;
        
        private String listenPort;
        
        private boolean force;
    }
    
    @Data
    public static class NtpServer {
        private Host host;
    }
}
