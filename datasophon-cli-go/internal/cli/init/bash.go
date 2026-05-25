package initcmd

import (
	"log/slog"
	"os"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitBash struct{ TaskBase }

func (t *InitBash) Name() string { return "bash解析器设置" }

func (t *InitBash) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitBash) doRun(exec executor.Executor) bool {
	// 检查 /bin/sh 是否指向 bash
	if r := exec.ExecShell("ls -l /bin/sh"); r.Success {
		parts := strings.Split(r.Output, "->")
		if len(parts) == 2 {
			target := strings.TrimSpace(parts[1])
			if target != "bash" {
				if s := exec.ExecShell("sudo ln -svf bash /bin/sh"); !s.Success {
					slog.Info("设置 /bin/sh -> bash 失败")
					os.Exit(1)
				}
			}
		}
	}

	// 若 root 用户，修改 /etc/passwd 中的 shell
	if r := exec.ExecShell("whoami"); r.Success && strings.TrimSpace(r.Output) == "root" {
		exec.ExecShell(`sed -i 's|root:x:0:0:root:/root:/bin/sh|root:x:0:0:root:/root:/bin/bash|g' /etc/passwd`)
	}

	// 检查 $SHELL
	if r := exec.ExecShell("echo $SHELL"); !strings.Contains(r.Output, "/bin/bash") {
		slog.Error("当前用户 shell 解析器不是 bash，请检查 /etc/passwd")
		os.Exit(1)
	}

	slog.Info("bash 解析器设置完成")
	return true
}

func (t *InitBash) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "bash",
		Short: "确保 /bin/sh 指向 bash",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
