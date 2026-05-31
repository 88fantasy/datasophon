package executor

import (
	"io"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// ─── mock Executor ────────────────────────────────────────────────────────────

type mockExecutor struct {
	shellFn func(cmd string) ExecResult
	calls   []string
	osType  osinfo.OsType
	arch    osinfo.ArchType
}

func (m *mockExecutor) ExecShell(cmd string) ExecResult {
	m.calls = append(m.calls, cmd)
	if m.shellFn != nil {
		return m.shellFn(cmd)
	}
	return Succeed("")
}
func (m *mockExecutor) Exists(path string) ExecResult           { return Succeed("") }
func (m *mockExecutor) SendFile(_, _ string, _ bool) ExecResult { return Succeed("") }
func (m *mockExecutor) SendDir(_, _ string, _ bool) ExecResult  { return Succeed("") }
func (m *mockExecutor) GetFileString(_ string) ExecResult       { return Succeed("") }
func (m *mockExecutor) WriteFromStream(_ io.Reader, _ string) ExecResult {
	return Succeed("")
}
func (m *mockExecutor) WriteLines(_ []string, _ string) ExecResult { return Succeed("") }
func (m *mockExecutor) GetArch() osinfo.ArchType                   { return m.arch }
func (m *mockExecutor) GetOs() osinfo.OsType                       { return m.osType }

// ─── ExecBatch ────────────────────────────────────────────────────────────────

func TestExecBatch_Success(t *testing.T) {
	exec := &mockExecutor{shellFn: func(_ string) ExecResult { return Succeed("ok") }}
	b := NewBatchExecutor(exec)
	require.NoError(t, b.ExecBatch([]string{"cmd1", "cmd2", "cmd3"}))
	assert.Equal(t, []string{"cmd1", "cmd2", "cmd3"}, exec.calls)
}

func TestExecBatch_FailShortCircuits(t *testing.T) {
	exec := &mockExecutor{shellFn: func(cmd string) ExecResult {
		if cmd == "cmd2" {
			return Fail("bad")
		}
		return Succeed("ok")
	}}
	b := NewBatchExecutor(exec)
	err := b.ExecBatch([]string{"cmd1", "cmd2", "cmd3"})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "cmd2")
	// cmd3 不应被调用
	assert.Equal(t, []string{"cmd1", "cmd2"}, exec.calls)
}

func TestExecBatch_EmptyCommands(t *testing.T) {
	exec := &mockExecutor{}
	b := NewBatchExecutor(exec)
	assert.NoError(t, b.ExecBatch(nil))
	assert.Empty(t, exec.calls)
}

// ─── InstallSoftware ──────────────────────────────────────────────────────────

func TestInstallSoftware_Centos_Yum(t *testing.T) {
	exec := &mockExecutor{
		osType:  osinfo.OsTypeCentos7,
		shellFn: func(_ string) ExecResult { return Succeed("ok") },
	}
	b := NewBatchExecutor(exec)
	require.NoError(t, b.InstallSoftware([]string{"pkg1", "pkg2"}, nil))
	assert.Equal(t, []string{"yum install -y pkg1", "yum install -y pkg2"}, exec.calls)
}

func TestInstallSoftware_Ubuntu_Apt(t *testing.T) {
	exec := &mockExecutor{
		osType:  osinfo.OsTypeUbuntu22041LTS,
		shellFn: func(_ string) ExecResult { return Succeed("ok") },
	}
	b := NewBatchExecutor(exec)
	require.NoError(t, b.InstallSoftware(nil, []string{"pkg1"}))
	assert.Equal(t, []string{"apt update", "apt install -y pkg1"}, exec.calls)
}

func TestInstallSoftware_EmptyPackages_Centos(t *testing.T) {
	exec := &mockExecutor{osType: osinfo.OsTypeCentos7}
	b := NewBatchExecutor(exec)
	assert.NoError(t, b.InstallSoftware(nil, nil))
	assert.Empty(t, exec.calls)
}

func TestInstallSoftware_UnsupportedOS(t *testing.T) {
	exec := &mockExecutor{osType: osinfo.OsTypeAuto}
	b := NewBatchExecutor(exec)
	err := b.InstallSoftware([]string{"pkg"}, []string{"pkg"})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "不支持的 OS 类型")
}

// ─── CheckAndInstall ─────────────────────────────────────────────────────────

func TestCheckAndInstall_AlreadyInstalled(t *testing.T) {
	exec := &mockExecutor{shellFn: func(cmd string) ExecResult {
		return Succeed("found") // check 命令成功且有输出 → 跳过
	}}
	result := CheckAndInstall(exec, "check-cmd", "install-cmd")
	assert.True(t, result)
	assert.Equal(t, []string{"check-cmd"}, exec.calls)
}

func TestCheckAndInstall_InstallAndVerify(t *testing.T) {
	callCount := 0
	exec := &mockExecutor{shellFn: func(cmd string) ExecResult {
		callCount++
		switch callCount {
		case 1: // 第一次 check 失败
			return ExecResult{Success: false}
		case 2: // install 成功
			return Succeed("")
		case 3: // 二次 check 成功
			return Succeed("now installed")
		}
		return Succeed("")
	}}
	result := CheckAndInstall(exec, "check-cmd", "install-cmd")
	assert.True(t, result)
	assert.Len(t, exec.calls, 3)
}

// ─── CheckAndInstallPkg ───────────────────────────────────────────────────────

func TestCheckAndInstallPkg_Ubuntu(t *testing.T) {
	exec := &mockExecutor{
		osType:  osinfo.OsTypeUbuntu22041LTS,
		shellFn: func(_ string) ExecResult { return Succeed("installed") },
	}
	result := CheckAndInstallPkg(exec, "curl")
	assert.True(t, result)
	require.Len(t, exec.calls, 1)
	assert.Contains(t, exec.calls[0], "dpkg")
}

func TestCheckAndInstallPkg_Centos(t *testing.T) {
	exec := &mockExecutor{
		osType:  osinfo.OsTypeCentos7,
		shellFn: func(_ string) ExecResult { return Succeed("installed") },
	}
	result := CheckAndInstallPkg(exec, "curl")
	assert.True(t, result)
	require.Len(t, exec.calls, 1)
	assert.Contains(t, exec.calls[0], "rpm")
}
