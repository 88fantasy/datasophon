package plan

import (
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/shellutil"
	"golang.org/x/crypto/ssh"
)

// mysqldExporterTask 在 MySQL 节点创建最小权限监控账号并安装 mysqld_exporter。
type mysqldExporterTask struct {
	Enable          bool
	PackagePath     string
	InstallPath     string
	X86Tar          string
	Aarch64Tar      string
	NodeIP          string
	Port            string
	MySQLPort       int
	RootPassword    string
	MonitorUser     string
	MonitorPassword string
}

var safeExporterReleaseName = regexp.MustCompile(`^[A-Za-z0-9._-]+$`)

func (t *mysqldExporterTask) Name() string { return "安装 mysqld_exporter" }

func (t *mysqldExporterTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *mysqldExporterTask) doRun(exec executor.Executor) error {
	if !t.Enable {
		slog.Info("mysqld_exporter enable=false，跳过")
		return nil
	}
	if strings.TrimSpace(t.MonitorUser) == "" || t.MonitorPassword == "" {
		return errors.New("mysqld_exporter 监控用户名或密码为空")
	}
	if containsLineBreak(t.MonitorUser) || containsLineBreak(t.MonitorPassword) || containsLineBreak(t.RootPassword) {
		return errors.New("MySQL 用户名或密码不能包含换行符")
	}
	if strings.TrimSpace(t.NodeIP) == "" || strings.TrimSpace(t.Port) == "" {
		return errors.New("mysqld_exporter 监听地址或端口为空")
	}
	if executor.InspectPath(exec, t.InstallPath) == executor.PathMissing {
		return fmt.Errorf("mysqld_exporter 安装目录不存在: %s", t.InstallPath)
	}
	if err := t.ensureMonitorAccount(exec); err != nil {
		return err
	}

	home := fmt.Sprintf("%s/mysqld_exporter", strings.TrimSuffix(t.InstallPath, "/"))
	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	if filepath.Base(tarName) != tarName || !strings.HasSuffix(tarName, ".tar.gz") {
		return fmt.Errorf("mysqld_exporter 制品名不合法: %q", tarName)
	}
	releaseName := strings.TrimSuffix(tarName, ".tar.gz")
	if !safeExporterReleaseName.MatchString(releaseName) {
		return fmt.Errorf("mysqld_exporter release 名不合法: %q", releaseName)
	}
	releaseDir := filepath.Join(home, "releases", releaseName)
	releaseBinary := filepath.Join(releaseDir, "mysqld_exporter")
	binaryLink := filepath.Join(home, "mysqld_exporter")
	if result := exec.ExecShell("mkdir -p " + shellutil.Quote(filepath.Join(home, "releases"))); !result.Success {
		return fmt.Errorf("创建 mysqld_exporter releases 目录失败: %s", result.ErrOutput)
	}
	// Migrate the pre-release-layout regular binary without deleting it.
	legacyDir := filepath.Join(home, "releases", "legacy-pre-versioned")
	migrate := fmt.Sprintf("if [ -e %s ] && [ ! -L %s ]; then mkdir -p %s && mv %s %s; fi",
		shellutil.Quote(binaryLink), shellutil.Quote(binaryLink), shellutil.Quote(legacyDir),
		shellutil.Quote(binaryLink), shellutil.Quote(filepath.Join(legacyDir, "mysqld_exporter")))
	if result := exec.ExecShell(migrate); !result.Success {
		return fmt.Errorf("迁移旧 mysqld_exporter 二进制失败: %s", result.ErrOutput)
	}
	if executor.InspectPath(exec, releaseDir) != executor.PathExists {
		tarPath := fmt.Sprintf("%s/%s", strings.TrimSuffix(t.PackagePath, "/"), tarName)
		if err := ensurePackageOnTarget(exec, tarPath, "mysqld_exporter"); err != nil {
			return err
		}
		if result := exec.ExecShell("mkdir -p " + shellutil.Quote(releaseDir)); !result.Success {
			return fmt.Errorf("创建 mysqld_exporter release 目录失败: %s", result.ErrOutput)
		}
		if result := exec.ExecShell(fmt.Sprintf("tar -xzf %s -C %s --strip-components=1",
			shellutil.Quote(tarPath), shellutil.Quote(releaseDir))); !result.Success {
			return fmt.Errorf("解压 mysqld_exporter 安装包失败: %s", result.ErrOutput)
		}
		if result := exec.ExecShell("chmod +x " + shellutil.Quote(releaseBinary)); !result.Success {
			return fmt.Errorf("设置 mysqld_exporter 执行权限失败: %s", result.ErrOutput)
		}
	}
	oldTarget := ""
	if !executor.IsDryRun(exec) {
		if result := exec.ExecShell("readlink " + shellutil.Quote(binaryLink)); result.Success {
			oldTarget = strings.TrimSpace(result.Output)
		}
	}
	binaryChanged := oldTarget != releaseBinary
	if binaryChanged {
		if result := switchVersionSymlink(exec, binaryLink, releaseBinary); !result.Success {
			return fmt.Errorf("切换 mysqld_exporter 版本失败: %s", result.ErrOutput)
		}
	}

	myCnfPath := home + "/.my.cnf"
	userOption, err := mysqlOptionFileValue(t.MonitorUser)
	if err != nil {
		return err
	}
	passwordOption, err := mysqlOptionFileValue(t.MonitorPassword)
	if err != nil {
		return err
	}
	myCnf := strings.Join([]string{
		"[client]",
		"user=" + userOption,
		"password=" + passwordOption,
		"host=127.0.0.1",
		fmt.Sprintf("port=%d", t.MySQLPort),
	}, "\n") + "\n"
	configResult := executor.WriteFileAtomic(exec, []byte(myCnf), myCnfPath, 0o600)
	if !configResult.Success {
		return fmt.Errorf("写入 mysqld_exporter MySQL 配置失败: %s", configResult.ErrOutput)
	}
	startScript := strings.Join(t.startScriptLines(home), "\n") + "\n"
	scriptResult := executor.WriteFileAtomic(exec, []byte(startScript), home+"/start.sh", 0o755)
	if !scriptResult.Success {
		return fmt.Errorf("写入 mysqld_exporter 启动脚本失败: %s", scriptResult.ErrOutput)
	}
	running := !executor.IsDryRun(exec) && t.checkStart(exec, home)
	changed := binaryChanged || configResult.Output == "changed" || scriptResult.Output == "changed"
	if running && !changed {
		slog.Info("mysqld_exporter 配置与版本未变化，保持运行", "path", home)
		return nil
	}
	if running && !t.stop(exec, home) {
		return errors.New("mysqld_exporter 为应用最新配置重启时停止失败")
	}
	if result := exec.ExecShell("bash " + shellutil.Quote(home+"/start.sh")); !result.Success {
		t.rollbackBinary(exec, binaryLink, oldTarget, binaryChanged, home)
		return fmt.Errorf("启动 mysqld_exporter 失败: %s", result.ErrOutput)
	}
	exec.ExecShell("sleep 3")
	if !executor.IsDryRun(exec) && !t.checkStart(exec, home) {
		t.rollbackBinary(exec, binaryLink, oldTarget, binaryChanged, home)
		return errors.New("mysqld_exporter 启动失败")
	}
	slog.Info("mysqld_exporter 安装成功", "path", home, "listen", t.NodeIP+":"+t.Port)
	return nil
}

