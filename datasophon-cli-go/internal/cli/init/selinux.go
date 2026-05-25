package initcmd

import (
	"log/slog"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitSelinux struct{ TaskBase }

func (t *InitSelinux) Name() string { return "关闭安全策略" }

func (t *InitSelinux) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitSelinux) doRun(exec executor.Executor) bool {
	r := exec.ExecShell("getenforce")
	if !r.Success {
		return false
	}
	if strings.TrimSpace(r.Output) == "Enforcing" {
		slog.Info("正在关闭 SELinux")
		if stop := exec.ExecShell("setenforce 0"); !stop.Success {
			slog.Info("SELinux disable 失败")
		}
		if sed := exec.ExecShell(`sed -i "s/SELINUX=enforcing/SELINUX=disabled/g" /etc/selinux/config`); !sed.Success {
			slog.Info("SELinux config 修改失败")
			return true // 对齐 Java 行为：sed 失败仍返回 true
		}
	}
	slog.Info("SELinux 已关闭")
	return true
}

func (t *InitSelinux) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "selinux",
		Short: "关闭 SELinux",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
