package initcmd

import (
	"log/slog"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitHostname struct {
	TaskBase
	Hostname string
}

func (t *InitHostname) Name() string { return "初始化hostname" }

func (t *InitHostname) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitHostname) doRun(exec executor.Executor) bool {
	exec.ExecShell("echo " + t.Hostname + " >/etc/hostname")
	if exec.Exists("/etc/sysconfig/network").Success {
		exec.ExecShell("echo HOSTNAME=" + t.Hostname + " >/etc/sysconfig/network")
		exec.ExecShell("echo NOZEROCONF=yes >>/etc/sysconfig/network")
	} else {
		slog.Warn("/etc/sysconfig/network 不存在")
	}
	exec.ExecShell("hostnamectl set-hostname " + t.Hostname)
	exec.ExecShell("hostnamectl set-hostname --static " + t.Hostname)

	r := exec.ExecShell("hostname")
	if !r.Success || strings.TrimSpace(r.Output) != t.Hostname {
		slog.Error("hostname 设置失败", "expected", t.Hostname, "actual", r.Output)
		return false
	}
	slog.Info("hostname 设置完成", "hostname", t.Hostname)
	return true
}

func (t *InitHostname) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "hostname",
		Short: "设置主机名",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.Hostname, "hostname", "H", "", "主机名 (必填)")
	_ = cmd.MarkFlagRequired("hostname")
	return cmd
}
