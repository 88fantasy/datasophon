package bootstrap

import (
	"io"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

type recordingExecutor struct {
	commands []string
	writes   map[string][]string
	failOn   string
}

func (r *recordingExecutor) ExecShell(command string) executor.ExecResult {
	r.commands = append(r.commands, command)
	if r.failOn != "" && strings.Contains(command, r.failOn) {
		return executor.Fail("failed")
	}
	return executor.Succeed("")
}

func (r *recordingExecutor) WriteLines(lines []string, path string) executor.ExecResult {
	if r.writes == nil {
		r.writes = make(map[string][]string)
	}
	r.writes[path] = lines
	return executor.Succeed("")
}

func (r *recordingExecutor) Exists(string) executor.ExecResult { return executor.ExecResult{} }
func (r *recordingExecutor) SendFile(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}
func (r *recordingExecutor) SendDir(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}
func (r *recordingExecutor) GetFileString(string) executor.ExecResult { return executor.Succeed("") }
func (r *recordingExecutor) WriteFromStream(io.Reader, string) executor.ExecResult {
	return executor.Succeed("")
}
func (r *recordingExecutor) GetArch() osinfo.ArchType { return osinfo.ArchX86_64 }
func (r *recordingExecutor) GetOs() osinfo.OsType     { return osinfo.OsTypeCentos7 }

func TestResetMySQLTemporaryRootPassword(t *testing.T) {
	exec := &recordingExecutor{}

	if err := ResetMySQLTemporaryRootPassword(exec, "temporary#password", "new-password"); err != nil {
		t.Fatal(err)
	}
	if !contains(exec.commands, "--connect-expired-password") {
		t.Fatal("temporary password reset must enable expired-password login")
	}
	if got := exec.writes["/tmp/.dsph_mysql_old.cnf"]; len(got) != 2 || got[1] != `password="temporary#password"` {
		t.Fatalf("unexpected client config: %v", got)
	}
}

func TestConfigureMySQLRootUsesConfiguredPort(t *testing.T) {
	exec := &recordingExecutor{}

	if err := ConfigureMySQLRoot(exec, "new-password", 3307); err != nil {
		t.Fatal(err)
	}
	if !contains(exec.commands, "-P3307") {
		t.Fatal("root configuration must use configured MySQL port")
	}
}

func contains(values []string, part string) bool {
	for _, value := range values {
		if strings.Contains(value, part) {
			return true
		}
	}
	return false
}