func (t *mysqldExporterTask) rollbackBinary(exec executor.Executor, binaryLink, oldTarget string, changed bool, home string) {
	if !changed || oldTarget == "" || executor.IsDryRun(exec) {
		return
	}
	if result := switchVersionSymlink(exec, binaryLink, oldTarget); !result.Success {
		slog.Error("回滚 mysqld_exporter 版本失败", "error", result.ErrOutput)
		return
	}
	exec.ExecShell("bash " + shellutil.Quote(home+"/start.sh"))
}

func (t *mysqldExporterTask) ensureMonitorAccount(exec executor.Executor) error {
	if executor.IsDryRun(exec) {
		slog.Info("[dry-run] 跳过 MySQL exporter 账号创建")
		return nil
	}
	tempResult := exec.ExecShell("mktemp -d /tmp/datasophon-mysqld-exporter.XXXXXX")
	if !tempResult.Success {
		return fmt.Errorf("创建 MySQL exporter 临时目录失败: %s", tempResult.ErrOutput)
	}
	tempDir := strings.TrimSpace(tempResult.Output)
	if containsLineBreak(tempDir) || !strings.HasPrefix(tempDir, "/tmp/datasophon-mysqld-exporter.") {
		return fmt.Errorf("MySQL exporter 临时目录不合法: %q", tempDir)
	}
	cleanup := func() { exec.ExecShell("rm -rf -- " + shellutil.Quote(tempDir)) }
	clientConfigPath := tempDir + "/client.cnf"
	configPassword, err := mysqlOptionFileValue(t.RootPassword)
	if err != nil {
		cleanup()
		return err
	}
	clientConfig := "[client]\npassword=" + configPassword + "\n"
	if result := executor.WriteFileAtomic(exec, []byte(clientConfig), clientConfigPath, 0o600); !result.Success {
		cleanup()
		return fmt.Errorf("写入 MySQL exporter 临时认证文件失败: %s", result.ErrOutput)
	}

	account := mysqlStringLiteral(t.MonitorUser)
	password := mysqlStringLiteral(t.MonitorPassword)
	sqlPath := tempDir + "/account.sql"
	// grantee host 用 127.0.0.1 而非 localhost：exporter 走 TCP 连 127.0.0.1（见下方
	// .my.cnf 的 host=127.0.0.1），MySQL 里 @'localhost' 只匹配 unix socket 连接，
	// 两者不一致会导致 Access denied，账号永远连不上。
	accountSQL := strings.Join([]string{
		fmt.Sprintf("CREATE USER IF NOT EXISTS '%s'@'127.0.0.1' IDENTIFIED BY '%s' WITH MAX_USER_CONNECTIONS 3;", account, password),
		fmt.Sprintf("ALTER USER '%s'@'127.0.0.1' IDENTIFIED BY '%s' WITH MAX_USER_CONNECTIONS 3;", account, password),
		fmt.Sprintf("GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO '%s'@'127.0.0.1';", account),
		"FLUSH PRIVILEGES;",
	}, "\n") + "\n"
	if result := executor.WriteFileAtomic(exec, []byte(accountSQL), sqlPath, 0o600); !result.Success {
		cleanup()
		return fmt.Errorf("写入 MySQL exporter 初始化 SQL 失败: %s", result.ErrOutput)
	}
	result := exec.ExecShell(fmt.Sprintf("mysql --defaults-extra-file=%s -uroot -P%d < %s",
		shellutil.Quote(clientConfigPath), t.MySQLPort, shellutil.Quote(sqlPath)))
	cleanup()
	if !result.Success {
		return fmt.Errorf("创建 MySQL exporter 监控账号失败: %s", result.ErrOutput)
	}
	return nil
}

