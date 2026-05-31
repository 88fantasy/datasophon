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

// InitContainerd installs containerd + runc + CNI plugins from offline Nexus packages,
// following the containerd 2.3 official getting-started guide.
type InitContainerd struct {
	TaskBase
	EnableK8sCluster bool
	KubernetesForce  bool
	Offline          bool
	PackagePath      string
	DockerHTTPPort   int

	ContainerdX86Tar string
	ContainerdArmTar string
	RuncX86Bin       string
	RuncArmBin       string
	CniX86Tar        string
	CniArmTar        string
}

func (t *InitContainerd) Name() string { return "安装containerd" }

func (t *InitContainerd) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitContainerd) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "containerd",
		Short: "安装 containerd + runc + CNI plugins",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVar(&t.EnableK8sCluster, "enableKubernetesCluster", true, "是否安装 kubernetes 集群")
	cmd.Flags().BoolVar(&t.KubernetesForce, "kubernetesForce", false, "已存在时是否覆盖安装")
	cmd.Flags().BoolVar(&t.Offline, "offline", false, "离线环境：将 Nexus Docker 仓库配置为 containerd 镜像源")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().IntVar(&t.DockerHTTPPort, "dockerHttpPort", 0, "Nexus Docker 仓库 HTTP 端口（离线模式必填）")
	cmd.Flags().StringVar(&t.ContainerdX86Tar, "containerdX86Tar", "", "containerd x86_64 tar 包（必填）")
	cmd.Flags().StringVar(&t.ContainerdArmTar, "containerdArmTar", "", "containerd aarch64 tar 包（必填）")
	cmd.Flags().StringVar(&t.RuncX86Bin, "runcX86Bin", "", "runc x86_64 二进制（必填）")
	cmd.Flags().StringVar(&t.RuncArmBin, "runcArmBin", "", "runc aarch64 二进制（必填）")
	cmd.Flags().StringVar(&t.CniX86Tar, "cniX86Tar", "", "CNI plugins x86_64 tar 包（必填）")
	cmd.Flags().StringVar(&t.CniArmTar, "cniArmTar", "", "CNI plugins aarch64 tar 包（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("containerdX86Tar")
	_ = cmd.MarkFlagRequired("containerdArmTar")
	_ = cmd.MarkFlagRequired("runcX86Bin")
	_ = cmd.MarkFlagRequired("runcArmBin")
	_ = cmd.MarkFlagRequired("cniX86Tar")
	_ = cmd.MarkFlagRequired("cniArmTar")
	return cmd
}

func (t *InitContainerd) doRun(exec executor.Executor) error {
	if !t.EnableK8sCluster {
		slog.Info("k8s 集群安装未开启，跳过 containerd 安装")
		return nil
	}

	if r := exec.ExecShell("containerd --version"); r.Success {
		if t.KubernetesForce {
			slog.Info("containerd 已安装，正在卸载")
			exec.ExecShell("systemctl stop containerd")
			exec.ExecShell("systemctl disable containerd")
			exec.ExecShell("rm -rf /var/lib/containerd")
			exec.ExecShell("rm -rf /etc/containerd")
			exec.ExecShell("rm -f /usr/local/bin/containerd*")
			exec.ExecShell("rm -f /usr/local/bin/ctr")
			exec.ExecShell("rm -f /usr/local/sbin/runc")
			exec.ExecShell("rm -rf /opt/cni/bin")
		} else {
			slog.Info("containerd 已安装，跳过")
			return nil
		}
	}

	isArm := exec.GetArch() == osinfo.ArchAarch64
	containerdTar := t.ContainerdX86Tar
	runcBin := t.RuncX86Bin
	cniTar := t.CniX86Tar
	if isArm {
		containerdTar = t.ContainerdArmTar
		runcBin = t.RuncArmBin
		cniTar = t.CniArmTar
	}

	containerdPath := fmt.Sprintf("%s/%s", t.PackagePath, containerdTar)
	runcPath := fmt.Sprintf("%s/%s", t.PackagePath, runcBin)
	cniPath := fmt.Sprintf("%s/%s", t.PackagePath, cniTar)

	for _, pair := range [][2]string{
		{containerdTar, containerdPath},
		{runcBin, runcPath},
		{cniTar, cniPath},
	} {
		if err := DownloadFromRegistry(exec, t.EnableRegistry,
			t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
			pair[0], pair[1], true); err != nil {
			return err
		}
	}

	for _, path := range []string{containerdPath, runcPath, cniPath} {
		if !exec.Exists(path).Success {
			slog.Error("安装包不存在", "path", path)
			return fmt.Errorf("安装包不存在: %s", path)
		}
	}

	// containerd binaries → /usr/local/bin/
	exec.ExecShell(fmt.Sprintf("tar Cxzvf /usr/local %s", containerdPath))

	// runc → /usr/local/sbin/runc
	exec.ExecShell(fmt.Sprintf("install -m 755 %s /usr/local/sbin/runc", runcPath))

	// CNI plugins → /opt/cni/bin/
	exec.ExecShell("mkdir -p /opt/cni/bin")
	exec.ExecShell(fmt.Sprintf("tar Cxzvf /opt/cni/bin %s", cniPath))

	// systemd unit
	exec.ExecShell("mkdir -p /usr/local/lib/systemd/system")
	exec.WriteLines(containerdServiceConf(), "/usr/local/lib/systemd/system/containerd.service")
	exec.ExecShell("systemctl daemon-reload")

	// default config with SystemdCgroup enabled (required by kubelet)
	exec.ExecShell("mkdir -p /etc/containerd")
	exec.ExecShell("containerd config default > /etc/containerd/config.toml")
	exec.ExecShell("sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml")

	// In offline environments, configure Nexus Docker repository as the image registry mirror.
	if t.Offline && t.EnableRegistry && t.DockerHTTPPort > 0 {
		t.configureOfflineRegistry(exec)
	}

	exec.ExecShell("systemctl enable --now containerd")
	if r := exec.ExecShell("systemctl status containerd"); !r.Success {
		slog.Error("containerd 启动失败")
		return errors.New("containerd 启动失败")
	}
	if r := exec.ExecShell("ctr version"); !r.Success {
		slog.Error("ctr 验证失败")
		return errors.New("ctr 验证失败")
	}
	slog.Info("containerd 安装成功")
	return nil
}

