package config

// ClusterType 标识集群类型。
type ClusterType string

const (
	ClusterTypeHadoop     ClusterType = "hadoop"
	ClusterTypeKubernetes ClusterType = "kubernetes"
)

// ClusterConfig 对应 Java ClusterConfig，对应 cluster-sample.yml 顶层结构。
type ClusterConfig struct {
	Global     GlobalConfig `yaml:"global"`
	Registry   Registry     `yaml:"registry"`
	Rustfs     Rustfs       `yaml:"rustfs"`
	NmapServer NodeRef      `yaml:"nmapServer"`
	Mysql      MysqlConfig  `yaml:"mysql"`
	YumServer  YumServer    `yaml:"yumServer"`
	NtpServer  NodeRef      `yaml:"ntpServer"`
	Kubernetes Kubernetes   `yaml:"kubernetes"`
	Packages   Packages     `yaml:"packages"`
	Nodes      []Host       `yaml:"nodes"`
}
