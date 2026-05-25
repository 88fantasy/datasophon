package executor

import (
	"errors"
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// BatchExecutor 对应 Java BatchExecutor，批量执行命令列表，失败即退出。
type BatchExecutor struct {
	exec Executor
}

func NewBatchExecutor(e Executor) *BatchExecutor {
	return &BatchExecutor{exec: e}
}

func (b *BatchExecutor) ExecBatch(cmds []string) error {
	for _, cmd := range cmds {
		slog.Info("批量执行", "cmd", cmd)
		r := b.exec.ExecShell(cmd)
		if !r.Success {
			slog.Error("批量执行失败", "cmd", cmd, "err", r.ErrOutput)
			return fmt.Errorf("批量执行失败 cmd=%s: %s", cmd, r.ErrOutput)
		}
	}
	return nil
}

// InstallSoftware 对应 Java BatchExecutor.installSoftware，按 OS 选择 yum/apt 命令。
func (b *BatchExecutor) InstallSoftware(rpmPkgs, debPkgs []string) error {
	osType := b.exec.GetOs()
	var cmds []string

	if osType.IsCentos() {
		for _, pkg := range rpmPkgs {
			cmds = append(cmds, "yum install -y "+pkg)
		}
	} else if osType.IsUbuntu() {
		cmds = append(cmds, "apt update")
		for _, pkg := range debPkgs {
			cmds = append(cmds, "apt install -y "+pkg)
		}
	} else {
		return errors.New("不支持的 OS 类型: " + string(osType))
	}

	if len(cmds) == 0 {
		slog.Warn("InstallSoftware: 未传入任何包名")
		return nil
	}
	return b.ExecBatch(cmds)
}

// CheckAndInstall 检查包是否已安装，未安装则执行 installCmd。
func CheckAndInstall(e Executor, checkCmd, installCmd string) bool {
	r := e.ExecShell(checkCmd)
	if r.Success && r.Output != "" {
		return true
	}
	ir := e.ExecShell(installCmd)
	if !ir.Success {
		slog.Error("安装失败", "cmd", installCmd, "err", ir.ErrOutput)
		return false
	}
	// 再次检查
	r2 := e.ExecShell(checkCmd)
	return r2.Success && r2.Output != ""
}

// CheckAndInstallPkg 对应 Java CliUtil.checkAndInstall，按 OS 自动选择 check/install 命令。
func CheckAndInstallPkg(e Executor, pkg string) bool {
	osType := e.GetOs()
	var checkCmd, installCmd string
	if osType == osinfo.OsTypeUbuntu22041LTS || osType.IsUbuntu() {
		checkCmd = "dpkg --list | grep " + pkg
		installCmd = "DEBIAN_FRONTEND=noninteractive apt install -y " + pkg
	} else {
		checkCmd = "rpm -qa | grep " + pkg
		installCmd = "yum install -y " + pkg
	}
	return CheckAndInstall(e, checkCmd, installCmd)
}
