package initcmd

import (
	"log/slog"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitSystemConf 对应 Java InitSystemConf — 设置操作系统配置（limits/sysctl/rc-local）。
type InitSystemConf struct{ TaskBase }

func (t *InitSystemConf) Name() string { return "设置操作系统配置" }

func (t *InitSystemConf) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitSystemConf) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "system-conf",
		Short: "设置操作系统配置（limits/sysctl/rc-local）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}

func (t *InitSystemConf) doRun(exec executor.Executor) bool {
	// /etc/systemd/system.conf
	r := exec.GetFileString("/etc/systemd/system.conf")
	if r.Success {
		slog.Info("写入 system.conf")
		lines := filterLines(r.Output, "DefaultLimitNOFILE", "DefaultLimitNPROC")
		lines = append(lines, "DefaultLimitNOFILE=1024000", "DefaultLimitNPROC=1024000")
		if w := exec.WriteLines(lines, "/etc/systemd/system.conf"); !w.Success {
			slog.Error("system.conf 写入失败", "err", w.ErrOutput)
			return false
		}
		slog.Info("system.conf 初始化完成")
	}

	// /etc/security/limits.conf
	r2 := exec.GetFileString("/etc/security/limits.conf")
	if r2.Success {
		slog.Info("写入 limits.conf")
		keys := []string{"soft    fsize", "hard    fsize", "soft    cpu", "hard    cpu",
			"soft    as", "hard    as", "soft    nofile", "hard    nofile",
			"soft    nproc", "hard    nproc"}
		lines := filterLines(r2.Output, keys...)
		lines = append(lines,
			"*            soft    fsize           unlimited",
			"*            hard    fsize           unlimited",
			"*            soft    cpu             unlimited",
			"*            hard    cpu             unlimited",
			"*            soft    as              unlimited",
			"*            hard    as              unlimited",
			"*            soft    nofile          1048576",
			"*            hard    nofile          1048576",
			"*            soft    nproc           unlimited",
			"*            hard    nproc           unlimited",
		)
		if w := exec.WriteLines(lines, "/etc/security/limits.conf"); !w.Success {
			slog.Error("limits.conf 写入失败", "err", w.ErrOutput)
			return false
		}
		slog.Info("limits.conf 初始化完成")
	}

	// CentOS 7 额外 nproc 配置
	if exec.GetOs() == osinfo.OsTypeCentos7 {
		exec.WriteLines([]string{
			"*          soft    nproc     unlimited",
			"root       soft    nproc     unlimited",
		}, "/etc/security/limits.conf")
	}

	// /etc/sysctl.conf
	r3 := exec.GetFileString("/etc/sysctl.conf")
	if r3.Success {
		slog.Info("写入 sysctl.conf")
		lines := filterLines(r3.Output, "kernel.pid_max")
		lines = append(lines, "kernel.pid_max=1000000")
		if w := exec.WriteLines(lines, "/etc/sysctl.conf"); !w.Success {
			slog.Error("sysctl.conf 写入失败", "err", w.ErrOutput)
			return false
		}
		slog.Info("sysctl.conf 初始化完成")
	}

	if load := exec.ExecShell("sysctl -p"); !load.Success {
		return false
	}

	// Ubuntu 额外的 rc.local 配置
	if exec.GetOs().IsUbuntu() {
		if !exec.Exists("/etc/rc.d/init.d/").Success {
			exec.ExecShell("mkdir -p /etc/rc.d/")
			exec.ExecShell("ln -s /etc/init.d/ /etc/rc.d/init.d")
		}
		if !exec.Exists("/etc/rc.local").Success {
			exec.ExecShell("touch /etc/rc.local")
			exec.ExecShell("echo '#!/bin/bash' > /etc/rc.local")
		}
		exec.ExecShell("chmod +x /etc/rc.local")

		rcStatus := exec.ExecShell("systemctl is-enabled rc-local.service")
		if strings.TrimSpace(rcStatus.Output) == "static" {
			exec.ExecShell("echo '\n\n[Install]' >> /lib/systemd/system/rc-local.service")
			exec.ExecShell("echo 'WantedBy=multi-user.target' >> /lib/systemd/system/rc-local.service")
		}
		rcStatus2 := exec.ExecShell("systemctl is-enabled rc-local.service")
		if strings.TrimSpace(rcStatus2.Output) == "disabled" {
			exec.ExecShell("systemctl enable rc-local.service")
		}
		exec.ExecShell("systemctl start rc-local.service")
		if status := exec.ExecShell("systemctl status rc-local.service"); !status.Success {
			return false
		}
	}

	slog.Info("操作系统配置完成")
	return true
}

// filterLines 过滤掉包含任意关键词的行（对应 Java removeIf(s -> s.contains(...))）。
func filterLines(content string, keywords ...string) []string {
	var result []string
	for _, line := range strings.Split(content, "\n") {
		skip := false
		for _, kw := range keywords {
			if strings.Contains(line, kw) {
				skip = true
				break
			}
		}
		if !skip {
			result = append(result, line)
		}
	}
	return result
}
