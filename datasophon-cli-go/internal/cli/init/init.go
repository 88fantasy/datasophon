package initcmd

import (
	"github.com/spf13/cobra"
)

// NewInitCommand 对应 Java Init.java，注册所有公开的 init 子命令。
func NewInitCommand(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "init",
		Short: "节点初始化命令组",
	}

	// ── Phase 1: 10 个核心任务 ────────────────────────────────────────────
	cmd.AddCommand((&InitFirewall{}).Command(dryRun))
	cmd.AddCommand((&InitSelinux{}).Command(dryRun))
	cmd.AddCommand((&InitSwap{}).Command(dryRun))
	cmd.AddCommand((&InitHostname{}).Command(dryRun))
	cmd.AddCommand((&InitAllHost{}).Command(dryRun))
	cmd.AddCommand((&InitOsUser{}).Command(dryRun))
	cmd.AddCommand((&InitLibrary{}).Command(dryRun))
	cmd.AddCommand((&InitBash{}).Command(dryRun))
	cmd.AddCommand((&InitJdk17{}).Command(dryRun))
	cmd.AddCommand(NewInitSshFreeCommand(dryRun))

	// ── Phase 2: 补齐剩余 init 任务 ──────────────────────────────────────

	// 系统基础配置
	cmd.AddCommand((&InitHugePage{}).Command(dryRun))
	cmd.AddCommand((&InitNmap{}).Command(dryRun))
	cmd.AddCommand((&InitTar{}).Command(dryRun))
	cmd.AddCommand((&InitSystemConf{}).Command(dryRun))
	cmd.AddCommand((&InitOsSafeConf{}).Command(dryRun))
	cmd.AddCommand((&InitBinPackage{}).Command(dryRun))

	// JDK
	cmd.AddCommand((&InitJdk8{}).Command(dryRun))

	// NTP 时钟同步
	cmd.AddCommand((&InitNtpServer{}).Command(dryRun))
	cmd.AddCommand((&InitNtpSlave{}).Command(dryRun))

	// 离线源
	cmd.AddCommand((&InitOfflineServer{}).Command(dryRun))
	cmd.AddCommand((&InitOfflineSlave{}).Command(dryRun))

	// MySQL
	cmd.AddCommand((&InitMysql{}).Command(dryRun))
	cmd.AddCommand((&InitMysqlAppDb{}).Command(dryRun))

	// 制品库 (Nexus)
	cmd.AddCommand((&InitRegistryDecode{}).Command(dryRun))

	// 对象存储 (Rustfs)
	cmd.AddCommand((&InitRustfs{}).Command(dryRun))

	// K8s 相关
	cmd.AddCommand((&InitDocker{}).Command(dryRun))
	cmd.AddCommand((&InitHelm{}).Command(dryRun))
	cmd.AddCommand((&InitHelmify{}).Command(dryRun))
	cmd.AddCommand((&InitKubectl{}).Command(dryRun))
	cmd.AddCommand((&InitK8sBaseServices{}).Command(dryRun))
	cmd.AddCommand((&InitK8sRegistryConf{}).Command(dryRun))
	cmd.AddCommand((&InitK8sKuboard{}).Command(dryRun))

	return cmd
}
