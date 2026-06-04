package plan

import (
	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// InitNodeRegistry 对应 create node 的 12 步节点级 DAG。
// 所有 builder 复用 builders_common.go 中已有工厂，仅把节点选择器换成 targetNode（单节点）。
//
// 步骤排序说明：
//   - offline_slave（步骤 6）在 library（步骤 8）之前：先配好离线 yum/apt 源，装包才能成功。
//   - ntp_slave（步骤 7）紧接 offline_slave：chrony 需要通过 yum/apt 安装。
//   - hadoop_user 用 ScopeHadoopOnly，非 hadoop 集群类型时由 GeneratePlan 自动跳过。
//   - offline_slave / ntp_slave 用 Condition 函数，cfg 未开启时自动跳过。
//
// 注意：offline=true 时，配置文件须包含有效的 registry.node 或 yumServer.node，
// 否则 buildOfflineNodes 构造的 ServerIP 为空字符串（下游会报错）。
var InitNodeRegistry = []Step{
	{ID: "node-bash", Name: "shell bash 设置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitBash{} }, targetNode)},

	{ID: "node-hadoopuser", Name: "创建 hadoop 用户和组",
		Scope: ScopeHadoopOnly,
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitHadoopUser{} }, targetNode)},

	{ID: "node-firewall", Name: "关闭防火墙",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitFirewall{} }, targetNode)},

	{ID: "node-selinux", Name: "关闭 selinux",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSelinux{} }, targetNode)},

	{ID: "node-swap", Name: "关闭 swap",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSwap{} }, targetNode)},

	{ID: "node-offline-slave", Name: "yum/apt 离线源节点配置",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Offline },
		Build:     buildOfflineNodes(targetNode)},

	{ID: "node-ntp-slave", Name: "配置 NTP Slave",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.NtpServer.Enable },
		Build:     buildNtpSlave(targetNode)},

	{ID: "node-library", Name: "初始化依赖库",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitLibrary{} }, targetNode)},

	{ID: "node-os-safe-conf", Name: "安全配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitOsSafeConf{} }, targetNode)},

	{ID: "node-system-conf", Name: "优化系统配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSystemConf{} }, targetNode)},

	{ID: "node-hostname", Name: "配置 hostname",
		Build: buildHostname(targetNode)},

	{ID: "node-hugepage", Name: "关闭透明大页",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitHugePage{} }, targetNode)},
}
