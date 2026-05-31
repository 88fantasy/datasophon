package config

import (
	"bytes"
	"fmt"
	"os"

	"gopkg.in/yaml.v3"
)

// Load 读取 cluster.yml 并返回 ClusterConfig（仅支持明文配置文件）。
// 采用严格解码（KnownFields），配置中出现未知字段会显式报错，
// 避免旧版本字段（如已上移到顶层的 registry/mysql）被静默忽略。
func Load(path string) (*ClusterConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败 %s: %w", path, err)
	}

	var cfg ClusterConfig
	dec := yaml.NewDecoder(bytes.NewReader(data))
	dec.KnownFields(true)
	if err := dec.Decode(&cfg); err != nil {
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
