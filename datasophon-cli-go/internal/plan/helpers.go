package plan

import (
	"log/slog"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// applyRegistry 把 Registry 配置写入 TaskBase。
func applyRegistry(tb *initcmd.TaskBase, registry *config.Registry) {
	if registry == nil || !registry.Enable {
		return
	}
	tb.EnableRegistry = true
	tb.RegistryIP = registry.Node
	tb.RegistryPort = registry.Config.WebPort
	tb.RegistryUsername = registry.Config.User
	tb.RegistryPassword = registry.Config.Password
}

// applyConfig 把配置文件路径写入 TaskBase。
func applyConfig(tb *initcmd.TaskBase, configFilePath string) {
	tb.ConfigFilePath = configFilePath
}

// hostsToPtr 把值切片转为指针切片。
func hostsToPtr(hosts []config.Host) []*config.Host {
	result := make([]*config.Host, len(hosts))
	for i := range hosts {
		result[i] = &hosts[i]
	}
	return result
}

// workerHostSlice 过滤掉本地节点，返回剩余节点（对应 nodeInitializer.workerNodes）。
func workerHostSlice(nodes []config.Host, localIP string) []*config.Host {
	var result []*config.Host
	for i := range nodes {
		if nodes[i].IP != localIP {
			result = append(result, &nodes[i])
		}
	}
	return result
}

// hostsToActions 把 []*config.Host + handler 展开成 []Action。
func hostsToActions(hosts []*config.Host, h handler.Handler) []Action {
	actions := make([]Action, 0, len(hosts))
	for _, host := range hosts {
		actions = append(actions, Action{HostKey: host.Hostname, Host: host, Handler: h})
	}
	return actions
}

// uniqueHostKeys 从 []Action 提取不重复的 hostname 列表（用于 PlanStep.Targets）。
func uniqueHostKeys(actions []Action) []string {
	seen := make(map[string]bool, len(actions))
	var result []string
	for _, a := range actions {
		if !seen[a.HostKey] {
			seen[a.HostKey] = true
			result = append(result, a.HostKey)
		}
	}
	return result
}

// nodeSelector 定义从 BuildContext 选取节点列表的函数类型。
type nodeSelector func(ctx *BuildContext) []config.Host

// allNodes 选取 cfg.Nodes。
func allNodes(ctx *BuildContext) []config.Host { return ctx.Cfg.Nodes }

// targetNode 选取 BuildContext.TargetNode（单节点目标，供 create node 使用）。
// TargetNode 为 nil 属于编程错误：InitNodeRegistry 必须通过 setupConfig 初始化后才能调用。
// 此时记录 Error 日志并返回空切片，使调用方（GeneratePlan/Apply）以零目标执行步骤，
// 而非静默成功——调用方应在此之前通过参数校验保证 TargetNode 非 nil。
func targetNode(ctx *BuildContext) []config.Host {
	if ctx.TargetNode == nil {
		slog.Error("targetNode: BuildContext.TargetNode 为 nil，步骤将无目标节点（无操作）；确认已通过 setupConfig 初始化")
		return nil
	}
	return []config.Host{*ctx.TargetNode}
}

// slavesOf 过滤掉 serverNode，返回其他节点（对应 nodeInitializer.slavesNodesExec）。
func slavesOf(all []*config.Host, serverHost *config.Host) []*config.Host {
	var result []*config.Host
	for _, h := range all {
		if h.IP != serverHost.IP {
			result = append(result, h)
		}
	}
	return result
}