// configureOfflineRegistry configures containerd to use Nexus as the image registry mirror
// using the containerd 2.x hosts.toml mechanism.
func (t *InitContainerd) configureOfflineRegistry(exec executor.Executor) {
	nexusAddr := fmt.Sprintf("http://%s:%d", t.RegistryIP, t.DockerHTTPPort)
	certsD := "/etc/containerd/certs.d"

	// Activate the hosts directory in containerd config.toml.
	exec.ExecShell(fmt.Sprintf(
		`sed -i 's|config_path = ""|config_path = "%s"|' /etc/containerd/config.toml`,
		certsD,
	))

	// Direct access configuration for the Nexus Docker registry itself.
	nexusCertDir := fmt.Sprintf("%s/%s:%d", certsD, t.RegistryIP, t.DockerHTTPPort)
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", nexusCertDir))
	exec.WriteLines([]string{
		fmt.Sprintf(`server = "%s"`, nexusAddr),
		"",
		fmt.Sprintf(`[host."%s"]`, nexusAddr),
		`  capabilities = ["pull", "resolve", "push"]`,
		`  skip_verify = true`,
	}, fmt.Sprintf("%s/hosts.toml", nexusCertDir))

	// Mirror standard public registries through Nexus so image pulls succeed offline.
	for _, reg := range []struct{ name, server string }{
		{"docker.io", "https://registry-1.docker.io"},
		{"registry.k8s.io", "https://registry.k8s.io"},
		{"quay.io", "https://quay.io"},
		{"gcr.io", "https://gcr.io"},
	} {
		dir := fmt.Sprintf("%s/%s", certsD, reg.name)
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", dir))
		exec.WriteLines([]string{
			fmt.Sprintf(`server = "%s"`, reg.server),
			"",
			fmt.Sprintf(`[host."%s"]`, nexusAddr),
			`  capabilities = ["pull", "resolve"]`,
			`  skip_verify = true`,
		}, fmt.Sprintf("%s/hosts.toml", dir))
	}

	slog.Info("containerd 离线 registry 配置完成", "nexus", nexusAddr)
}

// containerdServiceConf returns the systemd unit file contents based on the official
// containerd.service from https://github.com/containerd/containerd/blob/main/containerd.service
func containerdServiceConf() []string {
	return []string{
		"[Unit]",
		"Description=containerd container runtime",
		"Documentation=https://containerd.io",
		"After=network.target local-fs.target dbus.service",
		"",
		"[Service]",
		"ExecStartPre=-/sbin/modprobe overlay",
		"ExecStart=/usr/local/bin/containerd",
		"",
		"Type=notify",
		"Delegate=yes",
		"KillMode=process",
		"Restart=always",
		"RestartSec=5",
		"",
		"LimitNPROC=infinity",
		"LimitCORE=infinity",
		"TasksMax=infinity",
		"OOMScoreAdjust=-999",
		"",
		"[Install]",
		"WantedBy=multi-user.target",
	}
}
