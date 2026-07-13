package plan

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
	"golang.org/x/crypto/ssh"
)

// ─── workerHostSlice ─────────────────────────────────────────────────────────

func TestWorkerHostSlice_ExcludesLocal(t *testing.T) {
	nodes := []config.Host{
		{Hostname: "node1", IP: "10.0.0.1"},
		{Hostname: "node2", IP: "10.0.0.2"},
		{Hostname: "node3", IP: "10.0.0.3"},
	}
	workers := workerHostSlice(nodes, "10.0.0.1")
	require.Len(t, workers, 2)
	assert.Equal(t, "node2", workers[0].Hostname)
	assert.Equal(t, "node3", workers[1].Hostname)
}

func TestWorkerHostSlice_AllLocal(t *testing.T) {
	nodes := []config.Host{{Hostname: "solo", IP: "10.0.0.1"}}
	workers := workerHostSlice(nodes, "10.0.0.1")
	assert.Empty(t, workers)
}

// ─── slavesOf ────────────────────────────────────────────────────────────────

func TestSlavesOf_ExcludesServer(t *testing.T) {
	server := &config.Host{IP: "10.0.0.1"}
	all := []*config.Host{
		{IP: "10.0.0.1"},
		{IP: "10.0.0.2"},
		{IP: "10.0.0.3"},
	}
	slaves := slavesOf(all, server)
	require.Len(t, slaves, 2)
	for _, s := range slaves {
		assert.NotEqual(t, "10.0.0.1", s.IP)
	}
}

// ─── uniqueHostKeys ──────────────────────────────────────────────────────────

func TestUniqueHostKeys_Dedup(t *testing.T) {
	actions := []Action{
		{HostKey: "node1"},
		{HostKey: "node2"},
		{HostKey: "node1"},
		{HostKey: "node3"},
	}
	keys := uniqueHostKeys(actions)
	assert.Equal(t, []string{"node1", "node2", "node3"}, keys)
}

func TestUniqueHostKeys_Empty(t *testing.T) {
	assert.Empty(t, uniqueHostKeys(nil))
}

// ─── hostsToActions ──────────────────────────────────────────────────────────

type dummyHandler struct{ name string }

func (d *dummyHandler) Name() string                       { return d.name }
func (d *dummyHandler) Handle(_ *ssh.Client, _ bool) error { return nil }

func TestHostsToActions_FieldMapping(t *testing.T) {
	h := &dummyHandler{name: "test"}
	hosts := []*config.Host{
		{Hostname: "node1", IP: "10.0.0.1"},
		{Hostname: "node2", IP: "10.0.0.2"},
	}
	actions := hostsToActions(hosts, h)
	require.Len(t, actions, 2)
	assert.Equal(t, "node1", actions[0].HostKey)
	assert.Equal(t, hosts[0], actions[0].Host)
	assert.Equal(t, h, actions[0].Handler)
}

// ─── hostsToPtr ──────────────────────────────────────────────────────────────

func TestHostsToPtr_SameLength(t *testing.T) {
	hosts := []config.Host{{Hostname: "a"}, {Hostname: "b"}}
	ptrs := hostsToPtr(hosts)
	require.Len(t, ptrs, 2)
	assert.Equal(t, "a", ptrs[0].Hostname)
	assert.Equal(t, "b", ptrs[1].Hostname)
}

// ─── applyRegistry ───────────────────────────────────────────────────────────

func TestApplyRegistry_EnableTrue(t *testing.T) {
	tb := &initcmd.TaskBase{}
	reg := &config.Registry{
		Enable: true,
		Node:   "reg-host",
		Config: config.RegistryConfig{
			WebPort:  "8081",
			User:     "admin",
			Password: "secret",
		},
	}
	globalNodes := map[string]*config.Host{"reg-host": {Hostname: "reg-host", IP: "10.0.0.9"}}
	applyRegistry(tb, reg, globalNodes)
	assert.True(t, tb.EnableRegistry)
	assert.Equal(t, "10.0.0.9", tb.RegistryIP) // 解析为 IP，而非 hostname，避免 init-all-host 之前 DNS 不可用
	assert.Equal(t, "8081", tb.RegistryPort)
	assert.Equal(t, "admin", tb.RegistryUsername)
	assert.Equal(t, "secret", tb.RegistryPassword)
}

func TestApplyRegistry_UnknownNodeFallsBackToHostname(t *testing.T) {
	tb := &initcmd.TaskBase{}
	reg := &config.Registry{Enable: true, Node: "reg-host"}
	applyRegistry(tb, reg, map[string]*config.Host{})
	assert.Equal(t, "reg-host", tb.RegistryIP)
}

func TestApplyRegistry_EnableFalse(t *testing.T) {
	tb := &initcmd.TaskBase{}
	reg := &config.Registry{Enable: false, Node: "reg-host"}
	applyRegistry(tb, reg, nil)
	assert.False(t, tb.EnableRegistry)
	assert.Empty(t, tb.RegistryIP)
}

func TestApplyRegistry_Nil(t *testing.T) {
	tb := &initcmd.TaskBase{}
	applyRegistry(tb, nil, nil)
	assert.False(t, tb.EnableRegistry)
	assert.Empty(t, tb.RegistryIP)
}

// ─── applyConfig ─────────────────────────────────────────────────────────────

func TestApplyConfig_SetsPath(t *testing.T) {
	tb := &initcmd.TaskBase{}
	applyConfig(tb, "/etc/datasophon/cluster.yml")
	assert.Equal(t, "/etc/datasophon/cluster.yml", tb.ConfigFilePath)
}

// ─── allNodes selector ───────────────────────────────────────────────────────

func TestAllNodes_ReturnsAllCfgNodes(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	nodes := allNodes(ctx)
	assert.Equal(t, cfg.Nodes, nodes)
}

// ─── simpleAllNodes ──────────────────────────────────────────────────────────

func TestSimpleAllNodes_IndependentHandlers(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	newCalled := 0
	build := simpleAllNodes(func() handler.Handler {
		newCalled++
		return &dummyHandler{name: "h"}
	}, allNodes)
	actions, err := build(ctx)
	require.NoError(t, err)
	assert.Len(t, actions, len(cfg.Nodes))
	// 每节点独立 handler 实例
	assert.Equal(t, newCalled, len(cfg.Nodes))
}
