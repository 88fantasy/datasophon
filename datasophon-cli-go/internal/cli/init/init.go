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

	// 第一版 10 个核心任务
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

	// TODO Phase 2: nmap/ntpServer/ntpSlave/offlineServer/offlineSlave/
	//               binPackage/tar/systemConf/hugePage/registry/registryUpload/
	//               registryDecode/rustfs/mysql/mysqlAppDb/osSafeConf/osUser/
	//               k8sBaseServices/k8sKuboard/k8sRegistryConf/docker/helm/helmify/kubectl

	return cmd
}
