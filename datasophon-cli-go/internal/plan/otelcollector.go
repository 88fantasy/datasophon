package plan

import (
	_ "embed"
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"text/template"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/shellutil"
	"golang.org/x/crypto/ssh"
)

//go:embed assets/otelcollector/control.sh
var baseOtelCollectorControlScript string

//go:embed assets/otelcollector/otelcol.yaml.tmpl
var baseOtelCollectorConfigTemplate string

var safeCollectorHomeName = regexp.MustCompile(`^otelcol-contrib_[A-Za-z0-9.-]+$`)

type baseOtelCollectorTask struct {
	PackagePath string
	InstallPath string
	X86Tar      string
	Aarch64Tar  string

	OtlpHTTPPort    string
	OtlpGRPCPort    string
	SelfMetricsPort string
	MemLimitMiB     int
	QueueStorageDir string

	S3Region    string
	S3Bucket    string
	S3Prefix    string
	S3Endpoint  string
	S3AccessKey string
	S3SecretKey string

	MysqlTarget string
	NexusTarget string
	NexusPath   string
	NexusUser   string
	NexusPass   string
}

func (t *baseOtelCollectorTask) Name() string { return "安装基础采集collector" }

func (t *baseOtelCollectorTask) Handle(client *ssh.Client, dryRun bool) error {
	if !dryRun {
		if err := ensureS3Bucket(t.S3Endpoint, valueOrDefault(t.S3Bucket, defaultS3Bucket),
			valueOrDefault(t.S3Region, defaultS3Region), t.S3AccessKey, t.S3SecretKey); err != nil {
			return err
		}
	}
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *baseOtelCollectorTask) doRun(exec executor.Executor) error {
	if executor.InspectPath(exec, t.InstallPath) == executor.PathMissing {
		return fmt.Errorf("基础采集 collector 安装目录不存在: %s", t.InstallPath)
	}

	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	releaseName, err := collectorHomeName(tarName)
	if err != nil {
		return err
	}
	home := filepath.Join(t.InstallPath, "base-otel-collector")
	releaseDir := filepath.Join(home, "releases", releaseName)
	releaseBinary := filepath.Join(releaseDir, "otelcol-contrib")
	binaryLink := filepath.Join(home, "otelcol-contrib")
	tarPath := filepath.Join(t.PackagePath, tarName)

	if r := exec.ExecShell("mkdir -p " + shellutil.Quote(filepath.Join(home, "releases"))); !r.Success {
		return fmt.Errorf("创建基础采集 collector releases 目录失败: %s", r.ErrOutput)
	}
	if executor.InspectPath(exec, releaseDir) != executor.PathExists {
		if err := ensurePackageOnTarget(exec, tarPath, "基础采集 collector"); err != nil {
			return err
		}
		if r := exec.ExecShell(fmt.Sprintf("mkdir -p %s && tar -xzf %s -C %s",
			shellutil.Quote(releaseDir), shellutil.Quote(tarPath), shellutil.Quote(releaseDir))); !r.Success {
			return fmt.Errorf("解压基础采集 collector 失败: %s", r.ErrOutput)
		}
		if r := exec.ExecShell("chmod +x " + shellutil.Quote(releaseBinary)); !r.Success {
			return fmt.Errorf("设置 otelcol-contrib 可执行权限失败: %s", r.ErrOutput)
		}
	}

	oldTarget := ""
	if !executor.IsDryRun(exec) {
		if result := exec.ExecShell("readlink " + shellutil.Quote(binaryLink)); result.Success {
			oldTarget = strings.TrimSpace(result.Output)
		}
	}
	binaryChanged := oldTarget != releaseBinary
	if binaryChanged {
		if result := switchVersionSymlink(exec, binaryLink, releaseBinary); !result.Success {
			return fmt.Errorf("切换基础采集 collector 版本失败: %s", result.ErrOutput)
		}
	}

	renderTask := *t
	if renderTask.QueueStorageDir == "" {
		renderTask.QueueStorageDir = filepath.Join(home, "queue")
	}
	configYAML, err := renderTask.renderConfig()
	if err != nil {
		return err
	}
	envFile, err := t.renderEnv()
	if err != nil {
		return err
	}
	configDir := filepath.Join(home, "config")
	if r := exec.ExecShell("mkdir -p " + shellutil.Quote(configDir)); !r.Success {
		return fmt.Errorf("创建 collector 配置目录失败: %s", r.ErrOutput)
	}
	configResult := executor.WriteFileAtomic(exec, []byte(configYAML), filepath.Join(configDir, "otelcol.yaml"), 0o644)
	if !configResult.Success {
		return fmt.Errorf("写入 otelcol.yaml 失败: %s", configResult.ErrOutput)
	}
	envPath := filepath.Join(configDir, "otelcol.env")
	envResult := executor.WriteFileAtomic(exec, []byte(envFile), envPath, 0o600)
	if !envResult.Success {
		return fmt.Errorf("写入 otelcol.env 失败: %s", envResult.ErrOutput)
	}
	controlResult := executor.WriteFileAtomic(exec, []byte(baseOtelCollectorControlScript), filepath.Join(home, "control.sh"), 0o755)
	if !controlResult.Success {
		return fmt.Errorf("写入 collector control.sh 失败: %s", controlResult.ErrOutput)
	}

	running := !executor.IsDryRun(exec) && t.checkStart(exec, home)
	changed := binaryChanged || configResult.Output == "changed" || envResult.Output == "changed" || controlResult.Output == "changed"
	if running && !changed {
		slog.Info("基础采集 collector 配置与版本未变化，保持运行", "path", home)
		return nil
	}
	operation := "start"
	if running {
		operation = "restart"
	}
	if r := exec.ExecShell("bash " + shellutil.Quote(filepath.Join(home, "control.sh")) + " " + operation); !r.Success {
		t.rollbackCollector(exec, home, binaryLink, oldTarget, binaryChanged)
		return fmt.Errorf("%s 基础采集 collector 失败: %s", operation, r.ErrOutput)
	}
	exec.ExecShell("sleep 3")
	if !executor.IsDryRun(exec) && !t.checkStart(exec, home) {
		t.rollbackCollector(exec, home, binaryLink, oldTarget, binaryChanged)
		return errors.New("基础采集 collector 启动后未存活")
	}
	slog.Info("基础采集 collector 安装成功", "path", home)
	return nil
}

