package plan

import (
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

func TestBaseOtelCollectorConfigWithRealBinary(t *testing.T) {
	bin := os.Getenv("OTELCOL_VALIDATION_BIN")
	if bin == "" {
		t.Skip("set OTELCOL_VALIDATION_BIN to validate with a real otelcol-contrib binary")
	}
	task := &baseOtelCollectorTask{
		S3Endpoint:  "http://127.0.0.1:9040",
		MysqlTarget: "127.0.0.1:9104",
		NexusTarget: "127.0.0.1:8081",
		NexusUser:   "metrics",
		NexusPass:   "secret",
	}
	rendered, err := task.renderConfig()
	require.NoError(t, err)
	configPath := filepath.Join(t.TempDir(), "otelcol.yaml")
	require.NoError(t, os.WriteFile(configPath, []byte(rendered), 0o600))

	command := exec.Command(bin, "validate", "--config", configPath)
	output, err := command.CombinedOutput()
	require.NoError(t, err, "otelcol-contrib validate failed:\n%s", output)
}

func TestBaseOtelCollectorRenderConfig(t *testing.T) {
	task := &baseOtelCollectorTask{
		OtlpHTTPPort:    "4318",
		OtlpGRPCPort:    "4317",
		SelfMetricsPort: "8899",
		MemLimitMiB:     512,
		S3Region:        "us-east-1",
		S3Bucket:        "otel",
		S3Prefix:        "otel-base",
		S3Endpoint:      "http://10.0.0.1:9000",
		MysqlTarget:     "10.0.0.2:9104",
		NexusTarget:     "10.0.0.3:8081",
		NexusPath:       "/service/rest/metrics/prometheus",
		S3AccessKey:     "rustfs-access",
		S3SecretKey:     "rustfs-secret",
		NexusUser:       "metrics-user",
		NexusPass:       "metrics-secret",
	}

	got, err := task.renderConfig()
	require.NoError(t, err)
	var parsed map[string]any
	require.NoError(t, yaml.Unmarshal([]byte(got), &parsed), "渲染结果必须是合法 YAML:\n%s", got)

	assert.Contains(t, got, "job_name: mysql")
	assert.Contains(t, got, `targets: ["10.0.0.2:9104"]`)
	assert.Contains(t, got, "job_name: nexus")
	assert.Contains(t, got, "basic_auth:")
	assert.Contains(t, got, "username: ${env:NEXUS_METRICS_USER}")
	assert.Contains(t, got, "password: ${env:NEXUS_METRICS_PASSWORD}")
	assert.Contains(t, got, "awss3:")
	assert.Contains(t, got, "s3_force_path_style: true")
	assert.Contains(t, got, "marshaler: otlp_json")
	assert.Contains(t, got, "storage: file_storage/queue")
	assert.NotContains(t, got, task.S3AccessKey)
	assert.NotContains(t, got, task.S3SecretKey)
	assert.NotContains(t, got, task.NexusUser)
	assert.NotContains(t, got, task.NexusPass)

	envFile, err := task.renderEnv()
	require.NoError(t, err)
	assert.Contains(t, envFile, "AWS_ACCESS_KEY_ID=rustfs-access")
	assert.Contains(t, envFile, "AWS_SECRET_ACCESS_KEY=rustfs-secret")
	assert.Contains(t, envFile, "NEXUS_METRICS_USER=metrics-user")
	assert.Contains(t, envFile, "NEXUS_METRICS_PASSWORD=metrics-secret")
}

func TestBaseOtelCollectorDryRunDoesNotRequireRemoteFiles(t *testing.T) {
	task := &baseOtelCollectorTask{
		InstallPath: "/remote/install",
		X86Tar:      "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		Aarch64Tar:  "otelcol-contrib_0.156.0_linux_arm64.tar.gz",
		S3Endpoint:  "http://10.0.0.1:9040",
	}
	require.NoError(t, task.doRun(executor.NewLocalExecutor(true)))
}

func TestBaseOtelCollectorRenderConfigWithoutPrometheusTargets(t *testing.T) {
	task := &baseOtelCollectorTask{S3Endpoint: "http://10.0.0.1:9000"}
	got, err := task.renderConfig()
	require.NoError(t, err)
	assert.NotContains(t, got, "prometheus/infra:")
	assert.Contains(t, got, "receivers: [otlp]")
	assert.Contains(t, got, "limit_mib: 512")
	assert.Contains(t, got, "port: 8899")
}

func TestBaseOtelCollectorRenderEnvRejectsNewline(t *testing.T) {
	task := &baseOtelCollectorTask{S3SecretKey: "bad\nsecret"}
	_, err := task.renderEnv()
	require.Error(t, err)
	assert.Contains(t, err.Error(), "AWS_SECRET_ACCESS_KEY")
}

func TestBaseOtelCollectorRenderEnvOmitsDisabledNexusCredentials(t *testing.T) {
	task := &baseOtelCollectorTask{NexusUser: "unused-user", NexusPass: "unused-password"}
	got, err := task.renderEnv()
	require.NoError(t, err)
	assert.NotContains(t, got, "NEXUS_METRICS_USER")
	assert.NotContains(t, got, "unused-password")
}

func TestBaseOtelCollectorRenderEnvPreservesShellMetacharacters(t *testing.T) {
	task := &baseOtelCollectorTask{
		S3AccessKey: "access #$;'=value",
		NexusTarget: "10.0.0.1:8081",
		NexusPass:   `pass with spaces $HOME;"quoted"`,
	}
	got, err := task.renderEnv()
	require.NoError(t, err)
	// control.sh 逐行拆 key/value 后用 export "$key=$value"，不会执行 shell 展开；
	// 此处必须保留原值，若额外 shell quote，单引号反而会成为凭据内容。
	assert.Contains(t, got, "AWS_ACCESS_KEY_ID=access #$;'=value\n")
	assert.Contains(t, got, "NEXUS_METRICS_PASSWORD=pass with spaces $HOME;\"quoted\"\n")
}

func TestCollectorHomeName(t *testing.T) {
	got, err := collectorHomeName("otelcol-contrib_0.156.0_linux_amd64.tar.gz")
	require.NoError(t, err)
	assert.Equal(t, "otelcol-contrib_0.156.0", got)

	_, err = collectorHomeName("../../unsafe.tar.gz")
	require.Error(t, err)
	_, err = collectorHomeName("../../otelcol-contrib_0.156.0_linux_amd64.tar.gz")
	require.Error(t, err)
}

func TestEmbeddedCollectorControlScriptMatchesPackageTemplate(t *testing.T) {
	canonical, err := os.ReadFile("../../../package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh")
	require.NoError(t, err)
	assert.Equal(t, string(canonical), baseOtelCollectorControlScript,
		"embedded control.sh drifted from the package template")
}

func TestBuildBaseOtelCollector(t *testing.T) {
	cfg := stubCfg()
	cfg.Registry.Config = config.RegistryConfig{WebPort: "8081", User: "admin", Password: "registry-pass"}
	cfg.Rustfs.Config = config.RustfsConfig{APIPort: "9000", User: "rustfs", Password: "rustfs-pass"}
	cfg.Packages.OtelColContrib = config.Package{
		X86_64:  "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		Aarch64: "otelcol-contrib_0.156.0_linux_arm64.tar.gz",
	}
	cfg.BaseOtelCollector = config.BaseOtelCollector{
		Enable:          true,
		Node:            "node2",
		OtlpHTTPPort:    "4318",
		OtlpGRPCPort:    "4317",
		SelfMetricsPort: "8899",
		S3Bucket:        "otel",
		S3Prefix:        "otel-base",
		S3Region:        "us-east-1",
		MemLimitMiB:     512,
		MysqldExporter: config.MysqldExporter{
			Enable:          true,
			Port:            "9104",
			MonitorPassword: "mysql-metrics-pass",
		},
		NexusMetrics: config.NexusMetrics{
			MetricsUser:     "metrics",
			MetricsPassword: "metrics-pass",
			MetricsPath:     "/service/rest/metrics/prometheus",
		},
	}
	ctx := stubCtx(cfg, t.TempDir())

	actions, err := buildBaseOtelCollector(ctx)
	require.NoError(t, err)
	require.Len(t, actions, 1)
	assert.Equal(t, "node2", actions[0].HostKey)
	task := actions[0].Handler.(*baseOtelCollectorTask)
	assert.Equal(t, "http://10.0.0.1:9000", task.S3Endpoint)
	assert.Equal(t, "10.0.0.1:9104", task.MysqlTarget)
	assert.Equal(t, "10.0.0.1:8081", task.NexusTarget)
}

type otelCollectorRecordingExec struct {
	arch           osinfo.ArchType
	exists         map[string]bool
	commands       []string
	writes         map[string]string
	statusChecks   int
	sentFiles      []string
	initiallyUp    bool
	currentTarget  string
	unchangedFiles map[string]bool
	failRestart    bool
}

func (e *otelCollectorRecordingExec) ExecShell(command string) executor.ExecResult {
	e.commands = append(e.commands, command)
	if strings.HasPrefix(command, "readlink ") {
		if e.currentTarget == "" {
			return executor.Fail("not a symlink")
		}
		return executor.Succeed(e.currentTarget)
	}
	if e.failRestart && strings.HasSuffix(command, " restart") {
		return executor.Fail("restart failed")
	}
	if strings.HasSuffix(command, " status") {
		e.statusChecks++
		if e.statusChecks == 1 && !e.initiallyUp {
			return executor.Fail("not running")
		}
	}
	return executor.Succeed("")
}

func (e *otelCollectorRecordingExec) Exists(path string) executor.ExecResult {
	if e.exists[path] {
		return executor.Succeed("exists")
	}
	return executor.Fail("not found")
}

func (e *otelCollectorRecordingExec) SendFile(src, dst string, _ bool) executor.ExecResult {
	e.sentFiles = append(e.sentFiles, src+" -> "+dst)
	return executor.Succeed("")
}

func (e *otelCollectorRecordingExec) SendDir(string, string, bool) executor.ExecResult {
	return executor.Succeed("")
}

func TestBaseOtelCollectorTransfersPackageWhenTargetDoesNotShareBaseDir(t *testing.T) {
	const (
		installPath = "/opt/datasophon"
		packagePath = "/packages"
		tarName     = "otelcol-contrib_0.156.0_linux_amd64.tar.gz"
	)
	exec := &otelCollectorRecordingExec{
		arch:   osinfo.ArchX86_64,
		exists: map[string]bool{installPath: true},
	}
	task := &baseOtelCollectorTask{
		PackagePath: packagePath,
		InstallPath: installPath,
		X86Tar:      tarName,
		S3Endpoint:  "http://10.0.0.1:9040",
	}

	require.NoError(t, task.doRun(exec))
	require.Len(t, exec.sentFiles, 1)
	assert.Equal(t, packagePath+"/"+tarName+" -> "+packagePath+"/"+tarName, exec.sentFiles[0])
}

func (e *otelCollectorRecordingExec) GetFileString(string) executor.ExecResult {
	return executor.Succeed("")
}

func (e *otelCollectorRecordingExec) WriteFromStream(io.Reader, string) executor.ExecResult {
	return executor.Succeed("")
}

func (e *otelCollectorRecordingExec) WriteLines(lines []string, path string) executor.ExecResult {
	if e.writes == nil {
		e.writes = make(map[string]string)
	}
	e.writes[path] = strings.Join(lines, "\n")
	return executor.Succeed("")
}

func (e *otelCollectorRecordingExec) WriteFileAtomic(data []byte, path string, _ os.FileMode) executor.ExecResult {
	if e.writes == nil {
		e.writes = make(map[string]string)
	}
	e.writes[path] = string(data)
	if e.unchangedFiles[path] {
		return executor.Succeed("unchanged")
	}
	return executor.Succeed("changed")
}

func (e *otelCollectorRecordingExec) GetArch() osinfo.ArchType { return e.arch }
func (e *otelCollectorRecordingExec) GetOs() osinfo.OsType     { return osinfo.OsTypeCentos7 }

func TestBaseOtelCollectorDoRunExtractsRendersAndStarts(t *testing.T) {
	const (
		installPath = "/opt/datasophon"
		packagePath = "/opt/datasophon-init/packages"
		armTar      = "otelcol-contrib_0.156.0_linux_arm64.tar.gz"
	)
	home := installPath + "/base-otel-collector"
	release := home + "/releases/otelcol-contrib_0.156.0"
	exec := &otelCollectorRecordingExec{
		arch: osinfo.ArchAarch64,
		exists: map[string]bool{
			installPath:                true,
			packagePath + "/" + armTar: true,
		},
	}
	task := &baseOtelCollectorTask{
		PackagePath: packagePath,
		InstallPath: installPath,
		X86Tar:      "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		Aarch64Tar:  armTar,
		S3Endpoint:  "http://10.0.0.1:9000",
		S3AccessKey: "access",
		S3SecretKey: "secret",
	}

	require.NoError(t, task.doRun(exec))
	commands := strings.Join(exec.commands, "\n")
	assert.Contains(t, commands, "tar -xzf '"+packagePath+"/"+armTar+"' -C '"+release+"'")
	assert.Contains(t, commands, "ln -sfn '"+release+"/otelcol-contrib'")
	assert.Contains(t, commands, "bash '"+home+"/control.sh' start")
	assert.Equal(t, 2, exec.statusChecks)
	assert.Contains(t, exec.writes[home+"/config/otelcol.yaml"], "awss3:")
	assert.Contains(t, exec.writes[home+"/config/otelcol.yaml"], `directory: "`+home+`/queue"`)
	assert.Contains(t, exec.writes[home+"/config/otelcol.env"], "AWS_SECRET_ACCESS_KEY=secret")
	assert.Contains(t, exec.writes[home+"/control.sh"], "flock -w 60")
}

func TestBaseOtelCollectorRestartsToApplyRenderedConfig(t *testing.T) {
	const (
		home    = "/opt/datasophon/base-otel-collector"
		release = home + "/releases/otelcol-contrib_0.156.0"
	)
	exec := &otelCollectorRecordingExec{
		arch:          osinfo.ArchX86_64,
		initiallyUp:   true,
		currentTarget: release + "/otelcol-contrib",
		exists: map[string]bool{
			"/opt/datasophon": true,
			home:              true,
			release:           true,
		},
	}
	task := &baseOtelCollectorTask{
		InstallPath: "/opt/datasophon",
		X86Tar:      "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		S3Endpoint:  "http://10.0.0.1:9040",
	}

	require.NoError(t, task.doRun(exec))
	assert.Contains(t, strings.Join(exec.commands, "\n"), "control.sh' restart")
}

func TestBaseOtelCollectorKeepsUnchangedRunningProcess(t *testing.T) {
	const (
		home    = "/opt/datasophon/base-otel-collector"
		release = home + "/releases/otelcol-contrib_0.156.0"
	)
	exec := &otelCollectorRecordingExec{
		arch: osinfo.ArchX86_64, initiallyUp: true, currentTarget: release + "/otelcol-contrib",
		exists: map[string]bool{"/opt/datasophon": true, home: true, release: true},
		unchangedFiles: map[string]bool{
			home + "/config/otelcol.yaml": true,
			home + "/config/otelcol.env":  true,
			home + "/control.sh":          true,
		},
	}
	task := &baseOtelCollectorTask{
		InstallPath: "/opt/datasophon",
		X86Tar:      "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		Aarch64Tar:  "otelcol-contrib_0.156.0_linux_arm64.tar.gz",
		S3Endpoint:  "http://10.0.0.1:9040",
	}

	require.NoError(t, task.doRun(exec))
	commands := strings.Join(exec.commands, "\n")
	assert.NotContains(t, commands, "control.sh' restart")
	assert.NotContains(t, commands, "control.sh' start")
}

func TestBaseOtelCollectorRollsBackVersionWhenRestartFails(t *testing.T) {
	const (
		home       = "/opt/datasophon/base-otel-collector"
		oldTarget  = home + "/releases/otelcol-contrib_0.155.0/otelcol-contrib"
		newRelease = home + "/releases/otelcol-contrib_0.156.0"
	)
	exec := &otelCollectorRecordingExec{
		arch: osinfo.ArchX86_64, initiallyUp: true, currentTarget: oldTarget, failRestart: true,
		exists: map[string]bool{"/opt/datasophon": true, home: true, newRelease: true},
		unchangedFiles: map[string]bool{
			home + "/config/otelcol.yaml": true,
			home + "/config/otelcol.env":  true,
			home + "/control.sh":          true,
		},
	}
	task := &baseOtelCollectorTask{
		InstallPath: "/opt/datasophon",
		X86Tar:      "otelcol-contrib_0.156.0_linux_amd64.tar.gz",
		Aarch64Tar:  "otelcol-contrib_0.156.0_linux_arm64.tar.gz",
		S3Endpoint:  "http://10.0.0.1:9040",
	}

	err := task.doRun(exec)
	require.Error(t, err)
	commands := strings.Join(exec.commands, "\n")
	assert.Contains(t, commands, "ln -sfn '"+newRelease+"/otelcol-contrib'")
	assert.Contains(t, commands, "control.sh' stop")
	assert.Contains(t, commands, "ln -sfn '"+oldTarget+"'")
	assert.Contains(t, commands, "control.sh' start")
}
