package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitAllHost struct{ TaskBase }

func (t *InitAllHost) Name() string { return "初始化hosts" }

func (t *InitAllHost) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitAllHost) doRun(exec executor.Executor) bool {
	slog.Info("开始修改 /etc/hosts")

	// 删除上次写入的 hosts 块
	exec.ExecShell(`sed -i '/#modify etc hosts start/,/#modify etc hosts end/d' /etc/hosts`)
	exec.ExecShell(`echo '#modify etc hosts start' >>/etc/hosts`)

	cfg, err := t.GetConfig()
	if err != nil {
		slog.Error("读取配置失败", "err", err)
		return false
	}

	for _, node := range cfg.Nodes {
		exec.ExecShell(fmt.Sprintf("echo %s %s >>/etc/hosts", node.IP, node.Hostname))
	}
	for _, node := range cfg.AddNodes {
		exec.ExecShell(fmt.Sprintf("echo %s %s >>/etc/hosts", node.IP, node.Hostname))
	}

	exec.ExecShell(`echo '#modify etc hosts end' >>/etc/hosts`)
	exec.ExecShell(`sed -i 's/^[^#].*[0-9]-[0-9]/#&/g' /etc/hosts`)

	slog.Info("/etc/hosts 修改完成")
	slog.Info("配置 SSH StrictHostKeyChecking")
	exec.ExecShell(`echo 'StrictHostKeyChecking no' >~/.ssh/config`)
	return true
}

func (t *InitAllHost) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "allHost",
		Short: "初始化 /etc/hosts",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
