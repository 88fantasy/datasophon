package plan

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// newNodeFixture 返回一个不在 stubCfg().Nodes 中的目标新节点。
func newNodeFixture() config.Host {
	return config.Host{Hostname: "newnode", IP: "10.0.0.99", Port: 22, User: "root", Password: "pass"}
}

// ctxWithTarget 在 stubCtx 基础上设置 TargetNode。
func ctxWithTarget(cfg *config.ClusterConfig, tmpDir string, target *config.Host) *BuildContext {
	ctx := stubCtx(cfg, tmpDir)
	ctx.TargetNode = target
	return ctx
}

// ─── InitNodeRegistry 全量测试 ─────────────────────────────────────────────────

// TestInitNodeRegistry_StepCount 确认注册表长度与预期一致。
func TestInitNodeRegistry_StepCount(t *testing.T) {
	assert.Len(t, InitNodeRegistry, 12, "InitNodeRegistry 应包含 12 个步骤")
}

// TestInitNodeRegistry_Hadoop_AllConditionsEnabled 验证 hadoop+ntp+offline 全开时三个条件步骤均规划执行，
// 且 Targets 仅包含目标新节点 hostname。
func TestInitNodeRegistry_Hadoop_AllConditionsEnabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Global.ClusterType = config.ClusterTypeHadoop
	cfg.Global.Offline = true
	cfg.NtpServer = config.NodeRef{Enable: true, Node: "node1"}
	cfg.YumServer = config.YumServer{Enable: true, Node: "node1", ListenPort: "9999"}

	newNode := newNodeFixture()
	ctx := ctxWithTarget(cfg, t.TempDir(), &newNode)

	pf, err := GeneratePlan("initNode", InitNodeRegistry, ctx)
	require.NoError(t, err)
	assert.Equal(t, len(InitNodeRegistry), len(pf.Steps))

	// hadoop_user（ScopeHadoopOnly）应执行
	assertStepStatus(t, pf, "node-hadoopuser", StatusPending)
	assertTargetOnly(t, pf, "node-hadoopuser", newNode.Hostname)

	// ntp_slave（Condition: NtpServer.Enable=true）应执行
	assertStepStatus(t, pf, "node-ntp-slave", StatusPending)
	assertTargetOnly(t, pf, "node-ntp-slave", newNode.Hostname)

	// offline_slave（Condition: Global.Offline=true）应执行
	assertStepStatus(t, pf, "node-offline-slave", StatusPending)
	assertTargetOnly(t, pf, "node-offline-slave", newNode.Hostname)

	// 基础步骤也应执行并只针对新节点
	for _, id := range []string{"node-bash", "node-firewall", "node-selinux", "node-swap",
		"node-library", "node-os-safe-conf", "node-system-conf", "node-hostname", "node-hugepage"} {
		assertStepStatus(t, pf, id, StatusPending)
		assertTargetOnly(t, pf, id, newNode.Hostname)
	}
}

// TestInitNodeRegistry_Kubernetes_ConditionsDisabled 验证 kubernetes 类型时：
// hadoop_user 被 Scope 跳过；ntp/offline 在禁用时也被 Condition 跳过。
func TestInitNodeRegistry_Kubernetes_ConditionsDisabled(t *testing.T) {
	cfg := stubCfg()
	cfg.Global.ClusterType = config.ClusterTypeKubernetes
	cfg.Global.Offline = false
	cfg.NtpServer = config.NodeRef{Enable: false}

	newNode := newNodeFixture()
	ctx := ctxWithTarget(cfg, t.TempDir(), &newNode)

	pf, err := GeneratePlan("initNode", InitNodeRegistry, ctx)
	require.NoError(t, err)

	// hadoop_user 应跳过（ScopeHadoopOnly，但 type=kubernetes）
	assertStepStatus(t, pf, "node-hadoopuser", StatusSkipped)
	// ntp_slave 应跳过（NtpServer.Enable=false）
	assertStepStatus(t, pf, "node-ntp-slave", StatusSkipped)
	// offline_slave 应跳过（Global.Offline=false）
	assertStepStatus(t, pf, "node-offline-slave", StatusSkipped)
}

// TestInitNodeRegistry_NtpOnly 验证只开 ntp 时，offline_slave 仍跳过，ntp_slave 执行。
func TestInitNodeRegistry_NtpOnly(t *testing.T) {
	cfg := stubCfg()
	cfg.Global.ClusterType = config.ClusterTypeHadoop
	cfg.Global.Offline = false
	cfg.NtpServer = config.NodeRef{Enable: true, Node: "node1"}

	newNode := newNodeFixture()
	ctx := ctxWithTarget(cfg, t.TempDir(), &newNode)

	pf, err := GeneratePlan("initNode", InitNodeRegistry, ctx)
	require.NoError(t, err)

	assertStepStatus(t, pf, "node-ntp-slave", StatusPending)
	assertTargetOnly(t, pf, "node-ntp-slave", newNode.Hostname)
	assertStepStatus(t, pf, "node-offline-slave", StatusSkipped)
}

// TestInitNodeRegistry_NtpSlave_ExcludesNtpServerItself 验证新节点恰好是 NTP server 时
// ntp_slave 对新节点无目标（slavesOf 过滤掉同 IP）。
func TestInitNodeRegistry_NtpSlave_ExcludesNtpServerItself(t *testing.T) {
	cfg := stubCfg()
	cfg.Global.ClusterType = config.ClusterTypeHadoop
	cfg.Global.Offline = false
	cfg.NtpServer = config.NodeRef{Enable: true, Node: "node1"}

	// 目标新节点 IP 与 NTP server node1(10.0.0.1) 相同 → slavesOf 过滤
	ntpServerNode := config.Host{Hostname: "newntpserver", IP: "10.0.0.1", Port: 22, User: "root", Password: "pass"}
	ctx := ctxWithTarget(cfg, t.TempDir(), &ntpServerNode)

	pf, err := GeneratePlan("initNode", InitNodeRegistry, ctx)
	require.NoError(t, err)

	// ntp_slave 仍 pending（Condition 通过），但 targets 为空（slavesOf 排除了自身）
	assertStepStatus(t, pf, "node-ntp-slave", StatusPending)
	for _, s := range pf.Steps {
		if s.ID == "node-ntp-slave" {
			assert.Empty(t, s.Targets, "NTP server 自身不应成为 slave target")
		}
	}
}

// ─── helpers ──────────────────────────────────────────────────────────────────

// assertTargetOnly 断言指定 step 的 Targets 恰好只包含期望的 hostname（单节点目标）。
func assertTargetOnly(t *testing.T, pf *PlanFile, stepID, hostname string) {
	t.Helper()
	for _, s := range pf.Steps {
		if s.ID == stepID {
			assert.Equal(t, []string{hostname}, s.Targets,
				"step %s 的 targets 应仅包含 %s", stepID, hostname)
			return
		}
	}
	t.Errorf("step %s not found in plan", stepID)
}
