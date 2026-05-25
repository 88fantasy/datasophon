package executor

import (
	"bufio"
	"bytes"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"github.com/pkg/sftp"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// SSHExecutor 对应 Java JschExecutor，底层持有单个 *ssh.Client（跨多个 handler 复用）。
type SSHExecutor struct {
	client *ssh.Client
	DryRun bool
}

var _ Executor = (*SSHExecutor)(nil)

func NewSSHExecutor(client *ssh.Client, dryRun bool) *SSHExecutor {
	return &SSHExecutor{client: client, DryRun: dryRun}
}

func (s *SSHExecutor) ExecShell(cmd string) ExecResult {
	slog.Info("远程执行命令", "cmd", cmd)
	if s.DryRun {
		return Succeed("[dry-run] " + cmd)
	}
	session, err := s.client.NewSession()
	if err != nil {
		return Fail(err.Error())
	}
	defer session.Close()

	var stdout, stderr bytes.Buffer
	session.Stdout = &stdout
	session.Stderr = &stderr

	err = session.Run(cmd)
	out := strings.TrimSpace(stdout.String())
	errOut := strings.TrimSpace(stderr.String())
	return ExecResult{Output: out, ErrOutput: errOut, Success: err == nil}
}

func (s *SSHExecutor) Exists(path string) ExecResult {
	r := s.ExecShell("test -e " + path + " && echo exists || echo notfound")
	return ExecResult{Success: r.Success && r.Output == "exists"}
}

func (s *SSHExecutor) SendFile(src, dst string, override bool) ExecResult {
	if !override {
		if r := s.Exists(dst); r.Success {
			return Succeed("")
		}
	}
	sftpClient, err := sftp.NewClient(s.client)
	if err != nil {
		return Fail(err.Error())
	}
	defer sftpClient.Close()

	if err := sftpClient.MkdirAll(filepath.Dir(dst)); err != nil {
		return Fail(err.Error())
	}

	srcFile, err := os.Open(src)
	if err != nil {
		return Fail(err.Error())
	}
	defer srcFile.Close()

	dstFile, err := sftpClient.Create(dst)
	if err != nil {
		return Fail(err.Error())
	}
	defer dstFile.Close()

	if _, err := io.Copy(dstFile, srcFile); err != nil {
		return Fail(err.Error())
	}
	return Succeed("")
}

func (s *SSHExecutor) SendDir(srcDir, dstDir string, _ bool) ExecResult {
	sftpClient, err := sftp.NewClient(s.client)
	if err != nil {
		return Fail(err.Error())
	}
	defer sftpClient.Close()

	return walkAndSend(sftpClient, srcDir, dstDir)
}

func walkAndSend(sftpClient *sftp.Client, srcDir, dstDir string) ExecResult {
	entries, err := os.ReadDir(srcDir)
	if err != nil {
		return Fail(err.Error())
	}
	if err := sftpClient.MkdirAll(dstDir); err != nil {
		return Fail(err.Error())
	}
	for _, entry := range entries {
		srcPath := filepath.Join(srcDir, entry.Name())
		dstPath := filepath.Join(dstDir, entry.Name())
		if entry.IsDir() {
			if r := walkAndSend(sftpClient, srcPath, dstPath); !r.Success {
				return r
			}
			continue
		}
		srcFile, err := os.Open(srcPath)
		if err != nil {
			return Fail(err.Error())
		}
		dstFile, err := sftpClient.Create(dstPath)
		if err != nil {
			srcFile.Close()
			return Fail(err.Error())
		}
		_, copyErr := io.Copy(dstFile, srcFile)
		srcFile.Close()
		dstFile.Close()
		if copyErr != nil {
			return Fail(copyErr.Error())
		}
	}
	return Succeed("")
}

func (s *SSHExecutor) GetFileString(path string) ExecResult {
	sftpClient, err := sftp.NewClient(s.client)
	if err != nil {
		return Fail(err.Error())
	}
	defer sftpClient.Close()

	f, err := sftpClient.Open(path)
	if err != nil {
		return Fail(err.Error())
	}
	defer f.Close()

	data, err := io.ReadAll(f)
	if err != nil {
		return Fail(err.Error())
	}
	return Succeed(string(data))
}

func (s *SSHExecutor) WriteFromStream(in io.Reader, path string) ExecResult {
	sftpClient, err := sftp.NewClient(s.client)
	if err != nil {
		return Fail(err.Error())
	}
	defer sftpClient.Close()

	if err := sftpClient.MkdirAll(filepath.Dir(path)); err != nil {
		return Fail(err.Error())
	}
	f, err := sftpClient.Create(path)
	if err != nil {
		return Fail(err.Error())
	}
	defer f.Close()

	if _, err := io.Copy(f, in); err != nil {
		return Fail(err.Error())
	}
	return Succeed("")
}

func (s *SSHExecutor) WriteLines(lines []string, path string) ExecResult {
	var buf bytes.Buffer
	w := bufio.NewWriter(&buf)
	for _, line := range lines {
		w.WriteString(line + "\n")
	}
	w.Flush()
	return s.WriteFromStream(&buf, path)
}

func (s *SSHExecutor) GetOs() osinfo.OsType {
	r := s.ExecShell("cat /etc/os-release 2>/dev/null || echo ''")
	if !r.Success {
		return osinfo.OsTypeOther
	}
	return osinfo.ParseOsRelease(r.Output)
}

func (s *SSHExecutor) GetArch() osinfo.ArchType {
	r := s.ExecShell("uname -m")
	if !r.Success {
		return osinfo.ArchOther
	}
	return osinfo.ParseArch(r.Output)
}
