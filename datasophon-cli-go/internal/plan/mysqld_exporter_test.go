package plan

import (
	"io"
	"os"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

func TestBuildMysqldExporterDefaults(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildMysqldExporter(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 1)
	task := actions[0].Handler.(*mysqldExporterTask)
	assert.Equal(t, "9104", task.Port)
	assert.Equal(t, "exporter", task.MonitorUser)
}

func TestMysqldExporterDryRunDoesNotCreateCredentialsOrRequirePackage(t *testing.T) {
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/remote/install", NodeIP: "10.0.0.1", Port: "9104",
		MySQLPort: 3306, RootPassword: "root-secret", MonitorUser: "exporter", MonitorPassword: "secret",
		X86Tar: "mysqld_exporter-0.16.0.linux-amd64.tar.gz", Aarch64Tar: "mysqld_exporter-0.16.0.linux-arm64.tar.gz",
	}
	require.NoError(t, task.doRun(executor.NewLocalExecutor(true)))
}

type mysqldExporterRecordingExec struct {
	arch           osinfo.ArchType
	exists         map[string]bool
	commands       []string
	writes         map[string][]string
	processUp      bool
	failSQLRun     bool
	sentFiles      []string
	currentTarget  string
	unchangedFiles map[string]bool
	failFirstStart bool
	startAttempts  int
}

func (e *mysqldExporterRecordingExec) ExecShell(command string) executor.ExecResult {
	e.commands = append(e.commands, command)
	if strings.HasPrefix(command, "mktemp -d ") {
		return executor.Succeed("/tmp/datasophon-mysqld-exporter.ABC123")
	}
	if strings.HasPrefix(command, "readlink ") {
		if e.currentTarget == "" {
			return executor.Fail("not a symlink")
		}
		return executor.Succeed(e.currentTarget)
	}
	if strings.Contains(command, "awk -v bin=") && strings.Contains(command, "[ -n \"$pid\" ]") {
		if e.processUp {
			return executor.Succeed("mysqld_exporter")
		}
		return executor.Fail("")
	}
	if strings.Contains(command, "awk -v bin=") && strings.Contains(command, "kill \"$pid\"") {
		e.processUp = false
		return executor.Succeed("")
	}
	if strings.Contains(command, "mysql --defaults-extra-file=") && e.failSQLRun {
		return executor.Fail("access denied")
	}
	if strings.HasPrefix(command, "bash ") {
		e.startAttempts++
		if e.failFirstStart && e.startAttempts == 1 {
			return executor.Fail("start failed")
		}
		e.processUp = true
	}
	return executor.Succeed("")
}

func (e *mysqldExporterRecordingExec) Exists(path string) executor.ExecResult {
	if e.exists[path] {
		return executor.Succeed("exists")
	}
	return executor.Fail("")
}

func (e *mysqldExporterRecordingExec) SendFile(src, dst string, _ bool) executor.ExecResult {
	e.sentFiles = append(e.sentFiles, src+" -> "+dst)
	return executor.Succeed("")
}
func (e *mysqldExporterRecordingExec) SendDir(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}
func (e *mysqldExporterRecordingExec) GetFileString(string) executor.ExecResult {
	return executor.Succeed("")
}
func (e *mysqldExporterRecordingExec) WriteFromStream(io.Reader, string) executor.ExecResult {
	return executor.Succeed("")
}
func (e *mysqldExporterRecordingExec) WriteLines(lines []string, path string) executor.ExecResult {
	if e.writes == nil {
		e.writes = make(map[string][]string)
	}
	e.writes[path] = append([]string(nil), lines...)
	return executor.Succeed("")
}
func (e *mysqldExporterRecordingExec) WriteFileAtomic(data []byte, path string, _ os.FileMode) executor.ExecResult {
	if e.writes == nil {
		e.writes = make(map[string][]string)
	}
	e.writes[path] = strings.Split(strings.TrimSuffix(string(data), "\n"), "\n")
	if e.unchangedFiles[path] {
		return executor.Succeed("unchanged")
	}
	return executor.Succeed("changed")
}
func (e *mysqldExporterRecordingExec) GetArch() osinfo.ArchType { return e.arch }
func (e *mysqldExporterRecordingExec) GetOs() osinfo.OsType     { return osinfo.OsTypeCentos7 }

