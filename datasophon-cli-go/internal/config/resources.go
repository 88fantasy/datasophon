package config

import (
	"bytes"
	"embed"
	"fmt"
	"io/fs"
	"path/filepath"

	"github.com/spf13/viper"
)

//go:embed configs/*.yml
var configsFS embed.FS

// ResourceManager 统一管理资源文件
var resourceManager *ResourceManager

type ResourceManager struct {
	configs map[string]*viper.Viper
}

// GetResourceManager 获取资源管理器单例
func GetResourceManager() *ResourceManager {
	if resourceManager == nil {
		resourceManager = &ResourceManager{
			configs: make(map[string]*viper.Viper),
		}
	}
	return resourceManager
}

// LoadConfig 加载指定的配置文件
func (rm *ResourceManager) LoadConfig(name string) (*viper.Viper, error) {
	if v, exists := rm.configs[name]; exists {
		return v, nil
	}

	v := viper.New()
	v.SetConfigType("yaml")

	filePath := filepath.Join("configs", name+".yml")
	data, err := configsFS.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("读取嵌入的配置文件 %s 失败: %w", name, err)
	}

	if err := v.ReadConfig(bytes.NewReader(data)); err != nil {
		return nil, fmt.Errorf("解析配置文件 %s 失败: %w", name, err)
	}

	rm.configs[name] = v
	return v, nil
}

// GetConfigBytes 获取配置文件的原始字节数据
func (rm *ResourceManager) GetConfigBytes(name string) ([]byte, error) {
	filePath := filepath.Join("configs", name+".yml")
	data, err := configsFS.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("读取嵌入的配置文件 %s 失败: %w", name, err)
	}
	return data, nil
}

// ListConfigs 列出所有可用的配置文件名
func (rm *ResourceManager) ListConfigs() ([]string, error) {
	var names []string
	err := fs.WalkDir(configsFS, "configs", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() && filepath.Ext(path) == ".yml" {
			name := filepath.Base(path)
			names = append(names, name[:len(name)-4]) // 去掉 .yml 后缀
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("遍历配置文件失败: %w", err)
	}
	return names, nil
}

// GetClusterConfigTemplate 获取集群配置模板
func GetClusterConfigTemplate() ([]byte, error) {
	rm := GetResourceManager()
	return rm.GetConfigBytes("cluster-config")
}
