package config

import (
	"fmt"
	"log/slog"
	"os"

	"gopkg.in/yaml.v3"
)

// Load 读取 cluster.yml 并返回 ClusterConfig。
// 第一版仅支持明文密码（不解密 jasypt 加密字段）。
func Load(path, password string) (*ClusterConfig, error) {
	if password != "" {
		slog.Warn("⚠️  第一版暂不支持 jasypt 加密。密码字段将按明文读取。" +
			"请确保 cluster.yml 中所有密码已转为明文。" +
			"jasypt 兼容将在 Phase 4 实现。")
	}

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
