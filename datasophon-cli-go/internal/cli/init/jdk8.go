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

// jdk8ExtractDirName 是 Temurin JDK8 tar 包实际解压出的顶层目录名；
// 解压到 InstallPath（与 docker/rustfs 等其他组件同处一个安装根目录）后，
// 软链到版本无关的固定别名 /usr/local/jdk8，与 tar 包内实际版本号解耦，
// 升级 JDK 补丁版本时不需要连带改 hadoop-env.ftl / dolphinscheduler_env.ftl /
// InstallJDKHandler.java 等消费方。
const jdk8ExtractDirName = "jdk8u492-b09"

// InitJdk8 对应 Java InitJdk8 — 安装 Eclipse Temurin OpenJDK 8u492。
type InitJdk8 struct {
	TaskBase
	PackagePath string
	InstallPath string
}

func (t *InitJdk8) Name() string { return "初始化jdk8" }

func (t *InitJdk8) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitJdk8) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "jdk8",
		Short: "安装 OpenJDK 8（Eclipse Temurin 8u492）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	_ = cmd.MarkFlagRequired("installPath")
	return cmd
}

func (t *InitJdk8) doRun(exec executor.Executor) error {
	jdkFolderPath := "/usr/local"
	jdkPathName := "jdk8"
	jdkTarName := "OpenJDK8U-jdk_x64_linux_hotspot_8u492b09.tar.gz"
	if exec.GetArch() == osinfo.ArchAarch64 {
		jdkTarName = "OpenJDK8U-jdk_aarch64_linux_hotspot_8u492b09.tar.gz"
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

	// 解压到 installPath（与 docker/rustfs 等其他组件同处一个安装根目录），
	// 再软链到版本无关的固定别名 jdkFolderPath/jdkPathName
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.InstallPath))
	exec.ExecShell(fmt.Sprintf("tar -zxf %s/%s -C %s", t.PackagePath, jdkTarName, t.InstallPath))
	exec.ExecShell(fmt.Sprintf("rm -rf %s/%s && ln -s %s/%s %s/%s",
		jdkFolderPath, jdkPathName, t.InstallPath, jdk8ExtractDirName, jdkFolderPath, jdkPathName))

	// 配置环境变量
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell(fmt.Sprintf("echo 'export JAVA8_HOME=%s' >>/etc/profile", javaHome))
	exec.ExecShell("echo 'export PATH=$PATH:$JAVA_HOME/bin' >>/etc/profile")
	exec.ExecShell("echo 'source /etc/profile' >>~/.bash_profile")
	exec.ExecShell("echo 'source /etc/profile' >>~/.bashrc")

	// 安装 bcprov（可选增强：放宽 TLS1.0/1.1 算法限制，非 JDK8 核心功能所需，
	// 下载失败仅警告、不阻塞整体安装——没有 bcprov 时也不做 TLS 配置，避免
	// java.security 里禁用列表被改动但缺少对应算法实现）。
	slog.Info("配置 BCPROV")
	javaBcprovDir := fmt.Sprintf("%s/jre/lib/ext/", javaHome)
	javaBcprovJarName := "bcprov-jdk15on-1.68.jar"
	javaBcprovJar := fmt.Sprintf("%s/%s", t.PackagePath, javaBcprovJarName)
	if err := DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		javaBcprovJarName, javaBcprovJar, true); err != nil {
		slog.Warn("BCPROV 下载失败，跳过 TLS 算法放宽", "err", err)
	} else {
		exec.ExecShell(fmt.Sprintf("cp -a %s %s", javaBcprovJar, javaBcprovDir))
		slog.Info("BCPROV 安装完成")

		// 修改 TLS 配置
		exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/, TLSv1//' %s/jre/lib/security/java.security", javaHome))
		exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/TLSv1,//' %s/jre/lib/security/java.security", javaHome))
		exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/, TLSv1.1//' %s/jre/lib/security/java.security", javaHome))
		exec.ExecShell(fmt.Sprintf("sed -i '/jdk.tls.disabledAlgorithms=/ s/TLSv1.1,//' %s/jre/lib/security/java.security", javaHome))
	}

	exec.ExecShell("source /root/.bash_profile")
	exec.ExecShell("source /root/.bashrc")
	exec.ExecShell("source /etc/profile")
	slog.Info("JDK8 安装完成")
	return nil
}
