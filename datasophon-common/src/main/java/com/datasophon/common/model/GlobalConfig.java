
package com.datasophon.common.model;

import com.datasophon.common.enums.SSHAuthType;
import com.datasophon.common.model.uni.NexusRegistry;
import com.datasophon.common.model.uni.Package;
import com.datasophon.common.model.uni.Rustfs;
import lombok.Data;

import java.util.List;

@Data
public class GlobalConfig {

    private Long projectEnvId;


    private boolean offline;

    private OsInfo osInfo;

    private SSHAuthType sshAuthType;

    private NexusRegistry registry;

    private Rustfs rustfs;

    private NmapServer nmapServer;

    private MysqlConfig mysql;

    private YumServer yumServer;

    private NtpServer ntpServer;

    private Kubernetes kubernetes;

    private Packages packages;

    @Data
    public static class MysqlConfig {

        private Boolean enable;

        private String user;

        private String password;

        private Integer port;

        private Package packages;

        private List<MysqlAppDb> appDbs;

        private String node;
    }

    @Data
    public static class MysqlAppDb {
        private String account;

        private String password;

        private String dbName;
    }

    @Data
    public static class YumServer {
        private Boolean enable;
        private String node;

        private String listenPort;
    }

    @Data
    public static class NtpServer {
        private Boolean enable;
        private String node;
    }

    @Data
    public static class NmapServer {
        private Boolean enable;
        private String node;
    }

    @Data
    public static class OsInfo {
        private boolean auto;
        private String osType;
        private String archType;
    }

    @Data
    public static class Kubernetes {
        private Boolean enable;
        public BaseServices baseServices;
        public Kuboard kuboardI;
        public K8sTools k8sTools;
    }

    @Data
    public static class BaseServices {
        private List<String> namespaces;
        private List<String> masters;
        private List<String> nodes;
        private Enable sealos;
        private Enable kubernetesI;
        private Enable helmI;
        private Enable calicoI;
        private Enable ingressI;
    }

    @Data
    public static class Enable {
        private Boolean enable;
    }

    @Data
    public static class Kuboard {
        private Boolean enable;
        private List<String> nodes;
        private List<String> etcdNodes;
        private KuboardConfig config;
    }

    @Data
    public static class KuboardConfig {
        private String user;
        private String password;
    }

    @Data
    public static class K8sTools {
        private boolean docker;
        private boolean helm;
        private boolean helmify;
        private boolean kubectl;
    }

    @Data
    public static class Packages {
        private String os;
        private String config;
        private String soft;
        private Package nexus;
        private Package mysql;
        private Package rustfs;
        private Package sealos;
        private Package kubernetesI;
        private Package helmI;
        private Package calicoI;
        private Package ingressI;
        private Package kuboardI;
        private Package helmify;
        private Package docker;
        private Package helm;
        private Package kubectl;
    }
}