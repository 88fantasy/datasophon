package com.datasophon.cli.base;

import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.Host;
import lombok.Data;

import java.util.List;

@Data
public class GlobalConfig {
    
    private boolean offline;

    private SSHAuthType sshAuthType;
    
    private RegistryConfig registry;
    
    private Host nmapServer;
    
    private MysqlConfig mysql;
    
    private YumServer yumServer;
    
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
    public static class YumServer {
        private Host host;
        
        private String listenPort;
    }
    
    @Data
    public static class NtpServer {
        private Host host;
    }
}
