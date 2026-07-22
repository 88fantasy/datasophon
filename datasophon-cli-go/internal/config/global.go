package config

// SSHAuthType 对应 Java SSHAuthType 枚举。
type SSHAuthType string

const (
	SSHAuthTypePublicKey SSHAuthType = "PUBLICKEY"
	SSHAuthTypePassword  SSHAuthType = "PASSWORD"
	SSHAuthTypeAuto      SSHAuthType = "AUTO"
)

// GlobalConfig 对应 Java GlobalConfig，仅保留节点级上下文字段。
type GlobalConfig struct {
	ClusterType ClusterType `yaml:"cluster-type"`
	Offline     bool        `yaml:"offline"`
	OsInfo      OsInfo      `yaml:"osInfo"`
	SSHAuthType SSHAuthType `yaml:"sshAuthType"`
}

type OsInfo struct {
	Auto     bool   `yaml:"auto"`
	OsType   string `yaml:"osType"`
	ArchType string `yaml:"archType"`
}

// Registry 对应 Java NexusRegistry。
type Registry struct {
	Enable        bool           `yaml:"enable"`
	DisableUpload bool           `yaml:"disableUpload"` // 承接 --disableUploadRegistry
	Type          string         `yaml:"type"`
	Config        RegistryConfig `yaml:"config"`
	Node          string         `yaml:"node"`
}

type RegistryConfig struct {
	WebPort        string   `yaml:"webPort"`
	User           string   `yaml:"user"`
	Password       string   `yaml:"password"`
	DockerHTTPPort int      `yaml:"dockerHttpPort"`
	Repositories   []string `yaml:"repositories"`
}

type Rustfs struct {
	Enable bool         `yaml:"enable"`
	Config RustfsConfig `yaml:"config"`
	Nodes  []string     `yaml:"nodes"`
}

type RustfsConfig struct {
	WebPort     string `yaml:"webPort"`
	APIPort     string `yaml:"apiPort"`
	User        string `yaml:"user"`
	Password    string `yaml:"password"`
	InstallType string `yaml:"installType"`
	Volumes     string `yaml:"volumes"`
	// ObsEndpoint 为空时不启用指标上报；非空时应为本节点 OTel Collector 的 OTLP/HTTP 端点。
	ObsEndpoint string `yaml:"obsEndpoint"`
}

// BaseOtelCollector 配置引导期基础设施使用的独立 OTel Collector。
type BaseOtelCollector struct {
	Enable          bool           `yaml:"enable"`
	Node            string         `yaml:"node"`
	OtlpHTTPPort    string         `yaml:"otlpHttpPort"`
	OtlpGRPCPort    string         `yaml:"otlpGrpcPort"`
	SelfMetricsPort string         `yaml:"selfMetricsPort"`
	S3Bucket        string         `yaml:"s3Bucket"`
	S3Prefix        string         `yaml:"s3Prefix"`
	S3Region        string         `yaml:"s3Region"`
	MemLimitMiB     int            `yaml:"memLimitMiB"`
	MysqldExporter  MysqldExporter `yaml:"mysqldExporter"`
	NexusMetrics    NexusMetrics   `yaml:"nexusMetrics"`
}

type MysqldExporter struct {
	Enable          bool   `yaml:"enable"`
	Port            string `yaml:"port"`
	MonitorUser     string `yaml:"monitorUser"`
	MonitorPassword string `yaml:"monitorPassword"`
}

type NexusMetrics struct {
	MetricsUser     string `yaml:"metricsUser"`
	MetricsPassword string `yaml:"metricsPassword"`
	MetricsPath     string `yaml:"metricsPath"`
}

type MysqlConfig struct {
	Enable   bool         `yaml:"enable"`
	Force    bool         `yaml:"force"` // 承接 --mysqlInstallForce
	User     string       `yaml:"user"`
	Password string       `yaml:"password"`
	Port     int          `yaml:"port"`
	AppDbs   []MysqlAppDb `yaml:"appDbs"`
	Node     string       `yaml:"node"`
}

type MysqlAppDb struct {
	Account  string `yaml:"account"`
	Password string `yaml:"password"`
	DbName   string `yaml:"dbName"`
}

type YumServer struct {
	Enable     bool   `yaml:"enable"`
	Node       string `yaml:"node"`
	ListenPort string `yaml:"listenPort"`
}

// NodeRef 对应只有 enable + node 的简单节点引用（NtpServer, NmapServer）。
type NodeRef struct {
	Enable bool   `yaml:"enable"`
	Node   string `yaml:"node"`
}

type Kubernetes struct {
	Enable       bool         `yaml:"enable"`
	Force        bool         `yaml:"force"` // 承接 --kubernetesForce
	BaseServices BaseServices `yaml:"baseServices"`
	KuboardI     Kuboard      `yaml:"kuboardI"`
	K8sTools     K8sTools     `yaml:"k8sTools"`
}

type BaseServices struct {
	Namespaces  []string `yaml:"namespaces"`
	Masters     []string `yaml:"masters"`
	Nodes       []string `yaml:"nodes"`
	Sealos      bool     `yaml:"sealos"`
	KubernetesI bool     `yaml:"kubernetesI"`
	HelmI       bool     `yaml:"helmI"`
	CalicoI     bool     `yaml:"calicoI"`
	IngressI    bool     `yaml:"ingressI"`
}

type Kuboard struct {
	Enable    bool     `yaml:"enable"`
	Node      string   `yaml:"node"`
	EtcdNodes []string `yaml:"etcdNodes"`
}

type K8sTools struct {
	Docker     bool `yaml:"docker"`
	Containerd bool `yaml:"containerd"`
	Helm       bool `yaml:"helm"`
	Helmify    bool `yaml:"helmify"`
	Kubectl    bool `yaml:"kubectl"`
}

// Package 对应 Java Package，x86_64/aarch64 包文件名。
type Package struct {
	X86_64  string `yaml:"x86_64"`
	Aarch64 string `yaml:"aarch64"`
}

// Packages 对应 Java GlobalConfig.Packages。
type Packages struct {
	OS             string  `yaml:"os"`
	Config         string  `yaml:"config"`
	Soft           string  `yaml:"soft"`
	Nexus          Package `yaml:"nexus"`
	Mysql          Package `yaml:"mysql"`
	Rustfs         Package `yaml:"rustfs"`
	OtelColContrib Package `yaml:"otelColContrib"`
	MysqldExporter Package `yaml:"mysqldExporter"`
	Sealos         Package `yaml:"sealos"`
	KubernetesI    Package `yaml:"kubernetesI"`
	HelmI          Package `yaml:"helmI"`
	CalicoI        Package `yaml:"calicoI"`
	IngressI       Package `yaml:"ingressI"`
	KuboardI       Package `yaml:"kuboardI"`
	Helmify        Package `yaml:"helmify"`
	Docker         Package `yaml:"docker"`
	Containerd     Package `yaml:"containerd"`
	Runc           Package `yaml:"runc"`
	Cni            Package `yaml:"cni"`
	Helm           Package `yaml:"helm"`
	Kubectl        Package `yaml:"kubectl"`
}