func TestMysqldExporterTaskInstallsAccountAndExporter(t *testing.T) {
	exec := &mysqldExporterRecordingExec{
		arch: osinfo.ArchX86_64,
		exists: map[string]bool{
			"/opt/datasophon": true,
			"/packages/mysqld_exporter-0.16.0.linux-amd64.tar.gz": true,
		},
	}
	task := &mysqldExporterTask{
		Enable:          true,
		PackagePath:     "/packages",
		InstallPath:     "/opt/datasophon",
		X86Tar:          "mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		Aarch64Tar:      "mysqld_exporter-0.16.0.linux-arm64.tar.gz",
		NodeIP:          "10.0.0.11",
		Port:            "9104",
		MySQLPort:       3307,
		RootPassword:    `root"password`,
		MonitorUser:     "exporter",
		MonitorPassword: "exporter#password",
	}

	if err := task.doRun(exec); err != nil {
		t.Fatal(err)
	}
	assertCommandContains(t, exec.commands, "mysql --defaults-extra-file='/tmp/datasophon-mysqld-exporter.ABC123/client.cnf' -uroot -P3307")
	assertCommandContains(t, exec.commands, "tar -xzf '/packages/mysqld_exporter-0.16.0.linux-amd64.tar.gz'")
	assertCommandContains(t, exec.commands, "-C '/opt/datasophon/mysqld_exporter/releases/mysqld_exporter-0.16.0.linux-amd64'")
	rootCnf := strings.Join(exec.writes["/tmp/datasophon-mysqld-exporter.ABC123/client.cnf"], "\n")
	if !strings.Contains(rootCnf, `password="root\"password"`) {
		t.Fatalf("root option file password was not escaped: %s", rootCnf)
	}

	sql := strings.Join(exec.writes["/tmp/datasophon-mysqld-exporter.ABC123/account.sql"], "\n")
	for _, expected := range []string{
		"CREATE USER IF NOT EXISTS 'exporter'@'localhost'",
		"ALTER USER 'exporter'@'localhost' IDENTIFIED BY 'exporter#password' WITH MAX_USER_CONNECTIONS 3",
		"WITH MAX_USER_CONNECTIONS 3",
		"GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'localhost'",
	} {
		if !strings.Contains(sql, expected) {
			t.Fatalf("SQL missing %q:\n%s", expected, sql)
		}
	}
	myCnf := strings.Join(exec.writes["/opt/datasophon/mysqld_exporter/.my.cnf"], "\n")
	for _, expected := range []string{
		`user="exporter"`,
		`password="exporter#password"`,
		"host=127.0.0.1",
		"port=3307",
	} {
		if !strings.Contains(myCnf, expected) {
			t.Fatalf(".my.cnf missing %q:\n%s", expected, myCnf)
		}
	}
	startScript := strings.Join(exec.writes["/opt/datasophon/mysqld_exporter/start.sh"], "\n")
	if strings.Contains(startScript, "DATA_SOURCE_NAME") {
		t.Fatalf("v0.16.0 start script must not use DATA_SOURCE_NAME:\n%s", startScript)
	}
	if !strings.Contains(startScript, "--config.my-cnf='/opt/datasophon/mysqld_exporter/.my.cnf'") {
		t.Fatalf("unexpected start script:\n%s", startScript)
	}
	if !strings.Contains(startScript, "--web.listen-address='10.0.0.11:9104'") {
		t.Fatalf("start script does not bind node IP:\n%s", startScript)
	}
}

func TestMysqldExporterTaskTransfersPackageWhenTargetDoesNotShareBaseDir(t *testing.T) {
	exec := &mysqldExporterRecordingExec{
		arch:   osinfo.ArchX86_64,
		exists: map[string]bool{"/opt/datasophon": true},
	}
	task := &mysqldExporterTask{
		Enable: true, PackagePath: "/packages", InstallPath: "/opt/datasophon",
		X86Tar: "mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		NodeIP: "10.0.0.11", Port: "9104", MySQLPort: 3306,
		RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "secret",
	}

	require.NoError(t, task.doRun(exec))
	require.Len(t, exec.sentFiles, 1)
	assert.Equal(t,
		"/packages/mysqld_exporter-0.16.0.linux-amd64.tar.gz -> /packages/mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		exec.sentFiles[0],
	)
}

func TestMysqldExporterTaskRestartsRunningProcessToReloadCredentials(t *testing.T) {
	const release = "/opt/datasophon/mysqld_exporter/releases/mysqld_exporter-0.16.0.linux-amd64"
	exec := &mysqldExporterRecordingExec{
		arch:          osinfo.ArchX86_64,
		processUp:     true,
		currentTarget: release + "/mysqld_exporter",
		exists: map[string]bool{
			"/opt/datasophon":                 true,
			"/opt/datasophon/mysqld_exporter": true,
			release:                           true,
		},
	}
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/opt/datasophon", NodeIP: "10.0.0.11", Port: "9104",
		MySQLPort: 3306, RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "rotated-secret",
		X86Tar:     "mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		Aarch64Tar: "mysqld_exporter-0.16.0.linux-arm64.tar.gz",
	}

	require.NoError(t, task.doRun(exec))
	assertCommandContains(t, exec.commands, "kill \"$pid\"")
	assertCommandContains(t, exec.commands, "bash '/opt/datasophon/mysqld_exporter/start.sh'")
}

