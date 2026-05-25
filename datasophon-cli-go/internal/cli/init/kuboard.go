package initcmd

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitK8sKuboard 对应 Java InitK8sKuboard — 用 sealos 安装 Kuboard。
type InitK8sKuboard struct {
	TaskBase
	EnableK8sCluster bool
	KuboardX86Tar    string
	KuboardArmTar    string
	Etcds            []string
	PackagePath      string
	KubernetesForce  bool
}

func (t *InitK8sKuboard) Name() string { return "安装kuboard" }

func (t *InitK8sKuboard) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitK8sKuboard) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "kuboard",
		Short: "用 sealos 安装 Kuboard",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "enableKubernetesCluster", true, "是否安装 kubernetes 集群")
	cmd.Flags().StringVar(&t.KuboardX86Tar, "kuboardX86Tar", "", "Kuboard x86 包")
	cmd.Flags().StringVar(&t.KuboardArmTar, "kuboardArmTar", "", "Kuboard arm 包")
	cmd.Flags().StringSliceVarP(&t.Etcds, "etcds", "e", nil, "etcd 节点（必填）")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().BoolVar(&t.KubernetesForce, "kubernetesForce", false, "存在时是否覆盖安装")
	_ = cmd.MarkFlagRequired("etcds")
	_ = cmd.MarkFlagRequired("packagePath")
	return cmd
}

func (t *InitK8sKuboard) doRun(exec executor.Executor) bool {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过 kuboard 安装")
		return true
	}
	prodOut := exec.ExecShell("kubectl get pods -n kuboard").Output
	if strings.Contains(prodOut, "kuboard") {
		slog.Info("kuboard pods 已存在，跳过")
		return true
	}

	isX86 := exec.GetArch() != osinfo.ArchAarch64
	tarName := t.KuboardX86Tar
	if !isX86 {
		tarName = t.KuboardArmTar
	}
	kuboardPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	slog.Info("安装 kuboard...")
	DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		tarName, kuboardPath, true)

	// 给 etcd 节点打标签
	etcdsStr := strings.Join(t.Etcds, " ")
	exec.ExecShell(fmt.Sprintf("/usr/bin/kubectl label nodes %s k8s.kuboard.cn/role=etcd", etcdsStr))

	// 验证标签
	for _, node := range t.Etcds {
		checkCmd := fmt.Sprintf("/usr/bin/kubectl get nodes --show-labels | grep %s | grep k8s.kuboard.cn/role=etcd", node)
		if r := exec.ExecShell(checkCmd); !r.Success {
			slog.Error("节点打标签失败", "node", node)
			return false
		}
	}

	// 安装 kuboard
	cmd := fmt.Sprintf("/usr/bin/sealos run %s --force=true", kuboardPath)
	if r := exec.ExecShell(cmd); !r.Success {
		slog.Error("安装 kuboard 失败")
		return false
	}
	slog.Info("kuboard 安装成功，访问地址：http://ip:30080（默认账号 admin，首次登录请修改密码）")
	return true
}
