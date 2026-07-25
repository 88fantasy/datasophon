package executor

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/shellutil"
)

// Executor 对应 Java Executor interface（不含 ExecShellExp，Java 注释明确"尚未完善"）。
type Executor interface {
	ExecShell(cmd string) ExecResult
	Exists(path string) ExecResult
	SendFile(src, dst string, override bool) ExecResult
	SendDir(srcDir, dstDir string, visual bool) ExecResult
	GetFileString(path string) ExecResult
	WriteFromStream(in io.Reader, path string) ExecResult
	WriteLines(lines []string, path string) ExecResult
	GetArch() osinfo.ArchType
	GetOs() osinfo.OsType
}

type dryRunAware interface {
	IsDryRun() bool
}

type PathState uint8

const (
	PathUnknown PathState = iota
	PathMissing
	PathExists
)

// InspectPath preserves an honest Unknown state during dry-run instead of
// pretending every remote path already exists.
func InspectPath(exec Executor, path string) PathState {
	if IsDryRun(exec) {
		return PathUnknown
	}
	if exec.Exists(path).Success {
		return PathExists
	}
	return PathMissing
}

// IsDryRun lets handlers avoid non-Executor side effects while keeping the
// existing Executor interface stable for tests and third-party implementations.
func IsDryRun(exec Executor) bool {
	aware, ok := exec.(dryRunAware)
	return ok && aware.IsDryRun()
}

type atomicFileWriter interface {
	WriteFileAtomic(data []byte, path string, mode os.FileMode) ExecResult
}

// WriteFileAtomic writes a same-directory temporary file with the final mode
// before content is copied, then renames it into place. Output is "changed" or
// "unchanged" so reconcilers can avoid unnecessary restarts.
func WriteFileAtomic(exec Executor, data []byte, path string, mode os.FileMode) ExecResult {
	if writer, ok := exec.(atomicFileWriter); ok {
		return writer.WriteFileAtomic(data, path, mode)
	}
	// Compatibility fallback for lightweight/third-party executors. It cannot
	// detect unchanged content, but still preserves same-directory atomicity.
	tempPath, err := atomicTempPath(path)
	if err != nil {
		return Fail(err.Error())
	}
	result := exec.ExecShell("umask 077; : > " + shellutil.Quote(tempPath) +
		" && chmod " + formatFileMode(mode) + " " + shellutil.Quote(tempPath))
	if !result.Success {
		return result
	}
	lines := strings.Split(strings.TrimSuffix(string(data), "\n"), "\n")
	result = exec.WriteLines(lines, tempPath)
	if !result.Success {
		exec.ExecShell("rm -f -- " + shellutil.Quote(tempPath))
		return result
	}
	result = exec.ExecShell("mv -f " + shellutil.Quote(tempPath) + " " + shellutil.Quote(path))
	if !result.Success {
		exec.ExecShell("rm -f -- " + shellutil.Quote(tempPath))
		return result
	}
	return Succeed("changed")
}

func formatFileMode(mode os.FileMode) string {
	return fmt.Sprintf("%04o", mode.Perm())
}

func atomicTempPath(path string) (string, error) {
	var suffix [8]byte
	if _, err := rand.Read(suffix[:]); err != nil {
		return "", err
	}
	return path + ".tmp-" + hex.EncodeToString(suffix[:]), nil
}

func sameFileContentAndMode(data []byte, mode os.FileMode, existing []byte, existingMode os.FileMode) bool {
	return bytes.Equal(data, existing) && existingMode.Perm() == mode.Perm()
}