func (t *baseOtelCollectorTask) rollbackCollector(exec executor.Executor, home, binaryLink, oldTarget string, binaryChanged bool) {
	if !binaryChanged || oldTarget == "" || executor.IsDryRun(exec) {
		return
	}
	control := filepath.Join(home, "control.sh")
	exec.ExecShell("bash " + shellutil.Quote(control) + " stop")
	if result := switchVersionSymlink(exec, binaryLink, oldTarget); !result.Success {
		slog.Error("回滚基础采集 collector 版本失败", "error", result.ErrOutput)
		return
	}
	exec.ExecShell("bash " + shellutil.Quote(control) + " start")
}

func (t *baseOtelCollectorTask) checkStart(exec executor.Executor, home string) bool {
	return exec.ExecShell("bash " + shellutil.Quote(filepath.Join(home, "control.sh")) + " status").Success
}

func collectorHomeName(tarName string) (string, error) {
	base := filepath.Base(tarName)
	if tarName != base {
		return "", fmt.Errorf("collector 制品名不能包含路径: %q", tarName)
	}
	index := strings.Index(base, "_linux_")
	if index <= 0 {
		return "", fmt.Errorf("无法从 collector 制品名推导安装目录: %q", tarName)
	}
	name := base[:index]
	if !safeCollectorHomeName.MatchString(name) {
		return "", fmt.Errorf("collector 制品名包含不安全的安装目录: %q", tarName)
	}
	return name, nil
}

type otelCollectorTemplateData struct {
	OtlpHTTPPort    string
	OtlpGRPCPort    string
	SelfMetricsPort string
	MemLimitMiB     int
	QueueStorageDir string
	S3Region        string
	S3Bucket        string
	S3Prefix        string
	S3Endpoint      string
	MysqlTarget     string
	NexusTarget     string
	NexusPath       string
}

