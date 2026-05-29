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

// Save 将 ClusterConfig 序列化后写回指定路径，保留原文件权限。
// 注意：重序列化会丢失 YAML 注释，这是 go-yaml 的固有限制。
func Save(path string, cfg *ClusterConfig) error {
	data, err := yaml.Marshal(cfg)
	if err != nil {
		return fmt.Errorf("序列化配置失败: %w", err)
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return fmt.Errorf("写回配置文件失败 %s: %w", path, err)
	}
	return nil
}
