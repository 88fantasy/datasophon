package initcmd

import (
	"io"
	"path/filepath"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type writeLinesRecordingExec struct {
	writtenLines []string
	writtenPath  string
	fail         bool
}

func (m *writeLinesRecordingExec) ExecShell(_ string) executor.ExecResult {
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) Exists(_ string) executor.ExecResult { return executor.ExecResult{} }
func (m *writeLinesRecordingExec) SendFile(_, _ string, _ bool) executor.ExecResult {
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) SendDir(_, _ string, _ bool) executor.ExecResult {
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) GetFileString(_ string) executor.ExecResult {
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) WriteFromStream(_ io.Reader, _ string) executor.ExecResult {
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) WriteLines(lines []string, path string) executor.ExecResult {
	if m.fail {
		return executor.Fail("write failed")
	}
	m.writtenLines = lines
	m.writtenPath = path
	return executor.Succeed("")
}
func (m *writeLinesRecordingExec) GetArch() osinfo.ArchType { return osinfo.ArchX86_64 }
func (m *writeLinesRecordingExec) GetOs() osinfo.OsType     { return osinfo.OsTypeCentos7 }

// TestInitWorkerLocalProperties_WritesMysqlIpAndPassword 覆盖回归场景：conf/worker.properties
// 里 mysql.ip 写死 127.0.0.1、mysql.password 为空，假设 MySQL 与 Worker 同机部署；多节点集群
// 里除 MySQL 所在节点外，InitDbHookAction 会用这个默认值连接 MySQL 全部失败。本 handler 必须
// 把真实的 MySQL IP/密码写进 worker.local.properties 覆盖掉默认值。
func TestInitWorkerLocalProperties_WritesMysqlIpAndPassword(t *testing.T) {
	exec := &writeLinesRecordingExec{}
	task := &InitWorkerLocalProperties{
		InstallPath:   "/data/install_datasophon",
		MysqlIP:       "192.168.10.131",
		MysqlPassword: "mysql-secret",
	}

	require.NoError(t, task.doRun(exec))

	assert.Equal(t,
		filepath.Join("/data/install_datasophon", "datasophon-worker/conf/worker.local.properties"),
		exec.writtenPath)
	assert.Contains(t, exec.writtenLines, "mysql.ip=192.168.10.131")
	assert.Contains(t, exec.writtenLines, "mysql.password=mysql-secret")
}

func TestInitWorkerLocalProperties_WriteFailurePropagatesError(t *testing.T) {
	exec := &writeLinesRecordingExec{fail: true}
	task := &InitWorkerLocalProperties{
		InstallPath:   "/data/install_datasophon",
		MysqlIP:       "192.168.10.131",
		MysqlPassword: "mysql-secret",
	}

	err := task.doRun(exec)
	require.Error(t, err)
}