func (t *baseOtelCollectorTask) renderConfig() (string, error) {
	data := otelCollectorTemplateData{
		OtlpHTTPPort:    valueOrDefault(t.OtlpHTTPPort, defaultOtlpHTTPPort),
		OtlpGRPCPort:    valueOrDefault(t.OtlpGRPCPort, defaultOtlpGRPCPort),
		SelfMetricsPort: valueOrDefault(t.SelfMetricsPort, defaultSelfMetricsPort),
		MemLimitMiB:     t.MemLimitMiB,
		QueueStorageDir: valueOrDefault(t.QueueStorageDir, "./queue"),
		S3Region:        valueOrDefault(t.S3Region, defaultS3Region),
		S3Bucket:        valueOrDefault(t.S3Bucket, defaultS3Bucket),
		S3Prefix:        valueOrDefault(t.S3Prefix, defaultS3Prefix),
		S3Endpoint:      t.S3Endpoint,
		MysqlTarget:     t.MysqlTarget,
		NexusTarget:     t.NexusTarget,
		NexusPath:       valueOrDefault(t.NexusPath, defaultNexusMetricsPath),
	}
	if data.MemLimitMiB <= 0 {
		data.MemLimitMiB = defaultCollectorMemMiB
	}
	if data.S3Endpoint == "" {
		return "", errors.New("基础采集 collector 缺少 S3 endpoint")
	}

	tpl, err := template.New("otelcol.yaml").Funcs(template.FuncMap{"yamlQuote": strconv.Quote}).Parse(baseOtelCollectorConfigTemplate)
	if err != nil {
		return "", fmt.Errorf("解析 otelcol.yaml 模板失败: %w", err)
	}
	var result strings.Builder
	if err := tpl.Execute(&result, data); err != nil {
		return "", fmt.Errorf("渲染 otelcol.yaml 失败: %w", err)
	}
	return result.String(), nil
}

func (t *baseOtelCollectorTask) renderEnv() (string, error) {
	type envValue struct {
		key   string
		value string
	}
	values := []envValue{
		{"AWS_ACCESS_KEY_ID", t.S3AccessKey},
		{"AWS_SECRET_ACCESS_KEY", t.S3SecretKey},
	}
	if t.NexusTarget != "" {
		values = append(values,
			envValue{"NEXUS_METRICS_USER", t.NexusUser},
			envValue{"NEXUS_METRICS_PASSWORD", t.NexusPass},
		)
	}
	var lines []string
	for _, item := range values {
		if strings.ContainsAny(item.value, "\r\n") {
			return "", fmt.Errorf("%s 不能包含换行符", item.key)
		}
		lines = append(lines, item.key+"="+item.value)
	}
	return strings.Join(lines, "\n") + "\n", nil
}

func buildBaseOtelCollector(ctx *BuildContext) ([]Action, error) {
	resolved, err := resolveBaseObservability(ctx)
	if err != nil {
		return nil, err
	}
	cfg := resolved.Config

	t := &baseOtelCollectorTask{
		PackagePath:     ctx.PackagesPath,
		InstallPath:     ctx.InstallPath,
		X86Tar:          ctx.Cfg.Packages.OtelColContrib.X86_64,
		Aarch64Tar:      ctx.Cfg.Packages.OtelColContrib.Aarch64,
		OtlpHTTPPort:    cfg.OtlpHTTPPort,
		OtlpGRPCPort:    cfg.OtlpGRPCPort,
		SelfMetricsPort: cfg.SelfMetricsPort,
		MemLimitMiB:     cfg.MemLimitMiB,
		S3Region:        cfg.S3Region,
		S3Bucket:        cfg.S3Bucket,
		S3Prefix:        cfg.S3Prefix,
		S3Endpoint:      fmt.Sprintf("http://%s:%s", resolved.RustfsNode.IP, ctx.Cfg.Rustfs.Config.APIPort),
		S3AccessKey:     ctx.Cfg.Rustfs.Config.User,
		S3SecretKey:     ctx.Cfg.Rustfs.Config.Password,
		NexusPath:       cfg.NexusMetrics.MetricsPath,
		NexusUser:       cfg.NexusMetrics.MetricsUser,
		NexusPass:       cfg.NexusMetrics.MetricsPassword,
	}
	if ctx.Cfg.Mysql.Enable && cfg.MysqldExporter.Enable {
		t.MysqlTarget = fmt.Sprintf("%s:%s", resolved.MySQLNode.IP, cfg.MysqldExporter.Port)
	}
	if ctx.Cfg.Registry.Enable {
		t.NexusTarget = fmt.Sprintf("%s:%s", resolved.RegistryNode.IP, ctx.Cfg.Registry.Config.WebPort)
	}
	return singleHostAction(resolved.CollectorNode, t), nil
}
