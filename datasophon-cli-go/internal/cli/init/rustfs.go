package initcmd

import (
	"errors"
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
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

func (t *InitRustfs) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

// Run 导出 doRun，供 create 包手动模式/配置模式直接调用。
func (t *InitRustfs) Run(exec executor.Executor) error { return t.doRun(exec) }

func (t *InitRustfs) doRun(exec executor.Executor) error {
	if !t.Enable {
		slog.Info("rustfs enable=false，跳过")
		return nil
	}
	if !exec.Exists(t.InstallPath).Success {
		slog.Error("安装目录不存在", "path", t.InstallPath)
		return errors.New("rustfs 安装目录不存在")
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
			return errors.New("rustfs 安装包不存在")
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
		return nil
	}
	slog.Error("rustfs 启动失败", "path", home)
	return errors.New("rustfs 启动失败")
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
