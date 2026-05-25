package initcmd

import (
	"errors"
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitNtpSlave 对应 Java InitNtpSlave — 安装并配置 chrony NTP 从节点。
type InitNtpSlave struct {
	TaskBase
	NtpServerIP string
}

func (t *InitNtpSlave) Name() string { return "ntp slave配置" }

func (t *InitNtpSlave) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitNtpSlave) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "ntpslave",
		Short: "配置 chrony NTP 从节点",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.NtpServerIP, "ntpServerIp", "", "NTP 服务端 IP（必填）")
	_ = cmd.MarkFlagRequired("ntpServerIp")
	return cmd
}

func (t *InitNtpSlave) doRun(exec executor.Executor) error {
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
		fmt.Sprintf("server %s iburst", t.NtpServerIP),
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
	slog.Info("ntpSlave 配置完成")
	return nil
}
