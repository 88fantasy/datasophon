package create

import (
	"os"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// minimalClusterYAML 包含一个既有节点（10.0.0.1），用于测试重复检测。
const minimalClusterYAML = `global:
  cluster-type: hadoop
  sshAuthType: PASSWORD
ntpServer:
  enable: false
  node: ""
nodes:
  - ip: 10.0.0.1
    port: 22
    user: root
    password: root123
    hostname: node1
`

// writeTemp 写一个临时 YAML 文件，返回路径（调用方负责清理）。
func writeTemp(t *testing.T, content string) string {
	t.Helper()
	f, err := os.CreateTemp("", "cluster-*.yml")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.WriteString(content)
	f.Close()
	return f.Name()
}

// TestSetupConfig_DuplicateIP 验证目标节点 IP 已存在于配置文件时，setupConfig 返回错误并包含 IP 信息。
func TestSetupConfig_DuplicateIP(t *testing.T) {
	cfgPath := writeTemp(t, minimalClusterYAML)
	defer os.Remove(cfgPath)

	tmpDir := t.TempDir()
	n := &nodeInitializer{
		DatasophonPath:  tmpDir,
		InstallPath:     tmpDir + "/install",
		ProductPkgsPath: tmpDir + "/pkgs",
	}
	_ = os.MkdirAll(tmpDir+"/install", 0755)

	// 目标节点 IP = 10.0.0.1，已存在于 nodes 列表
	newNode := &config.Host{IP: "10.0.0.1", Port: 22, User: "root", Password: "pass", Hostname: "newnode"}
	err := n.setupConfig(cfgPath, newNode)
	if err == nil {
		t.Fatal("期望 setupConfig 返回错误（重复 IP），但返回 nil")
	}
	if !strings.Contains(err.Error(), "10.0.0.1") {
		t.Errorf("错误信息应包含重复 IP，实际: %v", err)
	}
	if !strings.Contains(err.Error(), "已存在") {
		t.Errorf("错误信息应包含'已存在'，实际: %v", err)
	}
}

// TestSetupConfig_DuplicateHostname 验证目标节点 hostname 已存在（IP 不同）时，setupConfig 返回错误。
// 修复前：仅检查 IP，同 hostname 节点会通过预检并完整初始化，最后被写回阶段静默跳过。
func TestSetupConfig_DuplicateHostname(t *testing.T) {
	cfgPath := writeTemp(t, minimalClusterYAML)
	defer os.Remove(cfgPath)

	tmpDir := t.TempDir()
	n := &nodeInitializer{
		DatasophonPath:  tmpDir,
		InstallPath:     tmpDir + "/install",
		ProductPkgsPath: tmpDir + "/pkgs",
	}
	_ = os.MkdirAll(tmpDir+"/install", 0755)

	// IP 不同，但 hostname = node1 与配置文件中已有节点重复
	newNode := &config.Host{IP: "10.0.0.99", Port: 22, User: "root", Password: "pass", Hostname: "node1"}
	err := n.setupConfig(cfgPath, newNode)
	if err == nil {
		t.Fatal("期望 setupConfig 返回错误（重复 hostname），但返回 nil")
	}
	if !strings.Contains(err.Error(), "node1") {
		t.Errorf("错误信息应包含重复 hostname，实际: %v", err)
	}
	if !strings.Contains(err.Error(), "已存在") {
		t.Errorf("错误信息应包含'已存在'，实际: %v", err)
	}
}

// TestSetupConfig_NewIP 验证目标节点 IP 不存在于配置文件时，setupConfig 正常返回并填充运行时字段。
func TestSetupConfig_NewIP(t *testing.T) {
	cfgPath := writeTemp(t, minimalClusterYAML)
	defer os.Remove(cfgPath)

	tmpDir := t.TempDir()
	n := &nodeInitializer{
		DatasophonPath:  tmpDir,
		InstallPath:     tmpDir + "/install",
		ProductPkgsPath: tmpDir + "/pkgs",
	}
	_ = os.MkdirAll(tmpDir+"/install", 0755)

	newNode := &config.Host{IP: "10.0.0.99", Port: 22, User: "root", Password: "pass", Hostname: "newnode"}
	err := n.setupConfig(cfgPath, newNode)
	if err != nil {
		t.Fatalf("期望 setupConfig 成功，但返回错误: %v", err)
	}

	// 验证关键字段被正确填充
	if n.currentCfg == nil {
		t.Fatal("currentCfg 应被设置")
	}
	if n.targetNode == nil || n.targetNode.IP != "10.0.0.99" {
		t.Errorf("targetNode 未正确设置，实际: %v", n.targetNode)
	}
	if n.initConfigYaml != cfgPath {
		t.Errorf("initConfigYaml 应等于 cfgPath，实际: %s", n.initConfigYaml)
	}
	if _, ok := n.globalNodes["node1"]; !ok {
		t.Error("globalNodes 应包含已有节点 node1")
	}
	// Bug 4 修复验证：targetNode 本身也应加入 globalNodes，
	// 确保 buildNtpSlave 等通过 requireNode 引用新节点时不报"节点不在列表中"
	if h, ok := n.globalNodes["newnode"]; !ok || h.IP != "10.0.0.99" {
		t.Error("globalNodes 应包含目标新节点 newnode（IP=10.0.0.99）")
	}
}
