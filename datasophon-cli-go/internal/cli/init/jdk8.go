package initcmd

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitJdk8 对应 Java InitJdk8 — 安装 OpenJDK 8（jdk-8u333）。
type InitJdk8 struct {
	TaskBase
	PackagePath string
}

func (t *InitJdk8) Name() string { return "初始化jdk8" }

func (t *InitJdk8) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitJdk8) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "jdk8",
		Short: "安装 OpenJDK 8（jdk-8u333）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	return cmd
}

func (t *InitJdk8) doRun(exec executor.Executor) error {
	jdkFolderPath := "/usr/local"
	jdkPathName := "jdk1.8.0_333"
	jdkTarName := "jdk-8u333-linux-x64.tar.gz"
	if exec.GetArch() == osinfo.ArchAarch64 {
		jdkTarName = "jdk-8u333-linux-aarch64.tar.gz"
	}
	javaBinPath := fmt.Sprintf("%s/%s/bin/java", jdkFolderPath, jdkPathName)
	javaHome := fmt.Sprintf("%s/%s", jdkFolderPath, jdkPathName)

	r := exec.ExecShell("which java")
	if strings.TrimSpace(r.Output) == javaBinPath {
		slog.Info("JDK8 已安装", "path", javaBinPath)
		return nil
	}

	slog.Info("JDK8 未安装，开始安装")
	if err := DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		jdkTarName, fmt.Sprintf("%s/%s", t.PackagePath, jdkTarName), true); err != nil {
		return err
	}

	// 清理旧 profile
	exec.ExecShell("sed -i '/export JAVA_HOME/d' /etc/profile")
	exec.ExecShell("sed -i '/export JAVA8_HOME/d' /etc/profile")
	exec.ExecShell("sed -i '/export CLASSPATH/d' /etc/profile")
	exec.ExecShell("sed -i '/export PATH=$PATH:$JAVA_HOME/d' /etc/profile")
	exec.ExecShell("sed -i '/source \\/etc\\/profile/d' /root/.bash_profile")
	exec.ExecShell("sed -i '/source \\/etc\\/profile/d' /root/.bashrc")

	// 解压安装
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", jdkFolderPath))
	exec.ExecShell(fmt.Sprintf("tar -zxf %s/%s -C %s", t.PackagePath, jdkTarName, jdkFolderPath))

	// 配置环境变量
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA8_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell("echo 'export PATH=$PATH:$JAVA_HOME/bin' >>/etc/profile")
	exec.ExecShell("echo 'source /etc/profile' >>~/.bash_profile")
	exec.ExecShell("echo 'source /etc/profile' >>~/.bashrc")

	// 安装 bcprov
	slog.Info("配置 BCPROV")
	javaBcprovDir := fmt.Sprintf("%s/jre/lib/ext/", javaHome)
	javaBcprovJarName := "bcprov-jdk15on-1.68.jar"
	javaBcprovJar := fmt.Sprintf("%s/%s", t.PackagePath, javaBcprovJarName)
	if err := DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		javaBcprovJarName, javaBcprovJar, true); err != nil {
		return err
	}
	exec.ExecShell(fmt.Sprintf("cp -a %s %s", javaBcprovJar, javaBcprovDir))
	slog.Info("BCPROV 安装完成")

	// 修改 TLS 配置
	exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/, TLSv1//' %s/jre/lib/security/java.security", javaHome))
	exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/TLSv1,//' %s/jre/lib/security/java.security", javaHome))
	exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/, TLSv1.1//' %s/jre/lib/security/java.security", javaHome))
	exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/TLSv1.1,//' %s/jre/lib/security/java.security", javaHome))

	exec.ExecShell("source /root/.bash_profile")
	exec.ExecShell("source /root/.bashrc")
	exec.ExecShell("source /etc/profile")
	slog.Info("JDK8 安装完成")
	return nil
}
