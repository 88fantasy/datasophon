package config

// ClusterConfig 对应 Java ClusterConfig，对应 cluster-sample.yml 顶层结构。
type ClusterConfig struct {
	Global   GlobalConfig `yaml:"global"`
	Nodes    []Host       `yaml:"nodes"`
	AddNodes []Host       `yaml:"addNodes"`
}
