package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

const (
	jdk17FolderPath = "/usr/local"
	jdk17PathName   = "jdk-17.0.1"
	jdk17TarX86     = "openjdk-17.0.1_linux-x64_bin.tar.gz"
	jdk17TarArm     = "openjdk-17.0.1_linux-aarch64_bin.tar.gz"
)

type InitJdk17 struct {
	TaskBase
	PackagePath string
}

func (t *InitJdk17) Name() string { return "初始化jdk17" }

func (t *InitJdk17) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitJdk17) doRun(exec executor.Executor) error {
	exec.ExecShell("source /etc/profile")

	tarName := jdk17TarX86
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = jdk17TarArm
	}

	javaBin := fmt.Sprintf("%s/%s/bin/java", jdk17FolderPath, jdk17PathName)
	if exec.Exists(javaBin).Success {
		slog.Info("JDK17 已安装", "path", javaBin)
		return nil
	}

	slog.Info("JDK17 未安装，开始安装")
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)

	// 清理旧的环境变量设置
	exec.ExecShell(`sed -i '/export JAVA17_HOME/d' /etc/profile`)
	exec.ExecShell(`sed -i '/export CLASSPATH/d' /etc/profile`)
	exec.ExecShell(`sed -i '/export PATH=\$PATH:\$JAVA17_HOME/d' /etc/profile`)
	exec.ExecShell(`sed -i '/source \/etc\/profile/d' /root/.bash_profile`)
	exec.ExecShell(`sed -i '/source \/etc\/profile/d' /root/.bashrc`)

	exec.ExecShell(fmt.Sprintf("mkdir -p %s", jdk17FolderPath))
	exec.ExecShell(fmt.Sprintf("tar -zxf %s -C %s", tarPath, jdk17FolderPath))

	javaHome := fmt.Sprintf("%s/%s", jdk17FolderPath, jdk17PathName)
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA17_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell(`echo 'source /etc/profile' >>~/.bash_profile`)
	exec.ExecShell(`echo 'source /etc/profile' >>~/.bashrc`)

	exec.ExecShell("source /root/.bash_profile")
	exec.ExecShell("source /root/.bashrc")
	exec.ExecShell("source /etc/profile")

	slog.Info("JDK17 安装完成")
	return nil
}

func (t *InitJdk17) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "jdk17",
		Short: "安装 OpenJDK 17",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.PackagePath, "packagePath", "p", "", "安装包所在目录 (必填)")
	_ = cmd.MarkFlagRequired("packagePath")
	return cmd
}