func TestMysqldExporterTaskKeepsUnchangedRunningProcess(t *testing.T) {
	const (
		home    = "/opt/datasophon/mysqld_exporter"
		release = home + "/releases/mysqld_exporter-0.16.0.linux-amd64"
	)
	exec := &mysqldExporterRecordingExec{
		arch: osinfo.ArchX86_64, processUp: true, currentTarget: release + "/mysqld_exporter",
		exists:         map[string]bool{"/opt/datasophon": true, home: true, release: true},
		unchangedFiles: map[string]bool{home + "/.my.cnf": true, home + "/start.sh": true},
	}
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/opt/datasophon", NodeIP: "10.0.0.11", Port: "9104",
		MySQLPort: 3306, RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "secret",
		X86Tar:     "mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		Aarch64Tar: "mysqld_exporter-0.16.0.linux-arm64.tar.gz",
	}

	require.NoError(t, task.doRun(exec))
	commands := strings.Join(exec.commands, "\n")
	assert.NotContains(t, commands, "kill \"$pid\"")
	assert.NotContains(t, commands, "bash '"+home+"/start.sh'")
}

func TestMysqldExporterTaskRollsBackVersionWhenStartFails(t *testing.T) {
	const (
		home       = "/opt/datasophon/mysqld_exporter"
		oldTarget  = home + "/releases/mysqld_exporter-0.15.1.linux-amd64/mysqld_exporter"
		newRelease = home + "/releases/mysqld_exporter-0.16.0.linux-amd64"
	)
	exec := &mysqldExporterRecordingExec{
		arch: osinfo.ArchX86_64, processUp: true, currentTarget: oldTarget, failFirstStart: true,
		exists:         map[string]bool{"/opt/datasophon": true, home: true, newRelease: true},
		unchangedFiles: map[string]bool{home + "/.my.cnf": true, home + "/start.sh": true},
	}
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/opt/datasophon", NodeIP: "10.0.0.11", Port: "9104",
		MySQLPort: 3306, RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "secret",
		X86Tar:     "mysqld_exporter-0.16.0.linux-amd64.tar.gz",
		Aarch64Tar: "mysqld_exporter-0.16.0.linux-arm64.tar.gz",
	}

	err := task.doRun(exec)
	require.Error(t, err)
	commands := strings.Join(exec.commands, "\n")
	assert.Contains(t, commands, "ln -sfn '"+newRelease+"/mysqld_exporter'")
	assert.Contains(t, commands, "ln -sfn '"+oldTarget+"'")
	assert.Equal(t, 2, exec.startAttempts, "rollback should start the previous version")
}

func TestMysqlOptionFileValueEscapesBackslashAndQuote(t *testing.T) {
	got, err := mysqlOptionFileValue(`p\a"ss`)
	if err != nil {
		t.Fatal(err)
	}
	if got != `"p\\a\"ss"` {
		t.Fatalf("unexpected option file value: %s", got)
	}
}

func TestMysqldExporterTaskRejectsNewlinesInOptionFileValues(t *testing.T) {
	exec := &mysqldExporterRecordingExec{exists: map[string]bool{"/opt/datasophon": true}}
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/opt/datasophon", NodeIP: "10.0.0.11", Port: "9104",
		MySQLPort: 3306, RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "bad\nvalue",
	}

	err := task.doRun(exec)
	if err == nil || !strings.Contains(err.Error(), "不能包含换行符") {
		t.Fatalf("expected newline validation error, got %v", err)
	}
	if len(exec.commands) != 0 {
		t.Fatalf("no command should run after invalid credentials: %v", exec.commands)
	}
}

func TestMysqldExporterTaskStopsWhenAccountCreationFails(t *testing.T) {
	exec := &mysqldExporterRecordingExec{
		arch:       osinfo.ArchX86_64,
		failSQLRun: true,
		exists:     map[string]bool{"/opt/datasophon": true},
	}
	task := &mysqldExporterTask{
		Enable: true, InstallPath: "/opt/datasophon", NodeIP: "10.0.0.11", Port: "9104",
		MySQLPort: 3306, RootPassword: "root", MonitorUser: "exporter", MonitorPassword: "secret",
	}

	err := task.doRun(exec)
	if err == nil || !strings.Contains(err.Error(), "创建 MySQL exporter 监控账号失败") {
		t.Fatalf("expected account creation error, got %v", err)
	}
	for _, command := range exec.commands {
		if strings.Contains(command, "tar -xzf") {
			t.Fatalf("exporter must not be installed after account failure: %s", command)
		}
	}
}

func assertCommandContains(t *testing.T, commands []string, expected string) {
	t.Helper()
	for _, command := range commands {
		if strings.Contains(command, expected) {
			return
		}
	}
	t.Fatalf("commands do not contain %q: %v", expected, commands)
}
