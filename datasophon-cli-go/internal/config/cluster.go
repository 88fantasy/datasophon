package config

import "fmt"

// ClusterType 标识集群类型。
type ClusterType string

const (
	ClusterTypeHadoop     ClusterType = "hadoop"
	ClusterTypeKubernetes ClusterType = "kubernetes"
)

// ParseClusterType 校验字符串并返回合法的 ClusterType，非法时返回错误。
// 集群类型的唯一校验入口，CLI flag 与配置文件值都应经此校验。
func ParseClusterType(s string) (ClusterType, error) {
	switch ClusterType(s) {
	case ClusterTypeHadoop, ClusterTypeKubernetes:
		return ClusterType(s), nil
	default:
		return "", fmt.Errorf("--type 必须是 %s 或 %s，当前值: %q",
			ClusterTypeHadoop, ClusterTypeKubernetes, s)
	}
}

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
