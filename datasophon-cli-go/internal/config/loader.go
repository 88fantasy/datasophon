package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Load 读取 cluster.yml 并返回 ClusterConfig（仅支持明文配置文件）。
func Load(path string) (*ClusterConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败 %s: %w", path, err)
	}

	var cfg ClusterConfig
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}
	return &cfg, nil
}
