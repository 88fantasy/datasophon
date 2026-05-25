package initcmd

import (
	"fmt"
	"log/slog"
	"os"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitDocker 对应 Java InitDocker — 安装 Docker（仅当 enableKubernetesCluster=true）。
type InitDocker struct {
	TaskBase
	EnableK8sCluster bool
	PackagePath      string
	InstallPath      string
	X86Tar           string
	Aarch64Tar       string
	DockerHTTPPort   int
	KubernetesForce  bool
}

func (t *InitDocker) Name() string { return "安装docker" }

func (t *InitDocker) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitDocker) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "docker",
		Short: "安装 Docker",
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
	cmd.Flags().IntVar(&t.DockerHTTPPort, "dockerHttpPort", 0, "Docker Registry HTTP 端口（必填）")
	cmd.Flags().BoolVar(&t.KubernetesForce, "kubernetesForce", false, "已存在时是否覆盖安装")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("x86Tar")
	_ = cmd.MarkFlagRequired("aarch64Tar")
	_ = cmd.MarkFlagRequired("dockerHttpPort")
	return cmd
}

func (t *InitDocker) doRun(exec executor.Executor) bool {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过 docker 安装")
		return true
	}

	dockerOut := exec.ExecShell("docker version").Output
	if strings.Contains(dockerOut, "API") {
		if t.KubernetesForce {
			slog.Info("docker 已安装，正在卸载")
			exec.ExecShell("systemctl stop docker")
			exec.ExecShell("rm -rf /var/lib/docker")
			exec.ExecShell("rm -rf /etc/docker")
			exec.ExecShell("rm -f /run/docker.sock")
			exec.ExecShell("rm -f /usr/bin/docker*")
		} else {
			slog.Info("docker 已安装，跳过")
			return true
		}
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
		os.Exit(1)
	}

	softPath := fmt.Sprintf("%s/docker", t.InstallPath)
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", softPath))
	exec.ExecShell(fmt.Sprintf("tar -xvf %s -C %s", tarPath, softPath))
	exec.ExecShell(fmt.Sprintf("cp -rf %s/docker/* /usr/bin/", softPath))

	exec.WriteLines(dockerServiceConf(), "/etc/systemd/system/docker.service")
	exec.ExecShell("chmod +x /etc/systemd/system/docker.service")
	exec.ExecShell("systemctl enable docker.service")
	exec.ExecShell("systemctl start docker")

	exec.ExecShell("mkdir -p /etc/docker")
	exec.WriteLines([]string{
		"{",
		fmt.Sprintf("\"insecure-registries\": [\"%s:%d\"]", t.RegistryIP, t.DockerHTTPPort),
		"}",
	}, "/etc/docker/daemon.json")

	b64 := exec.ExecShell(fmt.Sprintf("echo -n '%s:%s' | base64", t.RegistryUsername, t.RegistryPassword)).Output
	exec.WriteLines([]string{
		"{",
		fmt.Sprintf("\"auths\": {\"http://%s:%d\": {\"auth\": \"%s\"}}", t.RegistryIP, t.DockerHTTPPort, strings.TrimSpace(b64)),
		"}",
	}, "/root/.docker/config.json")

	exec.ExecShell("systemctl daemon-reload")
	exec.ExecShell("systemctl restart docker")

	if r := exec.ExecShell("systemctl status docker"); !r.Success {
		slog.Error("docker 安装失败")
		os.Exit(1)
	}
	if r := exec.ExecShell(fmt.Sprintf("docker login %s:%d", t.RegistryIP, t.DockerHTTPPort)); !r.Success {
		slog.Error("docker login 失败")
		os.Exit(1)
	}
	slog.Info("docker 安装成功")
	return true
}

func dockerServiceConf() []string {
	return []string{
		"[Unit]",
		"Description=Docker Application Container Engine",
		"Documentation=https://docs.docker.com",
		"After=network-online.target firewalld.service",
		"Wants=network-online.target",
		"",
		"[Service]",
		"Type=notify",
		"ExecStart=/usr/bin/dockerd",
		"ExecReload=/bin/kill -s HUP $MAINPID",
		"LimitNOFILE=infinity",
		"LimitNPROC=infinity",
		"LimitCORE=infinity",
		"TimeoutStartSec=0",
		"Delegate=yes",
		"KillMode=process",
		"Restart=on-failure",
		"StartLimitBurst=3",
		"StartLimitInterval=60s",
		"",
		"[Install]",
		"WantedBy=multi-user.target",
	}
}
