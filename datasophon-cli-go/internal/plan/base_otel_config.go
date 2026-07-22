package plan

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

const (
	defaultOtlpHTTPPort       = "4318"
	defaultOtlpGRPCPort       = "4317"
	defaultSelfMetricsPort    = "8899"
	defaultS3Bucket           = "otel"
	defaultS3Prefix           = "otel-base"
	defaultS3Region           = "us-east-1"
	defaultCollectorMemMiB    = 512
	defaultMysqldExporterPort = "9104"
	defaultMySQLMonitorUser   = "exporter"
	defaultNexusMetricsUser   = "metrics"
	defaultNexusMetricsPath   = "/service/rest/metrics/prometheus"
)

type resolvedBaseObservability struct {
	Config        config.BaseOtelCollector
	CollectorNode *config.Host
	RustfsNode    *config.Host
	MySQLNode     *config.Host
	RegistryNode  *config.Host
}

// resolveBaseObservability is the single defaults/validation boundary used by
// plan generation and apply. Invalid monitoring configuration fails before any
// remote action is scheduled.
func resolveBaseObservability(ctx *BuildContext) (*resolvedBaseObservability, error) {
	cfg := ctx.Cfg.BaseOtelCollector
	if !cfg.Enable {
		return &resolvedBaseObservability{Config: cfg}, nil
	}

	cfg.OtlpHTTPPort = valueOrDefault(cfg.OtlpHTTPPort, defaultOtlpHTTPPort)
	cfg.OtlpGRPCPort = valueOrDefault(cfg.OtlpGRPCPort, defaultOtlpGRPCPort)
	cfg.SelfMetricsPort = valueOrDefault(cfg.SelfMetricsPort, defaultSelfMetricsPort)
	cfg.S3Bucket = valueOrDefault(cfg.S3Bucket, defaultS3Bucket)
	cfg.S3Prefix = valueOrDefault(cfg.S3Prefix, defaultS3Prefix)
	cfg.S3Region = valueOrDefault(cfg.S3Region, defaultS3Region)
	if cfg.MemLimitMiB == 0 {
		cfg.MemLimitMiB = defaultCollectorMemMiB
	}
	cfg.MysqldExporter.Port = valueOrDefault(cfg.MysqldExporter.Port, defaultMysqldExporterPort)
	cfg.MysqldExporter.MonitorUser = valueOrDefault(cfg.MysqldExporter.MonitorUser, defaultMySQLMonitorUser)
	cfg.NexusMetrics.MetricsUser = valueOrDefault(cfg.NexusMetrics.MetricsUser, defaultNexusMetricsUser)
	cfg.NexusMetrics.MetricsPath = valueOrDefault(cfg.NexusMetrics.MetricsPath, defaultNexusMetricsPath)

	ports := []struct {
		name  string
		value string
	}{
		{"otlpHttpPort", cfg.OtlpHTTPPort},
		{"otlpGrpcPort", cfg.OtlpGRPCPort},
		{"selfMetricsPort", cfg.SelfMetricsPort},
	}
	for _, port := range ports {
		if err := validateTCPPort(port.name, port.value); err != nil {
			return nil, err
		}
	}
	if cfg.MemLimitMiB < 1 {
		return nil, fmt.Errorf("baseOtelCollector.memLimitMiB 必须大于 0: %d", cfg.MemLimitMiB)
	}
	if !s3BucketNamePattern.MatchString(cfg.S3Bucket) {
		return nil, fmt.Errorf("baseOtelCollector.s3Bucket 不合法: %q", cfg.S3Bucket)
	}
	if !ctx.Cfg.Rustfs.Enable || len(ctx.Cfg.Rustfs.Nodes) == 0 {
		return nil, fmt.Errorf("baseOtelCollector 需要启用至少一个 rustfs 节点作为 S3 sink")
	}
	if strings.TrimSpace(ctx.Cfg.Rustfs.Config.User) == "" || ctx.Cfg.Rustfs.Config.Password == "" {
		return nil, fmt.Errorf("baseOtelCollector 需要非空的 rustfs S3 用户名和密码")
	}
	if containsLineBreak(ctx.Cfg.Rustfs.Config.User) || containsLineBreak(ctx.Cfg.Rustfs.Config.Password) {
		return nil, fmt.Errorf("rustfs S3 用户名和密码不能包含换行符")
	}
	if strings.TrimSpace(ctx.Cfg.Packages.Rustfs.X86_64) == "" ||
		strings.TrimSpace(ctx.Cfg.Packages.Rustfs.Aarch64) == "" {
		return nil, fmt.Errorf("baseOtelCollector 依赖的 rustfs 缺少双架构制品配置")
	}
	if err := validateTCPPort("rustfs.config.apiPort", ctx.Cfg.Rustfs.Config.APIPort); err != nil {
		return nil, err
	}
	if strings.TrimSpace(ctx.Cfg.Packages.OtelColContrib.X86_64) == "" ||
		strings.TrimSpace(ctx.Cfg.Packages.OtelColContrib.Aarch64) == "" {
		return nil, fmt.Errorf("baseOtelCollector 缺少 otelColContrib 双架构制品配置")
	}

	collectorNode, err := requireNode(ctx.GlobalNodes, strings.TrimSpace(cfg.Node))
	if err != nil {
		return nil, fmt.Errorf("基础采集 collector 节点: %w", err)
	}
	rustfsNode, err := requireNode(ctx.GlobalNodes, ctx.Cfg.Rustfs.Nodes[0])
	if err != nil {
		return nil, fmt.Errorf("基础采集 collector rustfs 节点: %w", err)
	}
	resolved := &resolvedBaseObservability{
		Config: cfg, CollectorNode: collectorNode, RustfsNode: rustfsNode,
	}

	if ctx.Cfg.Mysql.Enable && cfg.MysqldExporter.Enable {
		if err := validateTCPPort("mysqldExporter.port", cfg.MysqldExporter.Port); err != nil {
			return nil, err
		}
		if ctx.Cfg.Mysql.Port < 1 || ctx.Cfg.Mysql.Port > 65535 {
			return nil, fmt.Errorf("mysql.port 必须在 1..65535: %d", ctx.Cfg.Mysql.Port)
		}
		if strings.TrimSpace(cfg.MysqldExporter.MonitorUser) == "" || cfg.MysqldExporter.MonitorPassword == "" {
			return nil, fmt.Errorf("启用 mysqldExporter 时 monitorUser 和 monitorPassword 不能为空")
		}
		if ctx.Cfg.Mysql.Password == "" {
			return nil, fmt.Errorf("启用 mysqldExporter 时 mysql.password 不能为空")
		}
		if containsLineBreak(cfg.MysqldExporter.MonitorUser) || containsLineBreak(cfg.MysqldExporter.MonitorPassword) {
			return nil, fmt.Errorf("mysqldExporter monitorUser 和 monitorPassword 不能包含换行符")
		}
		if strings.TrimSpace(ctx.Cfg.Packages.MysqldExporter.X86_64) == "" ||
			strings.TrimSpace(ctx.Cfg.Packages.MysqldExporter.Aarch64) == "" {
			return nil, fmt.Errorf("mysqldExporter 缺少双架构制品配置")
		}
		resolved.MySQLNode, err = requireNode(ctx.GlobalNodes, ctx.Cfg.Mysql.Node)
		if err != nil {
			return nil, fmt.Errorf("mysqld_exporter 节点: %w", err)
		}
	}

	if ctx.Cfg.Registry.Enable {
		if strings.TrimSpace(ctx.Cfg.Registry.Config.User) == "" || ctx.Cfg.Registry.Config.Password == "" {
			return nil, fmt.Errorf("启用 Nexus metrics 时 registry 管理员用户名和密码不能为空")
		}
		if strings.TrimSpace(cfg.NexusMetrics.MetricsUser) == "" || cfg.NexusMetrics.MetricsPassword == "" {
			return nil, fmt.Errorf("启用 Nexus metrics 时 metricsUser 和 metricsPassword 不能为空")
		}
		if containsLineBreak(cfg.NexusMetrics.MetricsUser) || containsLineBreak(cfg.NexusMetrics.MetricsPassword) {
			return nil, fmt.Errorf("Nexus metrics 用户名和密码不能包含换行符")
		}
		if !strings.HasPrefix(cfg.NexusMetrics.MetricsPath, "/") {
			return nil, fmt.Errorf("nexusMetrics.metricsPath 必须以 / 开头: %q", cfg.NexusMetrics.MetricsPath)
		}
		if err := validateTCPPort("registry.config.webPort", ctx.Cfg.Registry.Config.WebPort); err != nil {
			return nil, err
		}
		resolved.RegistryNode, err = requireNode(ctx.GlobalNodes, ctx.Cfg.Registry.Node)
		if err != nil {
			return nil, fmt.Errorf("Nexus metrics 节点: %w", err)
		}
	}
	return resolved, nil
}

func validateTCPPort(name, value string) error {
	port, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || port < 1 || port > 65535 {
		return fmt.Errorf("%s 必须是 1..65535 的端口: %q", name, value)
	}
	return nil
}

func valueOrDefault(value, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return strings.TrimSpace(value)
}
