package upload

import "github.com/spf13/cobra"

// NewUploadCommand 注册 upload 命令组，对齐 create.NewCreateCommand 的样式。
func NewUploadCommand(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "upload",
		Short: "上传相关命令组",
	}
	cmd.AddCommand((&UploadRegistry{}).Command(dryRun))
	return cmd
}
