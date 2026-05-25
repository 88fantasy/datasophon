package initcmd

import (
	"errors"
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitHelmify 对应 Java InitHelmify — 安装 Helmify。
type InitHelmify struct {
	TaskBase
	EnableK8sCluster bool
	PackagePath      string
	InstallPath      string
	X86Tar           string
	Aarch64Tar       string
}

func (t *InitHelmify) Name() string { return "安装helmify" }

func (t *InitHelmify) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitHelmify) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "helmify",
		Short: "安装 Helmify",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "kubernetesCluster", true, "是否安装 kubernetes 集群")
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

func (t *InitHelmify) doRun(exec executor.Executor) error {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过 helmify 安装")
		return nil
	}
	if r := exec.ExecShell("helmify -version"); r.Success {
		slog.Info("helmify 已安装")
		return nil
	}

	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	if err := DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		tarName, tarPath, true); err != nil {
		return err
	}

	if !exec.Exists(tarPath).Success {
		slog.Error("安装包不存在", "path", tarPath)
		return errors.New("安装包不存在")
	}
	softPath := fmt.Sprintf("%s/helmify", t.InstallPath)
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", softPath))
	exec.ExecShell(fmt.Sprintf("tar -xvf %s -C %s", tarPath, softPath))
	exec.ExecShell(fmt.Sprintf("cp %s/* /usr/bin/", softPath))
	slog.Info("helmify 安装成功")
	return nil
}
