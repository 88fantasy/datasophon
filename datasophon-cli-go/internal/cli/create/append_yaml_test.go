package create

import (
	"os"
	"strings"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

const testYAML = `global:
  sshAuthType: PASSWORD
nodes:
  # 主节点
  - ip: 10.0.0.1
    port: 22
    user: root
    password: root123
    hostname: master
`

func TestAppendNodeToYAML_Basic(t *testing.T) {
	f, err := os.CreateTemp("", "cluster-*.yml")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.WriteString(testYAML)
	f.Close()
	path := f.Name()
	defer os.Remove(path)

	host := &config.Host{IP: "10.0.0.42", Port: 22, User: "root", Password: "xxx", Hostname: "node42"}
	appended, err := appendNodeToYAML(path, host)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !appended {
		t.Fatal("expected appended=true")
	}
	data, _ := os.ReadFile(path)
	content := string(data)
	if !strings.Contains(content, "# 主节点") {
		t.Errorf("comment was stripped:\n%s", content)
	}
	if !strings.Contains(content, "10.0.0.42") {
		t.Errorf("new node IP not in file:\n%s", content)
	}
	if !strings.Contains(content, "node42") {
		t.Errorf("new hostname not in file:\n%s", content)
	}
	t.Logf("after append:\n%s", content)
}

func TestAppendNodeToYAML_DedupIP(t *testing.T) {
	f, err := os.CreateTemp("", "cluster-*.yml")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.WriteString(testYAML)
	f.Close()
	path := f.Name()
	defer os.Remove(path)

	host := &config.Host{IP: "10.0.0.1", Port: 22, User: "root", Password: "xxx", Hostname: "different"}
	appended, err := appendNodeToYAML(path, host)
	if err != nil {
		t.Fatal(err)
	}
	if appended {
		t.Error("expected appended=false when IP already exists")
	}
}

func TestAppendNodeToYAML_DedupHostname(t *testing.T) {
	f, err := os.CreateTemp("", "cluster-*.yml")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.WriteString(testYAML)
	f.Close()
	path := f.Name()
	defer os.Remove(path)

	host := &config.Host{IP: "10.0.0.99", Port: 22, User: "root", Password: "xxx", Hostname: "master"}
	appended, err := appendNodeToYAML(path, host)
	if err != nil {
		t.Fatal(err)
	}
	if appended {
		t.Error("expected appended=false when hostname already exists")
	}
}
