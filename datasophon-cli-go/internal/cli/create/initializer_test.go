package create

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

func newInitializerTestEnv(t *testing.T) (*nodeInitializer, string) {
	t.Helper()

	root := t.TempDir()
	datasophonPath := filepath.Join(root, "datasophon")
	productPackagesPath := filepath.Join(root, "package")
	configPath := filepath.Join(datasophonPath, "datasophon-init", "config", "cluster-sample.yml")
	if err := os.MkdirAll(filepath.Dir(configPath), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(productPackagesPath, 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(configPath, []byte(minimalClusterYAML), 0644); err != nil {
		t.Fatal(err)
	}

	return &nodeInitializer{
		DatasophonPath:  datasophonPath,
		InstallPath:     filepath.Join(root, "install"),
		ProductPkgsPath: productPackagesPath,
	}, configPath
}

func TestNodeInitializerPackagesPath(t *testing.T) {
	t.Run("setup", func(t *testing.T) {
		n, _ := newInitializerTestEnv(t)
		if _, err := n.setup(); err != nil {
			t.Fatal(err)
		}
		assertPackagesPath(t, n)
	})

	t.Run("setupStandalone", func(t *testing.T) {
		n, _ := newInitializerTestEnv(t)
		if err := n.setupStandalone(&config.Host{Hostname: "node1"}); err != nil {
			t.Fatal(err)
		}
		assertPackagesPath(t, n)
	})

	t.Run("setupConfig", func(t *testing.T) {
		n, configPath := newInitializerTestEnv(t)
		newNode := &config.Host{IP: "10.0.0.2", Hostname: "node2"}
		if err := n.setupConfig(configPath, newNode); err != nil {
			t.Fatal(err)
		}
		assertPackagesPath(t, n)
	})
}

func TestNodeInitializerProductPackagesPathValidation(t *testing.T) {
	t.Run("relative path is rejected", func(t *testing.T) {
		for _, setup := range initializerSetups(t, "package") {
			t.Run(setup.name, func(t *testing.T) {
				err := setup.run()
				if err == nil || !strings.Contains(err.Error(), "productPackagesPath 必须是绝对路径") {
					t.Fatalf("期望绝对路径错误，实际: %v", err)
				}
			})
		}
	})

	t.Run("missing path is rejected", func(t *testing.T) {
		missingPath := filepath.Join(t.TempDir(), "missing-package")
		for _, setup := range initializerSetups(t, missingPath) {
			t.Run(setup.name, func(t *testing.T) {
				err := setup.run()
				if err == nil || !strings.Contains(err.Error(), missingPath) {
					t.Fatalf("期望包含不存在路径 %q 的错误，实际: %v", missingPath, err)
				}
			})
		}
	})

	t.Run("trailing slash is trimmed", func(t *testing.T) {
		for _, setup := range initializerSetups(t, "") {
			t.Run(setup.name, func(t *testing.T) {
				productPackagesPath := setup.initializer.ProductPkgsPath
				setup.initializer.ProductPkgsPath += "/"
				if err := setup.run(); err != nil {
					t.Fatal(err)
				}
				if setup.initializer.ProductPkgsPath != productPackagesPath {
					t.Errorf("ProductPkgsPath 应去除尾部斜杠，实际: %s", setup.initializer.ProductPkgsPath)
				}
				assertPackagesPath(t, setup.initializer)
			})
		}
	})
}

type initializerSetup struct {
	name        string
	initializer *nodeInitializer
	run         func() error
}

func initializerSetups(t *testing.T, productPackagesPath string) []initializerSetup {
	t.Helper()

	newInitializer := func() (*nodeInitializer, string) {
		n, configPath := newInitializerTestEnv(t)
		if productPackagesPath != "" {
			n.ProductPkgsPath = productPackagesPath
		}
		return n, configPath
	}

	n, _ := newInitializer()
	standalone, _ := newInitializer()
	configMode, configPath := newInitializer()
	return []initializerSetup{
		{name: "setup", initializer: n, run: func() error { _, err := n.setup(); return err }},
		{name: "setupStandalone", initializer: standalone, run: func() error {
			return standalone.setupStandalone(&config.Host{Hostname: "node1"})
		}},
		{name: "setupConfig", initializer: configMode, run: func() error {
			return configMode.setupConfig(configPath, &config.Host{IP: "10.0.0.2", Hostname: "node2"})
		}},
	}
}

func assertPackagesPath(t *testing.T, n *nodeInitializer) {
	t.Helper()
	want := n.ProductPkgsPath + "/base"
	if n.packagesPath != want {
		t.Errorf("packagesPath = %s，期望 %s", n.packagesPath, want)
	}
}
