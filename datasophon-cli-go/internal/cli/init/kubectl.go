package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitKubectl 对应 Java InitKubectl — 安装 kubectl 二进制（仅当 enableKubernetesCluster=true）。
type InitKubectl struct {
	TaskBase
	EnableK8sCluster bool
	PackagePath      string
	InstallPath      string
	X86Tar           string
	Aarch64Tar       string
}

func (t *InitKubectl) Name() string { return "安装kubectl" }

func (t *InitKubectl) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitKubectl) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "kubectl",
		Short: "安装 kubectl",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "enableKubernetesCluster", true, "是否安装 kubernetes 集群")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	cmd.Flags().StringVarP(&t.X86Tar, "x86Tar", "x", "", "x86_64 包（必填）")
	cmd.Flags().StringVarP(&t.Aarch64Tar, "aarch64Tar", "a", "", "aarch64 包（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("x86Tar")
	_ = cmd.MarkFlagRequired("aarch64Tar")
	return cmd
}

func (t *InitKubectl) doRun(exec executor.Executor) bool {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过 kubectl 安装")
		return true
	}
	if r := exec.ExecShell("kubectl version"); r.Success {
		slog.Info("kubectl 已安装")
		return true
	}

	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		tarName, tarPath, true)

	if !exec.Exists(tarPath).Success {
		slog.Error("安装包不存在", "path", tarPath)
		return false
	}
	exec.ExecShell(fmt.Sprintf("cp %s /usr/bin/kubectl", tarPath))
	slog.Info("kubectl 安装成功")
	return true
}
