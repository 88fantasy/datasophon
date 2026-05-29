package initcmd

import (
	"errors"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"golang.org/x/crypto/ssh"
)

// InitNtpServer 对应 Java InitNtpServer — 安装并配置 chrony NTP 服务端。
type InitNtpServer struct{ TaskBase }

func (t *InitNtpServer) Name() string { return "ntpserver时钟配置" }

func (t *InitNtpServer) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

// Run 导出 doRun，供 create 包调用。
func (t *InitNtpServer) Run(exec executor.Executor) error { return t.doRun(exec) }

func (t *InitNtpServer) doRun(exec executor.Executor) error {
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
