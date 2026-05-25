package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitRustfs 对应 Java InitRustfs — 安装并启动 Rustfs 对象存储。
type InitRustfs struct {
	TaskBase
	Enable      bool
	PackagePath string
	InstallPath string
	X86Tar      string
	Aarch64Tar  string
	WebHost     string
	WebPort     string
	APIPort     string
	Username    string
	Password    string
}

func (t *InitRustfs) Name() string { return "安装rustfs" }

func (t *InitRustfs) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitRustfs) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "rustfs",
		Short: "安装并启动 Rustfs 对象存储",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVarP(&t.Enable, "enable", "e", false, "是否安装")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	cmd.Flags().StringVarP(&t.X86Tar, "x86Tar", "x", "", "x86_64 包名")
	cmd.Flags().StringVarP(&t.Aarch64Tar, "aarch64Tar", "a", "", "aarch64 包名")
	cmd.Flags().StringVar(&t.WebHost, "webHost", "", "Web 主机（必填）")
	cmd.Flags().StringVar(&t.WebPort, "webPort", "", "Web 端口（必填）")
	cmd.Flags().StringVar(&t.APIPort, "apiPort", "", "API 端口（必填）")
	cmd.Flags().StringVarP(&t.Username, "username", "u", "", "访问密钥（必填）")
	cmd.Flags().StringVarP(&t.Password, "password", "p", "", "密钥（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("webHost")
	_ = cmd.MarkFlagRequired("webPort")
	_ = cmd.MarkFlagRequired("apiPort")
	_ = cmd.MarkFlagRequired("username")
	_ = cmd.MarkFlagRequired("password")
	return cmd
}

func (t *InitRustfs) doRun(exec executor.Executor) bool {
	if !t.Enable {
		slog.Info("rustfs enable=false，跳过")
		return true
	}
	if !exec.Exists(t.InstallPath).Success {
		slog.Error("安装目录不存在", "path", t.InstallPath)
		return false
	}

	home := fmt.Sprintf("%s/rustfs", t.InstallPath)
	dataPath := fmt.Sprintf("%s/data", home)
	logsPath := fmt.Sprintf("%s/logs", home)

	if exec.Exists(home).Success {
		slog.Info("rustfs 目录已存在", "path", home)
	} else {
		tarName := t.X86Tar
		if exec.GetArch() == osinfo.ArchAarch64 {
			tarName = t.Aarch64Tar
		}
		tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
		if !exec.Exists(tarPath).Success {
			slog.Error("安装包不存在", "path", tarPath)
			return false
		}
		exec.ExecShell(fmt.Sprintf("tar xvz -f %s -C %s", tarPath, t.InstallPath))
		exec.ExecShell(fmt.Sprintf("mv %s/rustfs-* %s", t.InstallPath, home))
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", dataPath))
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", logsPath))
	}

	if !t.checkStart(exec) {
		t.start(exec, home, dataPath, logsPath)
		exec.ExecShell("sleep 3")
	}

	if t.checkStart(exec) {
		slog.Info("rustfs 安装成功", "path", home)
		return true
	}
	slog.Error("rustfs 启动失败", "path", home)
	return false
}

func (t *InitRustfs) checkStart(exec executor.Executor) bool {
	r := exec.ExecShell("ps -ef | grep rustfs | grep -v datasophon-cli | grep -v grep")
	if r.Success {
		slog.Info("rustfs 已在运行")
		return true
	}
	slog.Info("rustfs 未在运行")
	return false
}

func (t *InitRustfs) start(exec executor.Executor, home, data, logs string) bool {
	startCmd := fmt.Sprintf(
		"%s/rustfs --address %s:%s --console-enable --console-address %s:%s"+
			" --access-key %s --secret-key %s %s > %s/rustfs.log 2>&1 &",
		home, t.WebHost, t.APIPort, t.WebHost, t.WebPort,
		t.Username, t.Password, data, logs,
	)
	startPath := fmt.Sprintf("%s/start.sh", home)
	exec.WriteLines([]string{startCmd}, startPath)
	r := exec.ExecShell(fmt.Sprintf("bash %s", startPath))
	return r.Success
}
