package initcmd

import (
	"log/slog"
	"os"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitNmap 对应 Java InitNmap — 安装 nmap。
type InitNmap struct{ TaskBase }

func (t *InitNmap) Name() string { return "nmap安装" }

func (t *InitNmap) Handle(client *ssh.Client, dryRun bool) bool {
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

func (t *InitNmap) doRun(exec executor.Executor) bool {
	slog.Info("安装 nmap")
	ok := executor.CheckAndInstallPkg(exec, "nmap")
	if !ok {
		slog.Error("nmap 安装失败")
		os.Exit(1)
	}
	slog.Info("nmap 安装完成")
	return true
}
