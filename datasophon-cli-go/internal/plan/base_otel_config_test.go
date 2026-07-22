package plan

import (
	"strings"
	"testing"
)

func TestResolveBaseObservabilityAppliesDefaults(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	resolved, err := resolveBaseObservability(stubCtx(cfg, t.TempDir()))
	if err != nil {
		t.Fatal(err)
	}

	if resolved.Config.OtlpHTTPPort != defaultOtlpHTTPPort ||
		resolved.Config.OtlpGRPCPort != defaultOtlpGRPCPort ||
		resolved.Config.SelfMetricsPort != defaultSelfMetricsPort ||
		resolved.Config.MemLimitMiB != defaultCollectorMemMiB {
		t.Fatalf("collector defaults not applied: %+v", resolved.Config)
	}
	if resolved.Config.MysqldExporter.MonitorUser != defaultMySQLMonitorUser ||
		resolved.Config.NexusMetrics.MetricsUser != defaultNexusMetricsUser {
		t.Fatalf("monitoring account defaults not applied: %+v", resolved.Config)
	}
}

func TestResolveBaseObservabilityRejectsInvalidPortBeforePlanExecution(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	cfg.BaseOtelCollector.OtlpHTTPPort = "70000"

	_, err := resolveBaseObservability(stubCtx(cfg, t.TempDir()))
	if err == nil || !strings.Contains(err.Error(), "otlpHttpPort") {
		t.Fatalf("expected invalid otlpHttpPort error, got %v", err)
	}
}

func TestResolveBaseObservabilityRejectsMissingSecrets(t *testing.T) {
	cfg := stubCfg()
	enableValidBaseOtel(cfg)
	cfg.Rustfs.Config.Password = ""

	_, err := resolveBaseObservability(stubCtx(cfg, t.TempDir()))
	if err == nil || !strings.Contains(err.Error(), "rustfs S3") {
		t.Fatalf("expected missing RustFS secret error, got %v", err)
	}
}
