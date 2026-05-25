package executor_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

func TestLocalExecutor_ExecShell(t *testing.T) {
	exec := executor.NewLocalExecutor(false)
	result := exec.ExecShell("echo hello")
	assert.True(t, result.Success)
	assert.Contains(t, result.Output, "hello")
}

func TestLocalExecutor_ExecShell_DryRun(t *testing.T) {
	exec := executor.NewLocalExecutor(true)
	result := exec.ExecShell("echo hello")
	// dry-run 下不实际执行，Success 仍为 true（不执行不等于失败）
	assert.True(t, result.Success)
}

func TestLocalExecutor_Exists_File(t *testing.T) {
	exec := executor.NewLocalExecutor(false)
	// /etc/hosts 在所有 Unix 系统上都应该存在
	result := exec.Exists("/etc/hosts")
	assert.True(t, result.Success)
}

func TestLocalExecutor_Exists_Missing(t *testing.T) {
	exec := executor.NewLocalExecutor(false)
	result := exec.Exists("/nonexistent/path/file.txt")
	assert.False(t, result.Success)
}

func TestLocalExecutor_GetFileString(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "test.txt")
	require.NoError(t, os.WriteFile(path, []byte("hello world"), 0o644))

	exec := executor.NewLocalExecutor(false)
	result := exec.GetFileString(path)
	assert.True(t, result.Success)
	assert.Equal(t, "hello world", result.Output)
}

func TestLocalExecutor_WriteLines(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "out.txt")

	exec := executor.NewLocalExecutor(false)
	result := exec.WriteLines([]string{"line1", "line2", "line3"}, path)
	assert.True(t, result.Success)

	data, err := os.ReadFile(path)
	require.NoError(t, err)
	content := string(data)
	assert.True(t, strings.Contains(content, "line1"))
	assert.True(t, strings.Contains(content, "line3"))
}

func TestLocalExecutor_SendFile(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "src.txt")
	dst := filepath.Join(dir, "dst.txt")
	require.NoError(t, os.WriteFile(src, []byte("data"), 0o644))

	exec := executor.NewLocalExecutor(false)
	result := exec.SendFile(src, dst, false)
	assert.True(t, result.Success)

	data, err := os.ReadFile(dst)
	require.NoError(t, err)
	assert.Equal(t, "data", string(data))
}

func TestLocalExecutor_GetArch(t *testing.T) {
	exec := executor.NewLocalExecutor(false)
	arch := exec.GetArch()
	// 结果不为空即可（实际架构取决于运行机器）
	assert.NotEmpty(t, string(arch))
}

func TestLocalExecutor_GetOs(t *testing.T) {
	exec := executor.NewLocalExecutor(false)
	osType := exec.GetOs()
	assert.NotEmpty(t, string(osType))
}
