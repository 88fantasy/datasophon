package initcmd

import (
	"errors"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitNmap 对应 Java InitNmap — 安装 nmap。
type InitNmap struct{ TaskBase }

func (t *InitNmap) Name() string { return "nmap安装" }

func (t *InitNmap) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitNmap) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "nmap",
		Short: "安装 nmap",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}

func (t *InitNmap) doRun(exec executor.Executor) error {
	slog.Info("安装 nmap")
	if !executor.CheckAndInstallPkg(exec, "nmap") {
		slog.Error("nmap 安装失败")
		return errors.New("nmap 安装失败")
	}
	slog.Info("nmap 安装完成")
	return nil
}
