package plan

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// stubCfg/stubCtx 来自 plan_test.go（相同包，直接复用）

// buildBinPackage/buildJdk8/buildJdk21：
// stubCtx.LocalIP="127.0.0.1"，与 node1/node2 的 IP 均不同，
// 所以 workerHostSlice 不排除任何节点，2 个 worker。

func TestBuildBinPackage_WorkerCount(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildBinPackage(allNodes)(ctx)
	require.NoError(t, err)
	assert.Len(t, actions, 2)
}

func TestBuildTar_WorkerCount(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildTar(allNodes)(ctx)
	require.NoError(t, err)
	assert.Len(t, actions, 2)
}

func TestBuildTar_IncludesLocalNode(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	ctx.LocalIP = "10.0.0.1"
	ctx.ProductPkgsPath = t.TempDir() + "/pkg"

	actions, err := buildTar(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)
	assert.Equal(t, ctx.ProductPkgsPath, actions[0].Handler.(*initcmd.InitTar).ProductPackagesPath)
}

func TestBuildJdk8_WorkerCountAndRegistry(t *testing.T) {
	cfg := stubCfg()
	cfg.Registry = config.Registry{
		Enable: true,
		Node:   "node1",
		Config: config.RegistryConfig{WebPort: "8081", User: "admin", Password: "pass"},
	}
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildJdk8(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)

	jdk := actions[0].Handler.(*initcmd.InitJdk8)
	assert.True(t, jdk.EnableRegistry)
	assert.Equal(t, "10.0.0.1", jdk.RegistryIP) // 解析为 IP，而非 hostname "node1"
	assert.Equal(t, "8081", jdk.RegistryPort)
	assert.Equal(t, ctx.InstallPath, jdk.InstallPath)
}

func TestBuildJdk21_WorkerCountAndRegistry(t *testing.T) {
	cfg := stubCfg()
	cfg.Registry = config.Registry{
		Enable: true,
		Node:   "node1",
		Config: config.RegistryConfig{WebPort: "8082"},
	}
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildJdk21(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)

	jdk := actions[0].Handler.(*initcmd.InitJdk21)
	assert.True(t, jdk.EnableRegistry)
	assert.Equal(t, "8082", jdk.RegistryPort)
	assert.Equal(t, ctx.InstallPath, jdk.InstallPath)
}

func TestBuildOfflineNodes_AllNodes(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildOfflineNodes(allNodes)(ctx)
	require.NoError(t, err)
	// hostsToPtr(allNodes) → 全 2 节点，不过滤本地
	assert.Len(t, actions, 2)
}

func TestBuildOfflineNodes_RegistryOverridesYum(t *testing.T) {
	cfg := stubCfg()
	cfg.Registry = config.Registry{
		Enable: true,
		Node:   "node1",
		Config: config.RegistryConfig{WebPort: "8081"},
	}
	cfg.YumServer = config.YumServer{Enable: true, Node: "yum-node", ListenPort: "9999"}
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildOfflineNodes(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)
	// Registry 启用时 ServerIP/Port 应来自 Registry，不是 YumServer；
	// ServerIP 解析为 IP（而非 hostname "node1"），因为本步排在 init-all-host 之前，DNS 不可用。
	h := actions[0].Handler.(*initcmd.InitOfflineSlave)
	assert.Equal(t, "10.0.0.1", h.ServerIP)
	assert.Equal(t, "8081", h.ServerPort)
}

func TestBuildHostname_EachNodeOwnInstance(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildHostname(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)

	h0 := actions[0].Handler.(*initcmd.InitHostname)
	h1 := actions[1].Handler.(*initcmd.InitHostname)
	// 每个节点的 handler 应绑定自身的 hostname
	assert.Equal(t, cfg.Nodes[0].Hostname, h0.Hostname)
	assert.Equal(t, cfg.Nodes[1].Hostname, h1.Hostname)
	// 且两个 handler 是不同实例
	assert.NotSame(t, h0, h1)
}

func TestBuildAllHost_SingleHandler(t *testing.T) {
	cfg := stubCfg()
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildAllHost(allNodes)(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 2)
	// 同一 handler 实例被复用
	assert.Same(t, actions[0].Handler, actions[1].Handler)
}

func TestBuildNtpSlave_ExcludesServer(t *testing.T) {
	cfg := stubCfg()
	// NtpServer.Node = "node1"，GlobalNodes["node1"].IP = "10.0.0.1"
	ctx := stubCtx(cfg, t.TempDir())
	actions, err := buildNtpSlave(allNodes)(ctx)
	require.NoError(t, err)
	// node1 是 server，应被排除；只剩 node2
	require.Len(t, actions, 1)
	assert.Equal(t, "node2", actions[0].HostKey)
}
