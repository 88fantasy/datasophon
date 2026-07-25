package create

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

func TestCreateConfigRendersMonitoringPasswords(t *testing.T) {
	output := filepath.Join(t.TempDir(), "cluster-config.yml")
	cmd := &createConfigCmd{OutputPath: output, typeFlag: "hadoop"}

	require.NoError(t, cmd.run())
	cfg, err := config.Load(output)
	require.NoError(t, err)

	assert.False(t, cfg.BaseOtelCollector.Enable)
	assert.Len(t, cfg.BaseOtelCollector.MysqldExporter.MonitorPassword, passwordLength)
	assert.Len(t, cfg.BaseOtelCollector.NexusMetrics.MetricsPassword, passwordLength)
	assert.NotEqual(t,
		cfg.BaseOtelCollector.MysqldExporter.MonitorPassword,
		cfg.BaseOtelCollector.NexusMetrics.MetricsPassword,
	)
}
