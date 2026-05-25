package initcmd

import (
	"log/slog"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitFirewall struct{ TaskBase }

func (t *InitFirewall) Name() string { return "防火墙策略" }

func (t *InitFirewall) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitFirewall) doRun(exec executor.Executor) bool {
	osType := exec.GetOs()

	statusCmd := "systemctl status firewalld"
	stopCmd := "systemctl stop firewalld"
	disCmd := "systemctl disable firewalld"
	if osType.IsUbuntu() {
		statusCmd = "systemctl status ufw"
		stopCmd = "systemctl stop ufw"
		disCmd = "systemctl disable ufw"
	}

	r := exec.ExecShell(statusCmd)
	if r.Success {
		if stop := exec.ExecShell(stopCmd); !stop.Success {
			slog.Info("防火墙 stop 失败")
			return false
		}
		if dis := exec.ExecShell(disCmd); !dis.Success {
			slog.Info("防火墙 disable 失败")
			return false
		}
	}
	slog.Info("防火墙已关闭")
	return true
}

func (t *InitFirewall) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "firewall",
		Short: "关闭防火墙",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
