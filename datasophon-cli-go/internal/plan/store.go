package plan

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

const planFileName = "cluster.plan.json"

// PlanPath 返回计划文件的绝对路径。
func PlanPath(initPath string) string {
	return filepath.Join(initPath, "state", planFileName)
}

// Save 把 PlanFile 序列化为 JSON 并写入磁盘（0600 权限）。
func Save(initPath string, pf *PlanFile) error {
	pf.UpdatedAt = time.Now()
	dir := filepath.Join(initPath, "state")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("创建 state 目录失败: %w", err)
	}
	path := PlanPath(initPath)
	data, err := json.MarshalIndent(pf, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化 plan 失败: %w", err)
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		return fmt.Errorf("写入 plan 文件失败 %s: %w", path, err)
	}
	return nil
}

// Load 从磁盘反序列化 PlanFile。
func Load(initPath string) (*PlanFile, error) {
	path := PlanPath(initPath)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("计划文件不存在 %s，请先执行 `create cluster plan`", path)
		}
		return nil, fmt.Errorf("读取 plan 文件失败: %w", err)
	}
	var pf PlanFile
	if err := json.Unmarshal(data, &pf); err != nil {
		return nil, fmt.Errorf("解析 plan 文件失败: %w", err)
	}
	return &pf, nil
}

// PlanExists 检查计划文件是否存在。
func PlanExists(initPath string) bool {
	_, err := os.Stat(PlanPath(initPath))
	return err == nil
}

// ComputeHash 计算 cfg 的 SHA256 前缀（16 字节 hex），作为 ClusterHash。
func ComputeHash(cfg *config.ClusterConfig) string {
	h := sha256.New()
	data, _ := json.Marshal(cfg)
	h.Write(data)
	return hex.EncodeToString(h.Sum(nil))[:16]
}
