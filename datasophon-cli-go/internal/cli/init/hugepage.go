package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitHugePage 对应 Java InitHugePage — 关闭透明大页。
type InitHugePage struct{ TaskBase }

func (t *InitHugePage) Name() string { return "关闭透明大页" }

func (t *InitHugePage) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitHugePage) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "hugePage",
		Short: "关闭透明大页",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}

func (t *InitHugePage) doRun(exec executor.Executor) bool {
	slog.Info("开始关闭透明大页")
	osType := exec.GetOs()
	rcLocalPath := "/etc/rc.d/rc.local"
	if osType.IsUbuntu() {
		rcLocalPath = "/etc/rc.local"
	}
	if !exec.Exists(rcLocalPath).Success {
		slog.Error("文件不存在", "path", rcLocalPath)
		return false
	}
	exec.ExecShell("echo never > /sys/kernel/mm/transparent_hugepage/enabled")
	exec.ExecShell("echo never > /sys/kernel/mm/transparent_hugepage/defrag")

	r := exec.ExecShell(fmt.Sprintf(
		"egrep 'echo never > /sys/kernel/mm/transparent_hugepage/defrag' %s >&/dev/null",
		rcLocalPath,
	))
	if !r.Success {
		exec.ExecShell(fmt.Sprintf(
			"echo 'echo never > /sys/kernel/mm/transparent_hugepage/defrag' >>%s", rcLocalPath))
		exec.ExecShell(fmt.Sprintf(
			"echo 'echo never > /sys/kernel/mm/transparent_hugepage/enabled' >>%s", rcLocalPath))
	}
	slog.Info("透明大页已关闭")
	return true
}
