package plan

import (
	"io"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

type rustfsRecordingExec struct {
	writtenLines   []string
	writtenPath    string
	commands       []string
	runtimeMatches bool
	fileChanged    bool
}

func (e *rustfsRecordingExec) ExecShell(command string) executor.ExecResult {
	e.commands = append(e.commands, command)
	if strings.Contains(command, "/proc/$pid/environ") && !e.runtimeMatches {
		return executor.Fail("endpoint missing")
	}
	return executor.Succeed("")
}

func TestRustfsTask_RestartsRunningProcessWhenObsEndpointChanges(t *testing.T) {
	exec := &rustfsRecordingExec{}
	task := &rustfsTask{
		Enable: true, InstallPath: "/opt", WebHost: "10.0.0.1", WebPort: "9041", APIPort: "9040",
		Username: "admin", Password: "secret", ObsEndpoint: "http://10.0.0.2:4318",
	}

	require.NoError(t, task.doRun(exec))
	commands := strings.Join(exec.commands, "\n")
	assert.Contains(t, commands, "/proc/$pid/environ")
	assert.Contains(t, commands, "kill \"$pid\"")
	assert.Contains(t, commands, "bash '/opt/rustfs/start.sh'")
}
func (e *rustfsRecordingExec) Exists(string) executor.ExecResult {
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) SendFile(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) SendDir(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) GetFileString(string) executor.ExecResult {
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) WriteFromStream(io.Reader, string) executor.ExecResult {
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) WriteLines(lines []string, path string) executor.ExecResult {
	e.writtenLines = append([]string(nil), lines...)
	e.writtenPath = path
	return executor.Succeed("")
}
func (e *rustfsRecordingExec) WriteFileAtomic(data []byte, path string, _ os.FileMode) executor.ExecResult {
	e.writtenLines = strings.Split(strings.TrimSuffix(string(data), "\n"), "\n")
	e.writtenPath = path
	if e.fileChanged {
		return executor.Succeed("changed")
	}
	return executor.Succeed("unchanged")
}
func (e *rustfsRecordingExec) GetArch() osinfo.ArchType { return osinfo.ArchX86_64 }
func (e *rustfsRecordingExec) GetOs() osinfo.OsType     { return osinfo.OsTypeOther }

func TestBuildRustfs_DerivesCollectorEndpointFromNodeIP(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	cfg.BaseOtelCollector.OtlpHTTPPort = "4318"
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildRustfs(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 1)

	task, ok := actions[0].Handler.(*rustfsTask)
	require.True(t, ok)
	assert.Equal(t, "http://10.0.0.2:4318", task.ObsEndpoint)
}

func TestBuildRustfs_DefaultsCollectorHTTPPort(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildRustfs(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 1)
	assert.Equal(t, "http://10.0.0.2:4318", actions[0].Handler.(*rustfsTask).ObsEndpoint)
}

func TestBuildRustfs_CollectorDisabledLeavesEndpointEmpty(t *testing.T) {
	cfg := stubCfg()
	cfg.BaseOtelCollector = config.BaseOtelCollector{
		Enable:       false,
		Node:         "missing-node",
		OtlpHTTPPort: "4318",
	}
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildRustfs(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 1)
	assert.Empty(t, actions[0].Handler.(*rustfsTask).ObsEndpoint)
}

func TestRustfsTask_RemovesDisabledObsEndpointFromRunningProcess(t *testing.T) {
	exec := &rustfsRecordingExec{}
	task := &rustfsTask{
		Enable: true, InstallPath: "/opt", WebHost: "10.0.0.1", WebPort: "9041", APIPort: "9040",
		Username: "admin", Password: "secret",
	}

	require.NoError(t, task.doRun(exec))
	commands := strings.Join(exec.commands, "\n")
	assert.Contains(t, commands, "! tr '\\0' '\\n' < /proc/$pid/environ | grep -q '^RUSTFS_OBS_ENDPOINT='")
	assert.Contains(t, commands, "kill \"$pid\"")
}

func TestRustfsTask_WriteStartScriptInjectsQuotedObsEndpoint(t *testing.T) {
	exec := &rustfsRecordingExec{}
	task := &rustfsTask{
		WebHost:     "10.0.0.1",
		WebPort:     "9041",
		APIPort:     "9040",
		Username:    "admin",
		Password:    "secret",
		ObsEndpoint: "http://10.0.0.2:4318; touch /tmp/injected",
	}

	task.writeStartScript(exec, "/opt/rustfs", "/opt/rustfs/data", "/opt/rustfs/logs")

	assert.Equal(t, "/opt/rustfs/start.sh", exec.writtenPath)
	script := strings.Join(exec.writtenLines, "\n")
	assert.Contains(t, script, "export RUSTFS_OBS_ENDPOINT='http://10.0.0.2:4318; touch /tmp/injected'")
	assert.Contains(t, script, "export RUSTFS_OBS_SERVICE_NAME=rustfs")
}

func TestRustfsTask_WriteStartScriptOmitsObsEnvWhenDisabled(t *testing.T) {
	exec := &rustfsRecordingExec{}
	task := &rustfsTask{
		WebHost:  "10.0.0.1",
		WebPort:  "9041",
		APIPort:  "9040",
		Username: "admin",
		Password: "secret",
	}

	task.writeStartScript(exec, "/opt/rustfs", "/opt/rustfs/data", "/opt/rustfs/logs")

	script := strings.Join(exec.writtenLines, "\n")
	assert.NotContains(t, script, "RUSTFS_OBS_ENDPOINT")
	assert.NotContains(t, script, "RUSTFS_OBS_SERVICE_NAME")
}
