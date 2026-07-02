package plan

import (
	"fmt"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// ─── initALL / initSingleNode 共用的 builder 工厂 ─────────────────────────────

// buildBinPackage 分发资源包（worker 节点，去掉本地节点）。
func buildBinPackage(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		t := &initcmd.InitBinPackage{
			DatasophonInitPath:     ctx.InitPath,
			InstallPath:            ctx.InstallPath,
			InitPathOverwriteForce: ctx.InitPathOverwriteForce,
		}
		applyRegistry(&t.TaskBase, &ctx.Cfg.Registry)
		nodes := sel(ctx)
		workers := workerHostSlice(nodes, ctx.LocalIP)
		return hostsToActions(workers, t), nil
	}
}

// buildTar 安装 tar（worker 节点）。
func buildTar(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		t := &initcmd.InitTar{PackagePath: ctx.PackagesPath}
		nodes := sel(ctx)
		workers := workerHostSlice(nodes, ctx.LocalIP)
		return hostsToActions(workers, t), nil
	}
}

// buildJdk8 安装 JDK 8（worker 节点）。
func buildJdk8(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		t := &initcmd.InitJdk8{PackagePath: ctx.PackagesPath, InstallPath: ctx.InstallPath}
		applyRegistry(&t.TaskBase, &ctx.Cfg.Registry)
		nodes := sel(ctx)
		workers := workerHostSlice(nodes, ctx.LocalIP)
		return hostsToActions(workers, t), nil
	}
}

// buildJdk21 安装 JDK 21（worker 节点，平台自身运行时）。
func buildJdk21(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		t := &initcmd.InitJdk21{PackagePath: ctx.PackagesPath, InstallPath: ctx.InstallPath}
		applyRegistry(&t.TaskBase, &ctx.Cfg.Registry)
		nodes := sel(ctx)
		workers := workerHostSlice(nodes, ctx.LocalIP)
		return hostsToActions(workers, t), nil
	}
}

// buildOfflineNodes 配置 yum/apt 离线源（所有选定节点）。
func buildOfflineNodes(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		ys := ctx.Cfg.YumServer
		reg := ctx.Cfg.Registry
		t := &initcmd.InitOfflineSlave{
			ServerIP:   ys.Node,
			ServerPort: ys.ListenPort,
		}
		applyConfig(&t.TaskBase, ctx.ConfigYaml)
		if reg.Enable {
			t.ServerIP = reg.Node
			t.ServerPort = reg.Config.WebPort
			applyRegistry(&t.TaskBase, &reg)
		}
		nodes := sel(ctx)
		return hostsToActions(hostsToPtr(nodes), t), nil
	}
}

// buildHostname 给选定节点设置 hostname（每节点独立 handler 实例）。
func buildHostname(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		nodes := sel(ctx)
		actions := make([]Action, 0, len(nodes))
		for i := range nodes {
			t := &initcmd.InitHostname{Hostname: nodes[i].Hostname}
			actions = append(actions, Action{HostKey: nodes[i].Hostname, Host: &nodes[i], Handler: t})
		}
		return actions, nil
	}
}

// buildAllHost 更新选定节点的 /etc/hosts（单 handler 实例复用）。
func buildAllHost(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		t := &initcmd.InitAllHost{}
		applyConfig(&t.TaskBase, ctx.ConfigYaml)
		nodes := sel(ctx)
		return hostsToActions(hostsToPtr(nodes), t), nil
	}
}

// buildNtpSlave 配置 NTP 从节点（排除 NTP Server 自身）。
func buildNtpSlave(sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		ntp := ctx.Cfg.NtpServer
		serverNode, err := requireNode(ctx.GlobalNodes, ntp.Node)
		if err != nil {
			return nil, fmt.Errorf("ntpServer 节点: %w", err)
		}
		t := &initcmd.InitNtpSlave{NtpServerIP: serverNode.IP}
		applyConfig(&t.TaskBase, ctx.ConfigYaml)
		nodes := sel(ctx)
		slaves := slavesOf(hostsToPtr(nodes), serverNode)
		return hostsToActions(slaves, t), nil
	}
}

// simpleAllNodes 对所有节点（不过滤本地）运行 handler，每节点独立创建实例。
func simpleAllNodes(newH func() handler.Handler, sel nodeSelector) BuildFunc {
	return func(ctx *BuildContext) ([]Action, error) {
		nodes := sel(ctx)
		actions := make([]Action, 0, len(nodes))
		for i := range nodes {
			actions = append(actions, Action{HostKey: nodes[i].Hostname, Host: &nodes[i], Handler: newH()})
		}
		return actions, nil
	}
}
