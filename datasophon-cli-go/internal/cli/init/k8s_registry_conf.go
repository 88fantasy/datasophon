package initcmd

import (
	"bytes"
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitK8sRegistryConf 对应 Java InitK8sRegistryConf — 配置 containerd 私有镜像仓库认证。
type InitK8sRegistryConf struct {
	TaskBase
	EnableK8sCluster bool
	DockerHTTPPort   int
}

func (t *InitK8sRegistryConf) Name() string { return "初始化私有仓库nexus配置" }

func (t *InitK8sRegistryConf) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitK8sRegistryConf) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "k8sRegistryConf",
		Short: "配置 containerd 私有镜像仓库认证",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "enableKubernetesCluster", true, "是否安装 kubernetes 集群")
	cmd.Flags().IntVar(&t.DockerHTTPPort, "dockerHttpPort", 0, "Docker Registry HTTP 端口（必填）")
	_ = cmd.MarkFlagRequired("dockerHttpPort")
	return cmd
}

func (t *InitK8sRegistryConf) doRun(exec executor.Executor) bool {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过")
		return true
	}

	configTomlPath := "/etc/containerd/config.toml"
	certsdPath := "/etc/containerd/certs.d"
	hostPort := fmt.Sprintf("%s:%d", t.RegistryIP, t.DockerHTTPPort)
	certsdHostPortDir := fmt.Sprintf("%s/%s", certsdPath, hostPort)
	certsdHostPortFilePath := fmt.Sprintf("%s/hosts.toml", certsdHostPortDir)

	if !exec.Exists(certsdPath).Success {
		slog.Error("containerd certs.d 目录不存在", "path", certsdPath)
		return false
	}
	if !exec.Exists(configTomlPath).Success {
		slog.Error("config.toml 不存在", "path", configTomlPath)
		return false
	}

	exec.ExecShell(fmt.Sprintf("mkdir -p %s", certsdHostPortDir))
	exec.WriteLines([]string{
		fmt.Sprintf("server = \"http://%s\"", hostPort),
		fmt.Sprintf("[host.\"http://%s\"]", hostPort),
		"  capabilities = [\"pull\", \"resolve\", \"push\"]",
		"  skip_verify = true",
	}, certsdHostPortFilePath)

	configTomlContent := exec.GetFileString(configTomlPath).Output
	if !bytes.Contains([]byte(configTomlContent), []byte(hostPort)) {
		configTomlContent += fmt.Sprintf(
			"\n          [plugins.\"io.containerd.grpc.v1.cri\".registry.configs.\"%s\".auth]\n"+
				"            username = \"%s\"\n"+
				"            password = \"%s\"\n",
			hostPort, t.RegistryUsername, t.RegistryPassword,
		)
		in := bytes.NewBufferString(configTomlContent)
		exec.WriteFromStream(in, configTomlPath)
	}
	exec.ExecShell("systemctl restart containerd")
	slog.Info("k8sRegistryConf 配置完成")
	return true
}
