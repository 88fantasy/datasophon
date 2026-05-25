package initcmd

import (
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitK8sBaseServices 对应 Java InitK8sBaseServices — 用 sealos 安装 Kubernetes 集群。
type InitK8sBaseServices struct {
	TaskBase
	EnableK8sCluster bool
	KubernetesForce  bool
	Namespaces       []string
	Masters          []string
	Nodes            []string
	Sealos           bool
	SealosX86Tar     string
	SealosArmTar     string
	Kubernetes       bool
	KubernetesX86Tar string
	KubernetesArmTar string
	Helm             bool
	HelmX86Tar       string
	HelmArmTar       string
	Calico           bool
	CalicoX86Tar     string
	CalicoArmTar     string
	Ingress          bool
	IngressX86Tar    string
	IngressArmTar    string
	PackagePath      string
	SSHPort          int
	SSHPasswd        string
}

func (t *InitK8sBaseServices) Name() string { return "安装k8s集群" }

func (t *InitK8sBaseServices) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitK8sBaseServices) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "k8sBaseServices",
		Short: "用 sealos 安装 Kubernetes 集群",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "enableKubernetesCluster", true, "是否安装 kubernetes 集群")
	cmd.Flags().BoolVar(&t.KubernetesForce, "kubernetesForce", false, "存在时是否覆盖安装")
	cmd.Flags().StringSliceVar(&t.Namespaces, "namespaces", nil, "命名空间列表")
	cmd.Flags().StringSliceVar(&t.Masters, "masters", nil, "主节点（必填）")
	cmd.Flags().StringSliceVar(&t.Nodes, "nodes", nil, "计算节点（必填）")
	cmd.Flags().BoolVar(&t.Sealos, "sealos", true, "是否安装 sealos")
	cmd.Flags().StringVar(&t.SealosX86Tar, "sealosX86Tar", "", "sealos x86 包（必填）")
	cmd.Flags().StringVar(&t.SealosArmTar, "sealosArmTar", "", "sealos arm 包（必填）")
	cmd.Flags().BoolVar(&t.Kubernetes, "kubernetes", true, "是否安装 kubernetes")
	cmd.Flags().StringVar(&t.KubernetesX86Tar, "kubernetesX86Tar", "", "kubernetes x86 包（必填）")
	cmd.Flags().StringVar(&t.KubernetesArmTar, "kubernetesArmTar", "", "kubernetes arm 包（必填）")
	cmd.Flags().BoolVar(&t.Helm, "helm", true, "是否安装 helm")
	cmd.Flags().StringVar(&t.HelmX86Tar, "helmTX86ar", "", "helm x86 包（必填）")
	cmd.Flags().StringVar(&t.HelmArmTar, "helmArmTar", "", "helm arm 包（必填）")
	cmd.Flags().BoolVar(&t.Calico, "calico", true, "是否安装 calico")
	cmd.Flags().StringVar(&t.CalicoX86Tar, "calicoX86", "", "calico x86 包（必填）")
	cmd.Flags().StringVar(&t.CalicoArmTar, "calicoArm", "", "calico arm 包（必填）")
	cmd.Flags().BoolVar(&t.Ingress, "ingress", true, "是否安装 ingress")
	cmd.Flags().StringVar(&t.IngressX86Tar, "ingressX86", "", "ingress x86 包（必填）")
	cmd.Flags().StringVar(&t.IngressArmTar, "ingressArm", "", "ingress arm 包（必填）")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().IntVar(&t.SSHPort, "sshPort", 22, "ssh 端口")
	cmd.Flags().StringVar(&t.SSHPasswd, "sshPasswd", "", "ssh 密码")
	_ = cmd.MarkFlagRequired("masters")
	_ = cmd.MarkFlagRequired("nodes")
	_ = cmd.MarkFlagRequired("packagePath")
	return cmd
}

