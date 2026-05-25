package initcmd

import (
	"fmt"
	"log/slog"
	"os"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitBinPackage 对应 Java InitBinPackage — 分发 datasophon-init 资源包到远程节点。
type InitBinPackage struct {
	TaskBase
	DatasophonInitPath     string
	InstallPath            string
	InitPathOverwriteForce bool
}

func (t *InitBinPackage) Name() string { return "分发datasophon-init资源包" }

func (t *InitBinPackage) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitBinPackage) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "bin_packages",
		Short: "分发 datasophon-init 资源包到远程节点",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.DatasophonInitPath, "datasophonInitPath", "i", "", "本地 datasophon-init 目录（必填）")
	cmd.Flags().StringVarP(&t.InstallPath, "installPath", "n", "", "远程安装路径（必填）")
	cmd.Flags().BoolVar(&t.InitPathOverwriteForce, "initPathOverwriteForce", false, "远程目录存在时是否覆盖")
	_ = cmd.MarkFlagRequired("datasophonInitPath")
	_ = cmd.MarkFlagRequired("installPath")
	return cmd
}

func (t *InitBinPackage) doRun(exec executor.Executor) bool {
	// 本地目录检查
	info, err := os.Stat(t.DatasophonInitPath)
	if err != nil || !info.IsDir() {
		slog.Error("本地目录不存在", "path", t.DatasophonInitPath)
		return false
	}

	// 确保本地 installPath 存在
	if _, err := os.Stat(t.InstallPath); os.IsNotExist(err) {
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.InstallPath))
	}

	// 远程目录检查
	if exec.Exists(t.DatasophonInitPath).Success && !t.InitPathOverwriteForce {
		slog.Info("远程 datasophon-init 目录已存在，跳过", "path", t.DatasophonInitPath, "overwrite", t.InitPathOverwriteForce)
	} else {
		if r := exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.DatasophonInitPath)); !r.Success {
			slog.Error("远程创建目录失败", "path", t.DatasophonInitPath)
			return false
		}
		slog.Info("分发资源包开始", "path", t.DatasophonInitPath)
		if r := exec.SendDir(t.DatasophonInitPath, t.DatasophonInitPath, true); !r.Success {
			slog.Error("分发资源包失败", "path", t.DatasophonInitPath)
			return false
		}
		slog.Info("分发资源包完成", "path", t.DatasophonInitPath)
	}

	// 确保远程 installPath 存在
	if !exec.Exists(t.InstallPath).Success {
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.InstallPath))
	}
	return true
}
