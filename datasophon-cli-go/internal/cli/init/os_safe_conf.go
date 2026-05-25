package initcmd

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitOsSafeConf 对应 Java InitOsSafeConf — 初始化基线安全配置（20+ 项）。
type InitOsSafeConf struct {
	TaskBase
	exec     executor.Executor
	dateTime string
}

func (t *InitOsSafeConf) Name() string { return "初始化基线安全配置" }

func (t *InitOsSafeConf) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitOsSafeConf) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "osSafeConf",
		Short: "初始化基线安全配置",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	return cmd
}

func (t *InitOsSafeConf) doRun(exec executor.Executor) bool {
	t.exec = exec
	t.dateTime = time.Now().Format("20060102150405")
	osType := exec.GetOs()
	slog.Info("基线安全配置开始")

	t.setLoginTimeout()
	t.setHistoryRecordCount()
	t.setKeyFilePermission()

	// 密码策略路径（按 OS 区分）
	passwdConfFullPath := "/etc/pam.d/system-auth"
	passwdConfLoginPath := "/etc/pam.d/system-auth"
	passwdComplexityKeyword := "password    requisite     pam_cracklib.so"
	if osType.IsUbuntu() {
		passwdConfFullPath = "/etc/pam.d/common-password"
		passwdConfLoginPath = "/etc/pam.d/login"
		passwdComplexityKeyword = "password       requisite                       pam_cracklib.so"
	}
	t.setPasswdComplexity(passwdConfFullPath, passwdComplexityKeyword)
	t.setPasswordLifespan()
	t.setPasswdLockStrategy(passwdConfLoginPath)
	t.setPasswdRepeatTimes(passwdConfFullPath)

	t.lockNoUseAccount()
	t.disableShellOfNoUseAccount()
	t.setHostsAccessControl()
	t.disableSourceRoute()
	t.disableIpv4RouteRedirects()
	t.setRsyslogRotateValue()
	t.setSshdBanner()
	t.setSshdProtocolVersion()
	t.setSshdSkipDnsCheck()

	slog.Info("基线安全配置完成")
	return true
}

// editConf 对应 Java editConf() — 备份、删除旧配置行、追加新配置行。
// failedSignal: "abort"=失败退出, "skip"=失败跳过
func (t *InitOsSafeConf) editConf(keyword, confStr, confFullPath, failedSignal string) {
	slog.Info("设置配置项", "key", confStr, "file", confFullPath)
	if !t.exec.Exists(confFullPath).Success {
		slog.Error("配置文件不存在", "file", confFullPath)
		if failedSignal == "abort" {
			os.Exit(1)
		}
		return
	}
	// 备份
	t.backupConf(confFullPath)
	// 删除已有匹配行（循环直到无匹配）
	insertRow := 0
	for {
		r := t.exec.ExecShell(fmt.Sprintf(
			"grep -E -n \"^%s\" %s | awk -F ':' '{print $1}' | xargs echo | awk '{print $1}'",
			keyword, confFullPath))
		row := r.Output
		if r.Success && row != "" {
			t.exec.ExecShell(fmt.Sprintf("sed -i \"%sd\" %s &>/dev/null", row, confFullPath))
			// insertRow = 上一次删除行号 - 1（Java 行为对齐）
			if row != "" {
				fmt.Sscanf(row, "%d", &insertRow)
				insertRow--
			}
		} else {
			break
		}
	}
	// 追加新配置行
	var ok bool
	if insertRow <= 0 {
		// 找注释行位置
		r := t.exec.ExecShell(fmt.Sprintf(
			"grep -E -n \"^#%s\" %s | awk -F ':' '{print $1}' | xargs echo | awk '{print $NF}'",
			keyword, confFullPath))
		row := r.Output
		if r.Success && row == "" {
			ok = t.exec.ExecShell(fmt.Sprintf("sed -i \"$ a %s \" %s &>/dev/null", confStr, confFullPath)).Success
		} else {
			ok = t.exec.ExecShell(fmt.Sprintf("sed -i \"%s a %s \" %s &>/dev/null", row, confStr, confFullPath)).Success
		}
	} else {
		ok = t.exec.ExecShell(fmt.Sprintf("sed -i \"%d a %s \" %s &>/dev/null", insertRow, confStr, confFullPath)).Success
	}

	if ok {
		slog.Info("配置成功", "key", confStr)
	} else {
		slog.Error("配置失败", "key", confStr)
		if failedSignal == "abort" {
			os.Exit(1)
		}
	}
}

func (t *InitOsSafeConf) backupConf(confFullPath string) {
	confFileName := filepath.Base(confFullPath)
	dir := filepath.Dir(confFullPath)
	backupDir := filepath.Join(dir, "."+confFileName+".bak")
	backupFile := filepath.Join(backupDir, confFileName+".bak-"+t.dateTime)
	t.exec.ExecShell(fmt.Sprintf("mkdir -p %s", backupDir))
	t.exec.ExecShell(fmt.Sprintf("cp %s %s &>/dev/null", confFullPath, backupFile))
}

