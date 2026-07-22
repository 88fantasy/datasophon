package executor

import (
	"bufio"
	"bytes"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// LocalExecutor 对应 Java LocalExecutor，在本机运行命令和文件操作。
type LocalExecutor struct {
	// DryRun 为 true 时只打印命令不执行（--dry-run 支持）。
	DryRun bool
}

var _ Executor = (*LocalExecutor)(nil)

func NewLocalExecutor(dryRun bool) *LocalExecutor {
	return &LocalExecutor{DryRun: dryRun}
}

func (l *LocalExecutor) IsDryRun() bool { return l.DryRun }

func (l *LocalExecutor) ExecShell(cmd string) ExecResult {
	slog.Info("执行命令", "cmd", cmd)
	if l.DryRun {
		return Succeed("[dry-run] " + cmd)
	}
	var stdout, stderr bytes.Buffer
	c := exec.Command("bash", "-c", cmd)
	c.Stdout = &stdout
	c.Stderr = &stderr
	err := c.Run()
	out := strings.TrimSpace(stdout.String())
	errOut := strings.TrimSpace(stderr.String())
	return ExecResult{Output: out, ErrOutput: errOut, Success: err == nil}
}

func (l *LocalExecutor) Exists(path string) ExecResult {
	if l.DryRun {
		return Succeed("[dry-run] assume exists: " + path)
	}
	_, err := os.Stat(path)
	return ExecResult{Success: err == nil}
}

func (l *LocalExecutor) SendFile(src, dst string, override bool) ExecResult {
	if l.DryRun {
		slog.Info("[dry-run] 跳过文件复制", "src", src, "dst", dst)
		return Succeed("")
	}
	if !override {
		if _, err := os.Stat(dst); err == nil {
			return Succeed("")
		}
	}
	if err := os.MkdirAll(filepath.Dir(dst), 0755); err != nil {
		return Fail(err.Error())
	}
	in, err := os.Open(src)
	if err != nil {
		return Fail(err.Error())
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return Fail(err.Error())
	}
	defer out.Close()

	if _, err := io.Copy(out, in); err != nil {
		return Fail(err.Error())
	}
	return Succeed("")
}

func (l *LocalExecutor) SendDir(srcDir, dstDir string, _ bool) ExecResult {
	if l.DryRun {
		slog.Info("[dry-run] 跳过目录复制", "src", srcDir, "dst", dstDir)
		return Succeed("")
	}
	if err := os.MkdirAll(dstDir, 0755); err != nil {
		return Fail(err.Error())
	}
	result := l.ExecShell("cp -r " + srcDir + "/. " + dstDir + "/")
	return result
}

func (l *LocalExecutor) GetFileString(path string) ExecResult {
	data, err := os.ReadFile(path)
	if err != nil {
		return Fail(err.Error())
	}
	return Succeed(string(data))
}

func (l *LocalExecutor) WriteFromStream(in io.Reader, path string) ExecResult {
	if l.DryRun {
		slog.Info("[dry-run] 跳过文件写入", "path", path)
		return Succeed("")
	}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return Fail(err.Error())
	}
	f, err := os.Create(path)
	if err != nil {
		return Fail(err.Error())
	}
	defer f.Close()
	if _, err := io.Copy(f, in); err != nil {
		return Fail(err.Error())
	}
	return Succeed("")
}

func (l *LocalExecutor) WriteLines(lines []string, path string) ExecResult {
	if l.DryRun {
		slog.Info("[dry-run] 跳过文件写入", "path", path, "lines", len(lines))
		return Succeed("")
	}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return Fail(err.Error())
	}
	f, err := os.Create(path)
	if err != nil {
		return Fail(err.Error())
	}
	defer f.Close()
	w := bufio.NewWriter(f)
	for _, line := range lines {
		if _, err := w.WriteString(line + "\n"); err != nil {
			return Fail(err.Error())
		}
	}
	if err := w.Flush(); err != nil {
		return Fail(err.Error())
	}
	return Succeed("")
}

func (l *LocalExecutor) WriteFileAtomic(data []byte, path string, mode os.FileMode) ExecResult {
	if l.DryRun {
		slog.Info("[dry-run] 跳过原子文件写入", "path", path, "mode", mode.Perm())
		return Succeed("changed")
	}
	if existing, err := os.ReadFile(path); err == nil {
		if info, statErr := os.Stat(path); statErr == nil && sameFileContentAndMode(data, mode, existing, info.Mode()) {
			return Succeed("unchanged")
		}
	}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return Fail(err.Error())
	}
	tempPath, err := atomicTempPath(path)
	if err != nil {
		return Fail(err.Error())
	}
	f, err := os.OpenFile(tempPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode.Perm())
	if err != nil {
		return Fail(err.Error())
	}
	cleanup := func() { _ = os.Remove(tempPath) }
	if err := f.Chmod(mode.Perm()); err != nil {
		_ = f.Close()
		cleanup()
		return Fail(err.Error())
	}
	if _, err := f.Write(data); err != nil {
		_ = f.Close()
		cleanup()
		return Fail(err.Error())
	}
	if err := f.Sync(); err != nil {
		_ = f.Close()
		cleanup()
		return Fail(err.Error())
	}
	if err := f.Close(); err != nil {
		cleanup()
		return Fail(err.Error())
	}
	if err := os.Rename(tempPath, path); err != nil {
		cleanup()
		return Fail(err.Error())
	}
	return Succeed("changed")
}

func (l *LocalExecutor) GetOs() osinfo.OsType {
	data, err := os.ReadFile("/etc/os-release")
	if err != nil {
		return osinfo.OsTypeOther
	}
	return osinfo.ParseOsRelease(string(data))
}

func (l *LocalExecutor) GetArch() osinfo.ArchType {
	out, err := exec.Command("uname", "-m").Output()
	if err != nil {
		return osinfo.ArchOther
	}
	return osinfo.ParseArch(string(out))
}
