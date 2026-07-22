package executor

import (
	"os"
	"strings"
	"testing"
)

func TestLocalExecutorDryRunDoesNotWriteFiles(t *testing.T) {
	exec := NewLocalExecutor(true)
	destination := t.TempDir() + "/nested/config.yml"

	if !exec.Exists(destination).Success {
		t.Fatal("dry-run Exists should allow handlers to continue")
	}
	if !exec.WriteLines([]string{"secret"}, destination).Success {
		t.Fatal("dry-run WriteLines should be a successful no-op")
	}
	if !exec.WriteFromStream(strings.NewReader("secret"), destination).Success {
		t.Fatal("dry-run WriteFromStream should be a successful no-op")
	}
	if !exec.SendFile(destination, destination+".copy", true).Success {
		t.Fatal("dry-run SendFile should be a successful no-op")
	}
	if NewLocalExecutor(false).Exists(destination).Success {
		t.Fatal("dry-run operations must not create the destination")
	}
	if state := InspectPath(exec, destination); state != PathUnknown {
		t.Fatalf("dry-run path state = %v, want PathUnknown", state)
	}
}

func TestSSHExecutorDryRunFileOperationsDoNotNeedClient(t *testing.T) {
	exec := NewSSHExecutor(nil, true)
	if !exec.Exists("/remote/path").Success ||
		!exec.SendFile("/local/file", "/remote/file", false).Success ||
		!exec.SendDir("/local/dir", "/remote/dir", false).Success ||
		!exec.WriteLines([]string{"value"}, "/remote/config").Success {
		t.Fatal("SSH dry-run file operations should be successful no-ops")
	}
	if !IsDryRun(exec) {
		t.Fatal("IsDryRun should detect SSHExecutor dry-run mode")
	}
}

func TestLocalExecutorWriteFileAtomicPreservesContentAndMode(t *testing.T) {
	exec := NewLocalExecutor(false)
	path := t.TempDir() + "/secrets/collector.env"
	data := []byte("TOKEN=secret\n")

	first := exec.WriteFileAtomic(data, path, 0o600)
	if !first.Success || first.Output != "changed" {
		t.Fatalf("first atomic write = %+v, want changed", first)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if got := info.Mode().Perm(); got != 0o600 {
		t.Fatalf("mode = %o, want 600", got)
	}
	second := exec.WriteFileAtomic(data, path, 0o600)
	if !second.Success || second.Output != "unchanged" {
		t.Fatalf("second atomic write = %+v, want unchanged", second)
	}
	updated := exec.WriteFileAtomic(data, path, 0o640)
	if !updated.Success || updated.Output != "changed" {
		t.Fatalf("mode-only atomic write = %+v, want changed", updated)
	}
}