// setLoginTimeout 设置闲置超时自动退出（TMOUT=300）。
func (t *InitOsSafeConf) setLoginTimeout() {
	slog.Info("设置闲置超时自动退出")
	t.editConf("export TMOUT=", "export TMOUT=300", "/etc/profile", "abort")
}

// setHistoryRecordCount 设置历史操作记录保留数量。
func (t *InitOsSafeConf) setHistoryRecordCount() {
	slog.Info("设置历史操作记录保留数量")
	t.editConf("HISTSIZE=", "HISTSIZE=3", "/etc/profile", "abort")
}

// setKeyFilePermission 收敛关键文件权限。
func (t *InitOsSafeConf) setKeyFilePermission() {
	slog.Info("收敛关键文件权限")
	commands := []string{
		"chmod 644 /etc/passwd",
		"chmod 400 /etc/shadow",
		"chmod 644 /etc/group",
		"chmod 644 /etc/services",
		"chmod 600 /etc/xinetd.conf",
		"chmod 600 /etc/security",
	}
	for _, cmd := range commands {
		parts := splitCmd(cmd)
		if len(parts) < 3 {
			continue
		}
		filePath := parts[2]
		if !t.exec.Exists(filePath).Success {
			slog.Error("文件不存在，跳过", "file", filePath)
			continue
		}
		if r := t.exec.ExecShell(cmd + " &>/dev/null"); r.Success {
			slog.Info("权限设置成功", "cmd", cmd)
		} else {
			slog.Error("权限设置失败，跳过", "cmd", cmd)
		}
	}
}

// setPasswdComplexity 设置账号密码复杂度要求。
func (t *InitOsSafeConf) setPasswdComplexity(confFullPath, keyword string) {
	slog.Info("设置账号密码复杂度要求")
	t.editConf(keyword,
		"password    requisite     pam_cracklib.so retry=3 minlen=8 dcredit=-1 ucredit=-1 lcredit=-1 ocredit=-1",
		confFullPath, "abort")
}

// setPasswordLifespan 设置账号密码有效期限。
func (t *InitOsSafeConf) setPasswordLifespan() {
	slog.Info("设置账号密码有效期限")
	t.editConf("PASS_MAX_DAYS", "PASS_MAX_DAYS  90", "/etc/login.defs", "abort")
	t.editConf("PASS_MIN_DAYS", "PASS_MIN_DAYS  10", "/etc/login.defs", "abort")
	t.editConf("PASS_WARN_AGE", "PASS_WARN_AGE  7", "/etc/login.defs", "abort")
}

// setPasswdLockStrategy 设置账号密码错误锁定策略。
func (t *InitOsSafeConf) setPasswdLockStrategy(confFullPath string) {
	slog.Info("设置账号密码错误锁定策略")
	t.editConf("auth        required      pam_tally2.so",
		"auth        required      pam_tally2.so deny=5 onerr=fail no_magic_root unlock_time=180",
		confFullPath, "abort")
	t.editConf("account        required      pam_tally2.so",
		"account        required      pam_tally2.so",
		confFullPath, "abort")
}

// setPasswdRepeatTimes 设置口令复用次数限制。
func (t *InitOsSafeConf) setPasswdRepeatTimes(confFullPath string) {
	slog.Info("设置口令复用次数限制")
	t.editConf(
		"password    sufficient    pam_unix.so sha512 shadow nullok try_first_pass use_authtok",
		"password    sufficient    pam_unix.so sha512 shadow nullok try_first_pass use_authtok remember=5",
		confFullPath, "abort")
}

// lockNoUseAccount 锁定不常用账号（修改 /etc/shadow 密码列为 *）。
func (t *InitOsSafeConf) lockNoUseAccount() {
	slog.Info("锁定不常用账号")
	t.userControl()
	users := []string{"lp", "sync", "halt", "news", "uucp", "operator", "games",
		"gopher", "smmsp", "nfsnobody", "nobody", "adm", "shutdown"}
	for _, user := range users {
		r := t.exec.ExecShell(fmt.Sprintf("sed -i 's/^%s:!!/%s:*/g' /etc/shadow", user, user))
		if r.Success {
			slog.Info("锁定账号成功", "user", user)
		} else {
			slog.Error("锁定账号失败，跳过", "user", user)
		}
	}
}

// disableShellOfNoUseAccount 禁用不常用账号的 Shell。
func (t *InitOsSafeConf) disableShellOfNoUseAccount() {
	slog.Info("禁用不常用账号的 Shell")
	users := []string{"lp", "sync", "halt", "news", "uucp", "operator", "games",
		"gopher", "smmsp", "nfsnobody", "nobody", "adm", "shutdown"}
	for _, user := range users {
		r := t.exec.ExecShell(fmt.Sprintf("grep -E %s /etc/passwd >& /dev/null", user))
		if !r.Success {
			slog.Info("用户不存在，跳过", "user", user)
			continue
		}
		if r2 := t.exec.ExecShell(fmt.Sprintf("usermod -s /bin/false %s &>/dev/null", user)); r2.Success {
			slog.Info("禁用 Shell 成功", "user", user)
		} else {
			slog.Error("禁用 Shell 失败，跳过", "user", user)
		}
	}
}

