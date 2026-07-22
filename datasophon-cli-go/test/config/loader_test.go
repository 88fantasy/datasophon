package config_test

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

func fixturesDir() string {
	_, file, _, _ := runtime.Caller(0)
	return filepath.Join(filepath.Dir(file), "..", "fixtures")
}

func TestLoad_Plaintext(t *testing.T) {
	path := filepath.Join(fixturesDir(), "cluster-sample.yml")
	cfg, err := config.Load(path)
	require.NoError(t, err)

	assert.Equal(t, config.SSHAuthTypeAuto, cfg.Global.SSHAuthType)
	assert.False(t, cfg.Global.Offline)

	require.Len(t, cfg.Nodes, 2)
	assert.Equal(t, "node1", cfg.Nodes[0].Hostname)
	assert.Equal(t, "192.168.1.10", cfg.Nodes[0].IP)
	assert.Equal(t, 22, cfg.Nodes[0].Port)
}

func TestLoad_FileNotFound(t *testing.T) {
	_, err := config.Load("/nonexistent/path/cluster.yml")
	assert.Error(t, err)
}

func TestLoad_InvalidYaml(t *testing.T) {
	dir := t.TempDir()
	badFile := filepath.Join(dir, "bad.yml")
	require.NoError(t, os.WriteFile(badFile, []byte("global: [invalid yaml }"), 0o644))

	_, err := config.Load(badFile)
	assert.Error(t, err)
}

func TestLoad_BaseOtelCollector(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "cluster.yml")
	content := `
baseOtelCollector:
  enable: true
  node: node2
  otlpHttpPort: "4318"
  otlpGrpcPort: "4317"
  selfMetricsPort: "8899"
  s3Bucket: otel
  s3Prefix: otel-base
  s3Region: us-east-1
  memLimitMiB: 512
  mysqldExporter:
    enable: true
    port: "9104"
    monitorUser: exporter
    monitorPassword: exporter-secret
  nexusMetrics:
    metricsUser: metrics
    metricsPassword: metrics-secret
    metricsPath: /service/rest/metrics/prometheus
packages:
  otelColContrib:
    x86_64: otelcol-contrib_0.156.0_linux_amd64.tar.gz
    aarch64: otelcol-contrib_0.156.0_linux_arm64.tar.gz
  mysqldExporter:
    x86_64: mysqld_exporter-0.16.0.linux-amd64.tar.gz
    aarch64: mysqld_exporter-0.16.0.linux-arm64.tar.gz
`
	require.NoError(t, os.WriteFile(path, []byte(content), 0o600))

	cfg, err := config.Load(path)
	require.NoError(t, err)

	collector := cfg.BaseOtelCollector
	assert.True(t, collector.Enable)
	assert.Equal(t, "node2", collector.Node)
	assert.Equal(t, "4318", collector.OtlpHTTPPort)
	assert.Equal(t, "4317", collector.OtlpGRPCPort)
	assert.Equal(t, "8899", collector.SelfMetricsPort)
	assert.Equal(t, "otel", collector.S3Bucket)
	assert.Equal(t, "otel-base", collector.S3Prefix)
	assert.Equal(t, "us-east-1", collector.S3Region)
	assert.Equal(t, 512, collector.MemLimitMiB)
	assert.Equal(t, config.MysqldExporter{
		Enable:          true,
		Port:            "9104",
		MonitorUser:     "exporter",
		MonitorPassword: "exporter-secret",
	}, collector.MysqldExporter)
	assert.Equal(t, config.NexusMetrics{
		MetricsUser:     "metrics",
		MetricsPassword: "metrics-secret",
		MetricsPath:     "/service/rest/metrics/prometheus",
	}, collector.NexusMetrics)
	assert.Equal(t, "otelcol-contrib_0.156.0_linux_amd64.tar.gz", cfg.Packages.OtelColContrib.X86_64)
	assert.Equal(t, "mysqld_exporter-0.16.0.linux-arm64.tar.gz", cfg.Packages.MysqldExporter.Aarch64)
}
