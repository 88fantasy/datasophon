
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

    private KubernetesConfig kubernetes;

    private PackagesConfig packages;

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
    public static class KubernetesConfig {
        private Boolean enable;
        private BaseServices baseServices;
        private KuboardConfig kuboard;
        private DockerConfig docker;
        private HelmConfig helm;
    }

    @Data
    public static class BaseServices {
        private List<String> namespaces;
        private List<String> masters;
        private List<String> nodes;
        private SealosConfig sealos;
        private KubernetesBaseConfig kubernetes;
        private HelmBaseConfig helm;
        private CalicoConfig calico;
        private IngressConfig ingress;
    }

    @Data
    public static class SealosConfig {
        private Boolean enable;
    }

    @Data
    public static class KubernetesBaseConfig {
        private Boolean enable;
    }

    @Data
    public static class HelmBaseConfig {
        private Boolean enable;
    }

    @Data
    public static class CalicoConfig {
        private Boolean enable;
    }

    @Data
    public static class IngressConfig {
        private Boolean enable;
    }

    @Data
    public static class KuboardConfig {
        private Boolean enable;
        private List<String> nodes;
        private List<String> etcdNodes;
        private KuboardConfigDetail config;
    }

    @Data
    public static class KuboardConfigDetail {
        private String user;
        private String password;
    }

    @Data
    public static class DockerConfig {
        private Boolean enable;
        private List<String> nodes;
    }

    @Data
    public static class HelmConfig {
        private Boolean enable;
        private List<String> nodes;
    }

    @Data
    public static class PackagesConfig {
        private String os;
        private String config;
        private String soft;
        private Package nexus;
        private Package mysql;
        private Package rustfs;
        private Package sealos;
        private Package kubernetes;
        private Package helm;
        private Package calico;
        private Package ingress;
        private Package kuboard;
        private Package docker;
    }
}