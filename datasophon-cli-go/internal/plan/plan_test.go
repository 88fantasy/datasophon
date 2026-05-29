package plan

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// ─── mock handler ─────────────────────────────────────────────────────────────

type mockHandlerSsh struct{ name string }

func (m *mockHandlerSsh) Name() string                       { return m.name }
func (m *mockHandlerSsh) Handle(_ *ssh.Client, _ bool) error { return nil }

// ─── stubs ────────────────────────────────────────────────────────────────────

func stubCfg() *config.ClusterConfig {
	return &config.ClusterConfig{
		Nodes: []config.Host{
			{Hostname: "node1", IP: "10.0.0.1", Port: 22, User: "root", Password: "pass"},
			{Hostname: "node2", IP: "10.0.0.2", Port: 22, User: "root", Password: "pass"},
		},
		Registry:   config.Registry{Enable: true, Node: "node1"},
		Mysql:      config.MysqlConfig{Enable: true, Node: "node1"},
		NtpServer:  config.NodeRef{Enable: true, Node: "node1"},
		NmapServer: config.NodeRef{Enable: true, Node: "node1"},
		YumServer:  config.YumServer{Enable: true, Node: "node1"},
		Kubernetes: config.Kubernetes{Enable: false},
		Rustfs:     config.Rustfs{Enable: true, Nodes: []string{"node1"}},
	}
}

func stubCtx(cfg *config.ClusterConfig, tmpDir string) *BuildContext {
	nodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		nodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	return &BuildContext{
		Cfg:          cfg,
		InitPath:     tmpDir,
		PackagesPath: tmpDir + "/packages",
		InstallPath:  tmpDir + "/install",
		ConfigYaml:   tmpDir + "/cluster.yml",
		LocalIP:      "127.0.0.1",
		LocalHost:    &cfg.Nodes[0],
		GlobalNodes:  nodes,
	}
}

// ─── GeneratePlan tests ───────────────────────────────────────────────────────

func TestGeneratePlan_AllEnabled(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	assert.Equal(t, "initALL", pf.Action)
	assert.Equal(t, len(InitALLRegistry), len(pf.Steps))
}

func TestGeneratePlan_K8sDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Kubernetes.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)

	k8sSkipped := 0
	for _, s := range pf.Steps {
		switch s.ID {
		case "k8s-base-services", "k8s-kuboard", "k8s-registry-conf",
			"k8s-docker", "k8s-kubectl", "k8s-helm", "k8s-helmify":
			assert.Equal(t, StatusSkipped, s.Status, "step %s should be skipped", s.ID)
			k8sSkipped++
		}
	}
	assert.Equal(t, 7, k8sSkipped)
}

func TestGeneratePlan_RegistryDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Registry.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)

	for _, s := range pf.Steps {
		switch s.ID {
		case "init-rustfs", "init-registry", "init-docker-for-registry", "init-registry-upload":
			assert.Equal(t, StatusSkipped, s.Status, "step %s should be skipped", s.ID)
		}
	}
}

func TestGeneratePlan_MysqlDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Mysql.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)

	for _, s := range pf.Steps {
		switch s.ID {
		case "init-mysql", "init-mysql-app-db":
			assert.Equal(t, StatusSkipped, s.Status, "step %s should be skipped", s.ID)
		}
	}
}

func TestGeneratePlan_OnlyInstallK8s(t *testing.T) {
	cfg := stubCfg()
	cfg.Kubernetes.Enable = true
	cfg.Kubernetes.OnlyInstall = true
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)

	for _, s := range pf.Steps {
		if len(s.ID) < 4 || s.ID[:4] != "k8s-" {
			assert.Equal(t, StatusSkipped, s.Status, "non-k8s step %s should be skipped", s.ID)
		}
	}
}

// ─── Store tests ─────────────────────────────────────────────────────────────

func TestSaveLoad_RoundTrip(t *testing.T) {
	tmpDir := t.TempDir()
	cfg := stubCfg()
	ctx := stubCtx(cfg, tmpDir)

	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)

	err = Save(tmpDir, pf)
	require.NoError(t, err)

	info, err := os.Stat(PlanPath(tmpDir, "initALL"))
	require.NoError(t, err)
	assert.Equal(t, os.FileMode(0600), info.Mode().Perm())

	loaded, err := Load(tmpDir, "initALL")
	require.NoError(t, err)
	assert.Equal(t, pf.Action, loaded.Action)
	assert.Equal(t, pf.ClusterHash, loaded.ClusterHash)
	assert.Equal(t, len(pf.Steps), len(loaded.Steps))
}

func TestLoad_FileNotExist(t *testing.T) {
	_, err := Load(t.TempDir(), "test")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "请先执行")
}

func TestComputeHash_Stable(t *testing.T) {
	cfg := stubCfg()
	h1 := ComputeHash(cfg)
	h2 := ComputeHash(cfg)
	assert.Equal(t, h1, h2)
}

func TestComputeHash_ChangeDetected(t *testing.T) {
	cfg := stubCfg()
	h1 := ComputeHash(cfg)
	cfg.Mysql.Enable = !cfg.Mysql.Enable
	h2 := ComputeHash(cfg)
	assert.NotEqual(t, h1, h2)
}

// ─── Apply tests ─────────────────────────────────────────────────────────────

