package initcmd

import (
	"log/slog"
	"os"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

type InitLibrary struct{ TaskBase }

func (t *InitLibrary) Name() string { return "初始化依赖库" }

func (t *InitLibrary) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitLibrary) doRun(exec executor.Executor) bool {
	osType := exec.GetOs()
	archType := exec.GetArch()

	if osType.IsCentos() {
		if archType == osinfo.ArchX86_64 {
			t.installPkg(exec, "libxslt-devel")
		}
		t.installPkg(exec, "psmisc")
		t.installPkg(exec, "perl-JSON")
		t.initJavaPolicy(exec)
		t.initTmpPid(exec)
		t.installPkg(exec, "xdg-utils")
		t.installPkg(exec, "gcc-c++")
		t.installPkg(exec, "openssl-devel")
		t.installPkg(exec, "libtool")
		t.initCleanBuff(exec)
		exec.ExecShell("source /etc/profile")
		exec.ExecShell("source /root/.bash_profile")
		t.installTelnet(exec, osType)
	} else if osType.IsUbuntu() {
		t.installPkg(exec, "psmisc")
		t.initJavaPolicy(exec)
		t.initTmpPid(exec)
		t.initCleanBuff(exec)
		exec.ExecShell("source /etc/profile")
		exec.ExecShell("source /root/.bash_profile")
		t.installPkg(exec, "libpam-cracklib")
		t.installPkg(exec, "policycoreutils")
		t.installTelnet(exec, osType)
	}
	return true
}

func (t *InitLibrary) installPkg(exec executor.Executor, pkg string) {
	slog.Info("安装包", "pkg", pkg)
	if !executor.CheckAndInstallPkg(exec, pkg) {
		slog.Warn("安装包失败（继续）", "pkg", pkg)
	}
}

func (t *InitLibrary) initJavaPolicy(exec executor.Executor) {
	slog.Info("初始化 Java 安全策略")
	exec.ExecShell("source /etc/profile")
	exec.ExecShell(`sed -i '/modify java policy start/,/modify java policy end/d' ${JAVA_HOME}/jre/lib/security/java.policy`)
	exec.ExecShell(`sed -i '/grant {/a\\//modify java policy end' ${JAVA_HOME}/jre/lib/security/java.policy`)
	exec.ExecShell(`sed -i '/modify java policy end/i\\//modify java policy start' ${JAVA_HOME}/jre/lib/security/java.policy`)
	exec.ExecShell(`sed -i '/modify java policy end/i\\permission javax.management.MBeanTrustPermission "register";' ${JAVA_HOME}/jre/lib/security/java.policy`)
}

func (t *InitLibrary) initTmpPid(exec executor.Executor) {
	slog.Info("初始化 tmp pid")
	if r := exec.ExecShell(`egrep '/tmp/hsperfdata' /usr/lib/tmpfiles.d/tmp.conf >&/dev/null`); !r.Success {
		exec.ExecShell(`echo "x /tmp/*.pid" >> /usr/lib/tmpfiles.d/tmp.conf`)
		exec.ExecShell(`echo "x /tmp/hsperfdata*/*" >> /usr/lib/tmpfiles.d/tmp.conf`)
		exec.ExecShell(`echo "X /tmp/hsperfdata*" >> /usr/lib/tmpfiles.d/tmp.conf`)
	}
}

func (t *InitLibrary) initCleanBuff(exec executor.Executor) {
	slog.Info("清理缓存")
	exec.ExecShell("sync")
	exec.ExecShell("sync")
	exec.ExecShell("sync")
	time.Sleep(10 * time.Millisecond)
	exec.ExecShell("echo 1 >/proc/sys/vm/drop_caches")
	exec.ExecShell("echo 2 >/proc/sys/vm/drop_caches")
	exec.ExecShell("echo 3 >/proc/sys/vm/drop_caches")
}

func (t *InitLibrary) installTelnet(exec executor.Executor, osType osinfo.OsType) {
	slog.Info("安装 telnet")
	var installCmd string
	if osType.IsUbuntu() {
		installCmd = "DEBIAN_FRONTEND=noninteractive apt install -y telnet"
	} else {
		installCmd = "yum install -y telnet"
	}
	if r := exec.ExecShell(installCmd); !r.Success {
		slog.Error("telnet 安装失败")
		os.Exit(1)
	}
}

func (t *InitLibrary) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "library",
		Short: "安装运行时依赖库",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}
