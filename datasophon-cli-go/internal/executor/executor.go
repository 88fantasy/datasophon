package executor

import (
	"io"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
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
