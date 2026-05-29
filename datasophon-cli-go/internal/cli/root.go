package cli

import (
	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/create"
	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/upload"
)

// Root 返回 Cobra 根命令，对应 Java Main 中 @Command(subcommands=…)。
// dryRun 通过指针向下传递，使所有子命令共用同一个 --dry-run 状态。
func Root() *cobra.Command {
	var dryRun bool

	root := &cobra.Command{
		Use:   "datasophon-cli",
		Short: "Datasophon 节点管理 CLI",
		Long: `datasophon-cli 是 Datasophon 集群管理平台的命令行工具，
用于节点初始化、集群创建与维护。`,
		SilenceUsage: true,
	}

	root.PersistentFlags().BoolVar(&dryRun, "dry-run", false, "仅打印命令，不实际执行")

	root.AddCommand(create.NewCreateCommand(&dryRun))
	root.AddCommand(initcmd.NewInitCommand(&dryRun))
	root.AddCommand(upload.NewUploadCommand(&dryRun))

	return root
}
