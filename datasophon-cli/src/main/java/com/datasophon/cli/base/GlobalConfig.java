package com.datasophon.cli.base;

import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.Host;
import com.datasophon.common.model.uni.NexusRegistry;
import com.datasophon.common.model.uni.Rustfs;
import lombok.Data;

import java.util.List;

@Data
public class GlobalConfig {
    
    private boolean offline;

    private OsInfo osInfo;

    private SSHAuthType sshAuthType;
    
    private NexusRegistry registry;

    private Rustfs rustfs;
    
    private NmapServer nmapServer;
    
    private MysqlConfig mysql;
    
    private YumServer yumServer;
    
    private NtpServer ntpServer;

    @Data
    public static class MysqlConfig {
        
        private Boolean enable;

        private String password;

        private Integer port;

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

    @Data
    public static class NmapServer {
        private Host host;
    }

    @Data
    public static class OsInfo {
        private boolean isAuto;
        private String osType;
        private String archType;
    }
}
