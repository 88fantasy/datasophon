package initcmd

import (
	"errors"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitTar 对应 Java InitTar — 确认 tar 命令已存在。
// Java 注释：TODO 默认已安装 tar，废弃，在线安装。
type InitTar struct {
	TaskBase
	PackagePath string
}

func (t *InitTar) Name() string { return "安装tar" }

func (t *InitTar) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitTar) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "tar",
		Short: "确认 tar 命令存在",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.PackagePath, "packagePath", "p", "", "安装包目录")
	return cmd
}

func (t *InitTar) doRun(exec executor.Executor) error {
	r := exec.ExecShell("which tar")
	if !r.Success {
		slog.Error("tar 命令不存在，请手动安装")
		return errors.New("tar 命令不存在")
	}
	slog.Info("tar 已存在", "path", r.Output)
	return nil
}