func buildNoop(_ *BuildContext) ([]Action, error) {
	return []Action{{HostKey: "noop", Host: &config.Host{}, Handler: &mockHandlerSsh{name: "noop"}}}, nil
}

func TestApply_SkipCompleted(t *testing.T) {
	tmpDir := t.TempDir()
	registry := []Step{
		{ID: "step-1", Name: "一", Build: buildNoop},
		{ID: "step-2", Name: "二", Build: buildNoop},
		{ID: "step-3", Name: "三", Build: buildNoop},
	}
	cfg := stubCfg()
	ctx := stubCtx(cfg, tmpDir)
	ctx.DryRun = true

	pf, err := GeneratePlan("test", registry, ctx)
	require.NoError(t, err)
	pf.Steps[0].Status = StatusCompleted
	pf.Steps[1].Status = StatusCompleted
	require.NoError(t, Save(tmpDir, pf))

	// Apply 会对 step-3 尝试 SSH；step-1/step-2 必须保持 completed
	_ = Apply(tmpDir, "test", registry, ctx)

	loaded, err := Load(tmpDir, "test")
	require.NoError(t, err)
	assert.Equal(t, StatusCompleted, loaded.Steps[0].Status, "step-1 should stay completed")
	assert.Equal(t, StatusCompleted, loaded.Steps[1].Status, "step-2 should stay completed")
	// step-3 要么 completed（dry-run mock）要么 failed（SSH 失败），但不能是 pending
	assert.NotEqual(t, StatusPending, loaded.Steps[2].Status, "step-3 should not be pending after apply")
}

func TestApply_HashMismatch(t *testing.T) {
	tmpDir := t.TempDir()
	registry := []Step{{ID: "step-1", Name: "一", Build: buildNoop}}
	cfg := stubCfg()
	ctx := stubCtx(cfg, tmpDir)

	pf, err := GeneratePlan("test", registry, ctx)
	require.NoError(t, err)
	require.NoError(t, Save(tmpDir, pf))

	// 修改 cfg 后 apply 应失败
	cfg.Mysql.Enable = !cfg.Mysql.Enable
	ctx.Cfg = cfg

	err = Apply(tmpDir, "test", registry, ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "clusterHash 不匹配")
}

func TestApply_FailedStepMarked(t *testing.T) {
	tmpDir := t.TempDir()
	cfg := stubCfg()
	ctx := stubCtx(cfg, tmpDir)

	failBuild := func(_ *BuildContext) ([]Action, error) {
		return nil, fmt.Errorf("build error injected")
	}
	registry := []Step{{ID: "step-fail", Name: "注入失败", Build: failBuild}}

	// 直接构造 plan 文件（GeneratePlan 也调用 Build 取 targets，failBuild 会在那里失败）
	pf := &PlanFile{
		Version:     "1",
		ConfigFile:  ctx.ConfigYaml,
		ClusterHash: ComputeHash(cfg),
		Action:      "test",
		Steps:       []PlanStep{{ID: "step-fail", Name: "注入失败", Status: StatusPending}},
	}
	require.NoError(t, Save(tmpDir, pf))

	err := Apply(tmpDir, "test", registry, ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "build error injected")

	loaded, err := Load(tmpDir, "test")
	require.NoError(t, err)
	assert.Equal(t, StatusFailed, loaded.Steps[0].Status)
}

// ─── Condition 边界用例 ────────────────────────────────────────────────────────

func assertStepStatus(t *testing.T, pf *PlanFile, id string, want Status) {
	t.Helper()
	for _, s := range pf.Steps {
		if s.ID == id {
			assert.Equal(t, want, s.Status, "step %s", id)
			return
		}
	}
	t.Errorf("step %s not found in plan", id)
}

func TestGeneratePlan_NtpServerDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.NtpServer.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	assertStepStatus(t, pf, "init-ntp-server", StatusSkipped)
	assertStepStatus(t, pf, "init-ntp-slave", StatusSkipped)
}

func TestGeneratePlan_NmapServerDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.NmapServer.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	assertStepStatus(t, pf, "init-nmap", StatusSkipped)
}

func TestGeneratePlan_OfflineNodesNotSkippedWhenRegistryEnabled(t *testing.T) {
	cfg := stubCfg()
	cfg.YumServer.Enable = false
	cfg.Registry.Enable = true
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	// YumServer=false 但 Registry=true → init-offline-nodes 不应跳过
	assertStepStatus(t, pf, "init-offline-nodes", StatusPending)
}

func TestGeneratePlan_RustfsSkippedWhenRegistryDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Rustfs.Enable = true
	cfg.Registry.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	assertStepStatus(t, pf, "init-rustfs", StatusSkipped)
}

func TestGeneratePlan_KuboardSkippedWhenKuboardDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Kubernetes.Enable = true
	cfg.Kubernetes.KuboardI.Enable = false
	ctx := stubCtx(cfg, t.TempDir())
	pf, err := GeneratePlan("initALL", InitALLRegistry, ctx)
	require.NoError(t, err)
	assertStepStatus(t, pf, "k8s-kuboard", StatusSkipped)
	// 其他 k8s-* 应正常 pending（不跳过）
	assertStepStatus(t, pf, "k8s-base-services", StatusPending)
	assertStepStatus(t, pf, "k8s-docker", StatusPending)
}