func (t *InitK8sBaseServices) doRun(exec executor.Executor) bool {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过")
		return true
	}
	isX86 := exec.GetArch() != osinfo.ArchAarch64
	osType := exec.GetOs()

	sealosPath := t.pkgPath(isX86, t.SealosX86Tar, t.SealosArmTar)
	kubernetesPath := t.pkgPath(isX86, t.KubernetesX86Tar, t.KubernetesArmTar)
	helmPath := t.pkgPath(isX86, t.HelmX86Tar, t.HelmArmTar)
	calicoPath := t.pkgPath(isX86, t.CalicoX86Tar, t.CalicoArmTar)
	ingressPath := t.pkgPath(isX86, t.IngressX86Tar, t.IngressArmTar)

	if installed := exec.ExecShell("kubectl version"); installed.Success {
		if t.KubernetesForce {
			slog.Info("k8s 已安装，记录删除命令（不执行，避免卸载不干净）")
			slog.Info("sealos delete --nodes ...", "nodes", strings.Join(t.Nodes, ","))
			slog.Info("sealos delete --masters ...", "masters", strings.Join(t.Masters, ","))
			slog.Info("sealos reset --force=true")
		} else {
			slog.Info("k8s 已安装，跳过")
			return true
		}
	}

	// 卸载 docker（sealos 要求）
	slog.Info("卸载 docker（sealos 要求）")
	exec.ExecShell("systemctl stop docker")
	exec.ExecShell("systemctl disable docker")
	if osType.IsUbuntu() {
		exec.ExecShell("apt remove -y docker*")
	} else {
		exec.ExecShell("yum remove -y docker*")
	}
	exec.ExecShell("rm -rf /var/lib/docker")
	exec.ExecShell("rm -rf /etc/docker")
	exec.ExecShell("rm -f /run/docker.sock")
	exec.ExecShell("rm -f /usr/bin/docker*")

	if len(t.Nodes) < 3 {
		slog.Error("nodes 节点不能少于 3")
		return false
	}

	// 安装 sealos
	if t.Sealos {
		slog.Info("安装 sealos")
		sealosCmd := fmt.Sprintf("tar zxvf %s sealos && chmod +x sealos && mv sealos /usr/bin", sealosPath)
		if r := exec.ExecShell(sealosCmd); !r.Success {
			slog.Error("sealos 安装失败")
			return false
		}
		slog.Info("sealos 安装成功")
	}

	// 下载各安装包
	for _, pair := range [][2]string{
		{t.tarName(isX86, t.SealosX86Tar, t.SealosArmTar), sealosPath},
		{t.tarName(isX86, t.KubernetesX86Tar, t.KubernetesArmTar), kubernetesPath},
		{t.tarName(isX86, t.HelmX86Tar, t.HelmArmTar), helmPath},
		{t.tarName(isX86, t.CalicoX86Tar, t.CalicoArmTar), calicoPath},
		{t.tarName(isX86, t.IngressX86Tar, t.IngressArmTar), ingressPath},
	} {
		DownloadFromRegistry(exec, t.EnableRegistry,
			t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
			pair[0], pair[1], true)
	}

	// 安装 kubernetes
	slog.Info("安装 kubernetes")
	t.sealosInstall(exec, kubernetesPath, true)
	time.Sleep(5 * time.Second)

	// 安装插件
	slog.Info("安装 k8s 插件")
	t.sealosInstall(exec, calicoPath, false)
	t.sealosInstall(exec, helmPath, false)
	t.sealosInstall(exec, ingressPath, false)

	// 等待集群就绪（最多 5 分钟）
	for i := 0; i < 60; i++ {
		if t.isKubernetesReady(exec) {
			break
		}
		slog.Info("等待 k8s 集群就绪...", "retry", i+1)
		time.Sleep(5 * time.Second)
		if i == 59 {
			slog.Error("k8s 集群就绪超时")
			return false
		}
	}

	// 创建命名空间
	for _, ns := range t.Namespaces {
		exec.ExecShell(fmt.Sprintf("/usr/bin/kubectl create namespace %s", ns))
	}

	slog.Info("k8s 集群安装成功")
	return true
}

func (t *InitK8sBaseServices) pkgPath(isX86 bool, x86, arm string) string {
	name := x86
	if !isX86 {
		name = arm
	}
	return fmt.Sprintf("%s/%s", t.PackagePath, name)
}

func (t *InitK8sBaseServices) tarName(isX86 bool, x86, arm string) string {
	if isX86 {
		return x86
	}
	return arm
}

func (t *InitK8sBaseServices) isKubernetesReady(exec executor.Executor) bool {
	r := exec.ExecShell("/usr/bin/kubectl get nodes | grep -v NAME | awk '{print $2}' | xargs echo")
	for _, status := range strings.Fields(r.Output) {
		if strings.TrimSpace(status) != "Ready" {
			return false
		}
	}
	return true
}

func (t *InitK8sBaseServices) sealosInstall(exec executor.Executor, imagePath string, isKubernetes bool) {
	cmd := fmt.Sprintf("/usr/bin/sealos run %s --port=%d --force=true", imagePath, t.SSHPort)
	if isKubernetes {
		cmd += fmt.Sprintf(" --masters %s --nodes %s", strings.Join(t.Masters, ","), strings.Join(t.Nodes, ","))
	}
	if t.SSHPasswd != "" {
		cmd += fmt.Sprintf(" --passwd=\"%s\"", t.SSHPasswd)
	}
	r := exec.ExecShell(cmd)
	slog.Info("sealos 安装", "path", imagePath, "success", r.Success)
	if !r.Success {
		slog.Error("sealos 安装失败", "path", imagePath)
		panic(fmt.Sprintf("%s 安装失败", imagePath))
	}
}
