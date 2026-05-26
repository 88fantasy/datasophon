package create

import "github.com/spf13/cobra"

// NewCreateCommand 对应 Java Create.java。
func NewCreateCommand(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "create",
		Short: "创建集群相关命令组",
	}
	cmd.AddCommand(NewClusterCommand(dryRun))
	cmd.AddCommand(NewNodeCommand(dryRun))
	cmd.AddCommand(NewConfigCommand())
	return cmd
}