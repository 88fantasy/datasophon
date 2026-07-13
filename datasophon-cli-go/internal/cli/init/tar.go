package initcmd

import (
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"sort"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitTar 对应 Java InitTar — 确认 tar 命令已存在。
// Java 注释：TODO 默认已安装 tar，废弃，在线安装。
type InitTar struct {
	TaskBase
	PackagePath         string
	ProductPackagesPath string
}

func (t *InitTar) Name() string { return "安装tar" }

func (t *InitTar) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitTar) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "tar",
		Short: "确认 tar 命令存在",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.PackagePath, "packagePath", "p", "", "安装包目录")
	cmd.Flags().StringVar(&t.ProductPackagesPath, "productPackagesPath", "", "离线源根目录（-n），用于缺失时离线安装 tar")
	return cmd
}

func (t *InitTar) doRun(exec executor.Executor) error {
	r := exec.ExecShell("which tar")
	if r.Success && strings.TrimSpace(r.Output) != "" {
		slog.Info("tar 已存在", "path", strings.TrimSpace(r.Output))
		return nil
	}

	if t.ProductPackagesPath == "" {
		slog.Error("tar 命令不存在，请手动安装")
		return errors.New("tar 命令不存在")
	}

	arch := exec.GetArch()
	osType := exec.GetOs()
	repoDir := filepath.Join(t.ProductPackagesPath, "yum", string(arch), string(osType))

	var glob, installCmdFmt string
	switch {
	case osType.IsUbuntu():
		glob = filepath.Join(repoDir, "tar_*.deb")
		installCmdFmt = "dpkg -i %s"
	case osType.IsCentos():
		glob = filepath.Join(repoDir, "tar-[0-9]*.rpm")
		installCmdFmt = "rpm -ivh %s"
	default:
		return fmt.Errorf("不支持的 OS: %s", string(osType))
	}

	matches, err := filepath.Glob(glob)
	if err != nil {
		return fmt.Errorf("检索 tar 安装包失败 glob=%s: %w", glob, err)
	}
	if len(matches) == 0 {
		return fmt.Errorf("离线源目录未找到 tar 安装包: %s（请把 tar 的 rpm/deb 放到该目录）", glob)
	}
	sort.Strings(matches)
	localPkg := matches[0]
	if len(matches) > 1 {
		slog.Warn("匹配到多个 tar 安装包，取排序首个", "picked", localPkg, "all", matches)
	}

	remotePkg := "/tmp/" + filepath.Base(localPkg)
	slog.Info("分发 tar 安装包", "src", localPkg, "dst", remotePkg)
	if sr := exec.SendFile(localPkg, remotePkg, true); !sr.Success {
		return fmt.Errorf("分发 tar 安装包失败 dst=%s: %s", remotePkg, sr.ErrOutput)
	}

	installCmd := fmt.Sprintf(installCmdFmt, shellQuote(remotePkg))
	slog.Info("安装 tar", "cmd", installCmd)
	if ir := exec.ExecShell(installCmd); !ir.Success {
		return fmt.Errorf("安装 tar 失败: %s", ir.ErrOutput)
	}
	exec.ExecShell("rm -f " + shellQuote(remotePkg))

	r = exec.ExecShell("which tar")
	if !(r.Success && strings.TrimSpace(r.Output) != "") {
		return errors.New("tar 安装后仍不可用")
	}
	slog.Info("tar 安装完成", "path", strings.TrimSpace(r.Output))
	return nil
}

func shellQuote(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}
