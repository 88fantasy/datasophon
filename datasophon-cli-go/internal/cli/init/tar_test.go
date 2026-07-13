package initcmd

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type tarRecordingExec struct {
	recordingExec
	tarAvailable bool
	sentFiles    [][2]string
}

func (m *tarRecordingExec) ExecShell(cmd string) executor.ExecResult {
	m.commands = append(m.commands, cmd)
	switch {
	case cmd == "which tar":
		if m.tarAvailable {
			return executor.Succeed("/usr/bin/tar")
		}
		return executor.Fail("tar not found")
	case strings.HasPrefix(cmd, "rpm -ivh "):
		m.tarAvailable = true
	}
	return executor.Succeed("")
}

func (m *tarRecordingExec) SendFile(src, dst string, _ bool) executor.ExecResult {
	m.sentFiles = append(m.sentFiles, [2]string{src, dst})
	return executor.Succeed("")
}

func TestInitTar_AlreadyExists(t *testing.T) {
	m := &tarRecordingExec{
		recordingExec: recordingExec{arch: osinfo.ArchX86_64},
		tarAvailable:  true,
	}

	err := (&InitTar{ProductPackagesPath: "/packages"}).doRun(m)

	require.NoError(t, err)
	assert.Equal(t, []string{"which tar"}, m.commands)
	assert.Empty(t, m.sentFiles)
}

func TestInitTar_MissingWithoutProductPackagesPath(t *testing.T) {
	m := &tarRecordingExec{recordingExec: recordingExec{arch: osinfo.ArchX86_64}}

	err := (&InitTar{}).doRun(m)

	require.EqualError(t, err, "tar 命令不存在")
	assert.Empty(t, m.sentFiles)
}

func TestInitTar_InstallsOfflineRpm(t *testing.T) {
	root := t.TempDir()
	repoDir := filepath.Join(root, "yum", string(osinfo.ArchX86_64), string(osinfo.OsTypeOpenEuler220303SP3))
	require.NoError(t, os.MkdirAll(repoDir, 0o755))
	localPkg := filepath.Join(repoDir, "tar-1.0-0.x86_64.rpm")
	require.NoError(t, os.WriteFile(localPkg, nil, 0o644))

	m := &tarRecordingExec{
		recordingExec: recordingExec{arch: osinfo.ArchX86_64},
	}
	err := (&InitTar{ProductPackagesPath: root}).doRun(m)

	require.NoError(t, err)
	require.Equal(t, [][2]string{{localPkg, "/tmp/tar-1.0-0.x86_64.rpm"}}, m.sentFiles)
	assert.True(t, hasCmd(m.commands, "rpm -ivh '/tmp/tar-1.0-0.x86_64.rpm'"))
	assert.True(t, hasCmd(m.commands, "rm -f '/tmp/tar-1.0-0.x86_64.rpm'"))
	assert.Equal(t, 2, countCommand(m.commands, "which tar"))
}

func (m *tarRecordingExec) GetOs() osinfo.OsType {
	return osinfo.OsTypeOpenEuler220303SP3
}

func countCommand(commands []string, target string) int {
	count := 0
	for _, command := range commands {
		if command == target {
			count++
		}
	}
	return count
}
