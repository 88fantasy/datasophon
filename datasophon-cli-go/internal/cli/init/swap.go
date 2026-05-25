package initcmd

import (
	"log/slog"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

type InitSwap struct{ TaskBase }

func (t *InitSwap) Name() string { return "关闭swap分区" }

func (t *InitSwap) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitSwap) doRun(exec executor.Executor) bool {
	if r := exec.ExecShell(`sed -ri 's/.*swap.*/#&/' /etc/fstab`); !r.Success {
		return false
	}
	if r := exec.ExecShell(`echo 0 >/proc/sys/vm/swappiness`); !r.Success {
		return false
	}

	// 修改 /etc/sysctl.conf 中的 vm.swappiness
	if r := exec.GetFileString("/etc/sysctl.conf"); r.Success {
		lines := strings.Split(r.Output, "\n")
		found := false
		for i, l := range lines {
			if strings.HasPrefix(l, "vm.swappiness") {
				lines[i] = "vm.swappiness=0"
				found = true
				break
			}
		}
		if !found {
			lines = append(lines, "vm.swappiness=0")
		}
		exec.WriteLines(lines, "/etc/sysctl.conf")
	}

	if r := exec.ExecShell("sysctl vm.swappiness=0"); !r.Success {
		return false
	}
	if r := exec.ExecShell("swapoff -a && swapon -a"); !r.Success {
		return false
	}
	if r := exec.ExecShell("sysctl -p"); !r.Success {
		return false
	}
	slog.Info("Swap 已关闭")
	return true
}

func (t *InitSwap) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "swap",
		Short: "关闭 Swap 分区",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
