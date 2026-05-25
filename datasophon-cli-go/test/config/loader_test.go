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
	cfg, err := config.Load(path, "")
	require.NoError(t, err)

	assert.Equal(t, config.SSHAuthTypeAuto, cfg.Global.SSHAuthType)
	assert.False(t, cfg.Global.Offline)

	require.Len(t, cfg.Nodes, 2)
	assert.Equal(t, "node1", cfg.Nodes[0].Hostname)
	assert.Equal(t, "192.168.1.10", cfg.Nodes[0].IP)
	assert.Equal(t, 22, cfg.Nodes[0].Port)

	require.Len(t, cfg.AddNodes, 1)
	assert.Equal(t, "node2", cfg.AddNodes[0].Hostname)
}

func TestLoad_PasswordWarning(t *testing.T) {
	// 传 password 时应仅打印 warn，不返回 error
	path := filepath.Join(fixturesDir(), "cluster-sample.yml")
	cfg, err := config.Load(path, "somepassword")
	require.NoError(t, err)
	assert.NotNil(t, cfg)
}

func TestLoad_FileNotFound(t *testing.T) {
	_, err := config.Load("/nonexistent/path/cluster.yml", "")
	assert.Error(t, err)
}

func TestLoad_InvalidYaml(t *testing.T) {
	dir := t.TempDir()
	badFile := filepath.Join(dir, "bad.yml")
	require.NoError(t, os.WriteFile(badFile, []byte("global: [invalid yaml }"), 0o644))

	_, err := config.Load(badFile, "")
	assert.Error(t, err)
}
