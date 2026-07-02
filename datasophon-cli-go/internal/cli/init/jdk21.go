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
	jdk21FolderPath     = "/usr/local"
	jdk21PathName       = "jdk21"
	jdk21ExtractDirName = "jdk-21.0.11+10"
	jdk21TarX86         = "OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz"
	jdk21TarArm         = "OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.11_10.tar.gz"
)

// InitJdk21 安装 Eclipse Temurin OpenJDK 21.0.11（Datasophon 平台自身运行时，取代原 InitJdk17）。
type InitJdk21 struct {
	TaskBase
	PackagePath string
	InstallPath string
}

func (t *InitJdk21) Name() string { return "初始化jdk21" }

func (t *InitJdk21) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitJdk21) doRun(exec executor.Executor) error {
	exec.ExecShell("source /etc/profile")

	tarName := jdk21TarX86
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = jdk21TarArm
	}

	javaBin := fmt.Sprintf("%s/%s/bin/java", jdk21FolderPath, jdk21PathName)
	if exec.Exists(javaBin).Success {
		slog.Info("JDK21 已安装", "path", javaBin)
		return nil
	}

	slog.Info("JDK21 未安装，开始安装")
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	if err := DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		tarName, tarPath, true); err != nil {
		return err
	}

	// 清理旧的环境变量设置
	exec.ExecShell(`sed -i '/export JAVA_HOME/d' /etc/profile`)
	exec.ExecShell(`sed -i '/export CLASSPATH/d' /etc/profile`)
	exec.ExecShell(`sed -i '/export PATH=\$PATH:\$JAVA_HOME/d' /etc/profile`)
	exec.ExecShell(`sed -i '/source \/etc\/profile/d' /root/.bash_profile`)
	exec.ExecShell(`sed -i '/source \/etc\/profile/d' /root/.bashrc`)

	// 解压到 installPath（与 docker/rustfs 等其他组件同处一个安装根目录），
	// 再软链到版本无关的固定别名 jdk21FolderPath/jdk21PathName（与 tar 包内版本号解耦）
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.InstallPath))
	exec.ExecShell(fmt.Sprintf("tar -zxf %s -C %s", tarPath, t.InstallPath))
	exec.ExecShell(fmt.Sprintf("rm -rf %s/%s && ln -s %s/%s %s/%s",
		jdk21FolderPath, jdk21PathName, t.InstallPath, jdk21ExtractDirName, jdk21FolderPath, jdk21PathName))

	javaHome := fmt.Sprintf("%s/%s", jdk21FolderPath, jdk21PathName)
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell(`echo 'export PATH=$PATH:$JAVA_HOME/bin' >>/etc/profile`)
	exec.ExecShell(`echo 'source /etc/profile' >>~/.bash_profile`)
	exec.ExecShell(`echo 'source /etc/profile' >>~/.bashrc`)

	exec.ExecShell("source /root/.bash_profile")
	exec.ExecShell("source /root/.bashrc")
	exec.ExecShell("source /etc/profile")

	slog.Info("JDK21 安装完成")
	return nil
}

func (t *InitJdk21) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "jdk21",
		Short: "安装 OpenJDK 21",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.PackagePath, "packagePath", "p", "", "安装包所在目录 (必填)")
	_ = cmd.MarkFlagRequired("packagePath")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	_ = cmd.MarkFlagRequired("installPath")
	return cmd
}