func (t *mysqldExporterTask) startScriptLines(home string) []string {
	listenAddress := t.NodeIP + ":" + t.Port
	return []string{
		"#!/usr/bin/env bash",
		fmt.Sprintf("nohup %s --config.my-cnf=%s --web.listen-address=%s > %s 2>&1 &",
			shellutil.Quote(home+"/mysqld_exporter"),
			shellutil.Quote(home+"/.my.cnf"),
			shellutil.Quote(listenAddress),
			shellutil.Quote(home+"/mysqld_exporter.log")),
	}
}

func (t *mysqldExporterTask) findPIDCommand(home string) string {
	return fmt.Sprintf("ps -eo pid=,args= | awk -v bin=%s '$2 == bin {print $1; exit}'",
		shellutil.Quote(home+"/mysqld_exporter"))
}

func (t *mysqldExporterTask) checkStart(exec executor.Executor, home string) bool {
	return exec.ExecShell(fmt.Sprintf("pid=$(%s); [ -n \"$pid\" ]", t.findPIDCommand(home))).Success
}

func (t *mysqldExporterTask) stop(exec executor.Executor, home string) bool {
	command := fmt.Sprintf("pid=$(%s); [ -z \"$pid\" ] || { kill \"$pid\" && "+
		"i=0; while kill -0 \"$pid\" 2>/dev/null && [ $i -lt 10 ]; do sleep 1; i=$((i+1)); done; "+
		"kill -0 \"$pid\" 2>/dev/null && kill -9 \"$pid\" || true; }", t.findPIDCommand(home))
	return exec.ExecShell(command).Success
}

func mysqlStringLiteral(value string) string {
	return strings.NewReplacer(`\`, `\\`, `'`, `''`).Replace(value)
}

func mysqlOptionFileValue(value string) (string, error) {
	if containsLineBreak(value) {
		return "", errors.New("MySQL option file 值不能包含换行符")
	}
	escaped := strings.NewReplacer(`\`, `\\`, `"`, `\"`).Replace(value)
	return `"` + escaped + `"`, nil
}

func containsLineBreak(value string) bool {
	return strings.ContainsAny(value, "\r\n")
}