// setHostsAccessControl 设置 hosts.allow 和 hosts.deny 策略。
func (t *InitOsSafeConf) setHostsAccessControl() {
	slog.Info("设置 hosts.allow/hosts.deny 策略")
	if !t.exec.Exists("/etc/hosts.allow").Success {
		t.exec.WriteLines(nil, "/etc/hosts.allow")
	}
	t.editConf("telnet:all:allow", "telnet:all:allow", "/etc/hosts.allow", "skip")

	if !t.exec.Exists("/etc/hosts.deny").Success {
		t.exec.WriteLines(nil, "/etc/hosts.deny")
	}
	t.editConf("telnet:all", "telnet:all", "/etc/hosts.deny", "skip")
}

// disableSourceRoute 禁止 IP 源路由。
func (t *InitOsSafeConf) disableSourceRoute() {
	slog.Info("禁止 IP 源路由")
	t.editConf("net.ipv4.conf.all.accept_source_route",
		"net.ipv4.conf.all.accept_source_route=0", "/etc/sysctl.conf", "abort")
	t.exec.ExecShell("sysctl -p > /dev/null 2>&1")
}

// disableIpv4RouteRedirects 禁止 IP 路由转发。
func (t *InitOsSafeConf) disableIpv4RouteRedirects() {
	slog.Info("禁止 IP 路由转发")
	t.editConf("net.ipv4.conf.all.accept_redirects",
		"net.ipv4.conf.all.accept_redirects=0", "/etc/sysctl.conf", "abort")
	t.exec.ExecShell("sysctl -p > /dev/null 2>&1")
}

// setRsyslogRotateValue 设置审计日志留存 50 周。
func (t *InitOsSafeConf) setRsyslogRotateValue() {
	slog.Info("设置审计日志留存 50 周")
	t.editConf("rotate ", "rotate 50", "/etc/logrotate.conf", "abort")
	if r := t.exec.ExecShell("systemctl restart rsyslog > /dev/null 2>&1"); !r.Success {
		slog.Error("rsyslog 重启失败")
		os.Exit(1)
	}
}

// setSshdBanner 设置 SSH Banner。
func (t *InitOsSafeConf) setSshdBanner() {
	slog.Info("设置 sshd banner")
	t.userControl()
	t.writeContent2File("/etc/motd",
		"Warning!!! If you are not the operations staff, logout the system right now!")
	t.writeContent2File("/etc/ssh/mybanner",
		"Authorized users only!!! All activity may be monitored and reported!")
	t.editConf("Banner ", "Banner /etc/ssh/mybanner", "/etc/ssh/sshd_config", "abort")
	t.restartSshd()
}

// setSshdProtocolVersion 设置 SSH 协议版本为 2。
func (t *InitOsSafeConf) setSshdProtocolVersion() {
	slog.Info("设置 SSH 协议版本为 2")
	t.editConf("Protocol ", "Protocol 2", "/etc/ssh/sshd_config", "abort")
	t.restartSshd()
}

// setSshdSkipDnsCheck 设置 SSH 不进行 DNS 检查。
func (t *InitOsSafeConf) setSshdSkipDnsCheck() {
	slog.Info("设置 SSH 不进行 DNS 检查")
	t.editConf("UseDNS ", "UseDNS no", "/etc/ssh/sshd_config", "abort")
	t.restartSshd()
}

func (t *InitOsSafeConf) writeContent2File(confFullPath, content string) {
	if r := t.exec.Exists(confFullPath); r.Success {
		fileContent := t.exec.ExecShell(fmt.Sprintf("cat %s", confFullPath)).Output
		if fileContent != "" {
			t.backupConf(confFullPath)
		} else {
			t.exec.ExecShell(fmt.Sprintf("touch %s", confFullPath))
		}
	}
	t.exec.ExecShell(fmt.Sprintf("echo '%s' > %s", content, confFullPath))
}

func (t *InitOsSafeConf) restartSshd() {
	if r := t.exec.ExecShell("systemctl restart sshd > /dev/null 2>&1"); !r.Success {
		slog.Error("sshd 重启失败")
		os.Exit(1)
	}
	slog.Info("sshd 重启成功")
}

func (t *InitOsSafeConf) userControl() {
	r := t.exec.ExecShell("whoami")
	if r.Output != "root" {
		slog.Error("需要 root 权限执行")
		os.Exit(1)
	}
}

// splitCmd 将命令字符串按空格拆分（简单实现）。
func splitCmd(cmd string) []string {
	var parts []string
	var cur string
	for _, c := range cmd {
		if c == ' ' {
			if cur != "" {
				parts = append(parts, cur)
				cur = ""
			}
		} else {
			cur += string(c)
		}
	}
	if cur != "" {
		parts = append(parts, cur)
	}
	return parts
}
