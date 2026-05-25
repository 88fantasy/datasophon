package initcmd

import (
	"errors"
	"log/slog"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

const hadoopGroup = "hadoop"
const hadoopUser = "hadoop"

type InitOsUser struct{ TaskBase }

func (t *InitOsUser) Name() string { return "初始化用户" }

func (t *InitOsUser) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitOsUser) doRun(exec executor.Executor) error {
	// 创建 hadoop 组
	if r := exec.ExecShell("egrep '^" + hadoopGroup + "' /etc/group >&/dev/null"); !r.Success {
		if gr := exec.ExecShell("groupadd " + hadoopGroup); gr.Success {
			slog.Info("创建组成功", "group", hadoopGroup)
		} else {
			slog.Error("创建组失败", "group", hadoopGroup)
			return errors.New("创建 hadoop 组失败")
		}
	}

	// 创建 hadoop 用户
	if r := exec.ExecShell(`egrep "^` + hadoopUser + `" /etc/passwd >&/dev/null`); !r.Success {
		if ur := exec.ExecShell("useradd --shell /bin/bash -g " + hadoopGroup + " " + hadoopUser); ur.Success {
			slog.Info("创建用户成功", "user", hadoopUser)
		} else {
			slog.Error("创建用户失败", "user", hadoopUser)
			return errors.New("创建 hadoop 用户失败")
		}
	}

	exec.ExecShell("mkdir -p /home/" + hadoopUser + "/")
	exec.ExecShell("cp -r /root/.ssh /home/" + hadoopUser + "/")
	exec.ExecShell("chown -R " + hadoopUser + ":" + hadoopGroup + " /home/" + hadoopUser + "/.ssh/")

	slog.Info("hadoop 用户初始化完成")
	return nil
}

func (t *InitOsUser) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "osUser",
		Short: "创建 hadoop 用户和组",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
