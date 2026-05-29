package create

import (
	"errors"
	"fmt"
	"log/slog"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// ntpServerTask 安装并配置 chrony NTP 服务端。
type ntpServerTask struct{}

func (t *ntpServerTask) Name() string { return "ntpserver时钟配置" }

func (t *ntpServerTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.run(executor.NewSSHExecutor(client, dryRun))
}

func (t *ntpServerTask) run(exec executor.Executor) error {
	osType := exec.GetOs()

	checkCmd := "rpm -qa | grep chrony"
	installCmd := "yum -y install chrony"
	chronyConfPath := "/etc/chrony.conf"
	mvCmd := "mv /etc/chrony.conf /etc/chrony.conf.$(date +%Y%m%d.%H%M%S)"
	enableCmd := "systemctl enable chronyd"
	if osType.IsUbuntu() {
		checkCmd = "dpkg --list|grep chrony"
		installCmd = "DEBIAN_FRONTEND=noninteractive apt install chrony -y"
		chronyConfPath = "/etc/chrony/chrony.conf"
		mvCmd = "mv /etc/chrony/chrony.conf /etc/chrony/chrony.conf.$(date +%Y%m%d.%H%M%S)"
		enableCmd = "systemctl enable chrony"
	}

	exec.ExecShell(installCmd)
	if r := exec.ExecShell(checkCmd); !r.Success {
		slog.Error("chrony 安装失败")
		return errors.New("chrony 安装失败")
	}
	exec.ExecShell(mvCmd)

	conf := []string{
		"server 127.0.0.1 iburst",
		"driftfile /var/lib/chrony/drift",
		"makestep 1.0 3",
		"rtcsync",
		"allow all",
		"local stratum 10",
		"keyfile /etc/chrony.keys",
		"leapsectz right/UTC",
		"logdir /var/log/chrony",
	}
	exec.WriteLines(conf, chronyConfPath)
	slog.Info("chrony.conf 已写入", "path", chronyConfPath)

	exec.ExecShell(enableCmd)
	if osType.IsUbuntu() {
		exec.ExecShell("systemctl restart chronyd")
		exec.ExecShell("systemctl restart chrony")
	} else {
		exec.ExecShell("systemctl restart chronyd")
	}
	exec.ExecShell("chronyc sources")
	slog.Info("ntpserver 配置完成")
	return nil
}

type createNtpServerCmd struct {
	configFile string
	dryRun     bool
}

func NewNtpServerCommand(dryRun *bool) *cobra.Command {
	c := &createNtpServerCmd{}
	cmd := &cobra.Command{
		Use:   "ntp-server",
		Short: "在 ntpServer 节点上安装并配置 chrony NTP 服务端",
		Long: `安装并配置 chrony NTP 服务端，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 ntpServer 读取节点，
     SSH 到目标节点远程执行；安装成功后将 ntpServer.enable 置为 true 写回配置文件。

  2. 手动模式（不指定 -c）：在本地节点直接执行安装与配置。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 ntpServer 读取节点）")
	return cmd
}

func (c *createNtpServerCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runLocally()
}

func (c *createNtpServerCmd) runFromConfig() error {
	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[cfg.NtpServer.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 ntpServer 节点: %s（请检查 ntpServer.node 与 nodes 列表是否一致）", cfg.NtpServer.Node)
	}

	t := &ntpServerTask{}
	if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
		return err
	}

	cfg.NtpServer.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createNtpServerCmd) runLocally() error {
	t := &ntpServerTask{}
	return t.run(executor.NewLocalExecutor(c.dryRun))
}
