package plan

import (
	"errors"
	"fmt"
	"log/slog"
	"strings"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"golang.org/x/crypto/ssh"
)

// mysqlTask 是 plan 包用于 MySQL 安装步骤的 handler。
type mysqlTask struct {
	Password         string
	Force            bool
	PackagePath      string
	InstallPath      string
	X86Tar           string
	Aarch64Tar       string
	Port             int
	EnableRegistry   bool
	RegistryIP       string
	RegistryPort     string
	RegistryUsername string
	RegistryPassword string
}

func (t *mysqlTask) Name() string { return "安装mysql" }

func (t *mysqlTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *mysqlTask) doRun(exec executor.Executor) error {
	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	osType := exec.GetOs()
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	httpRootPath := fmt.Sprintf("%s/tmp/mysql", t.InstallPath)

	if err := initcmd.DownloadFromRegistry(exec, t.EnableRegistry,
		t.RegistryIP, t.RegistryPort, t.RegistryUsername, t.RegistryPassword,
		tarName, tarPath, true); err != nil {
		return err
	}

	mysqlService := "mysqld"
	if osType.IsUbuntu() {
		mysqlService = "mysql"
	}

	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); r.Success {
		slog.Info("MySQL 已存在")
		if !t.Force {
			return nil
		}
	}

	if !exec.Exists(tarPath).Success {
		slog.Error("安装包不存在", "path", tarPath)
		return fmt.Errorf("安装包不存在: %s", tarPath)
	}

	if exec.Exists(httpRootPath).Success {
		exec.ExecShell(fmt.Sprintf("rm -rf %s/tmp/mysql", t.InstallPath))
	}
	exec.ExecShell(fmt.Sprintf("mkdir -p %s", httpRootPath))
	exec.ExecShell(fmt.Sprintf("tar -xvf %s -C %s", tarPath, httpRootPath))

	if osType.IsUbuntu() {
		return t.installUbuntu(exec, httpRootPath, mysqlService)
	}
	return t.installCentos(exec, osType, httpRootPath, mysqlService)
}

func (t *mysqlTask) installUbuntu(exec executor.Executor, httpRootPath, mysqlService string) error {
	if r := exec.ExecShell("dpkg --list|grep -E 'mysql-community-server|mariadb'"); r.Success {
		slog.Info("卸载已存在的 MySQL")
		exec.ExecShell("systemctl stop mysql")
		exec.ExecShell("apt remove mysql-common -y")
		exec.ExecShell("apt remove mariadb -y")
		exec.ExecShell("apt autoremove --purge mysql-server-8.0 -y")
		exec.ExecShell("dpkg -P systemd-timesyncd")
		exec.ExecShell("rm -rf /var/lib/mysql")
		exec.ExecShell("rm -rf /etc/mysql")
		exec.ExecShell("rm -rf /var/log/mysql")
	}
	exec.ExecShell(fmt.Sprintf("apt localinstall %s/*.rpm -y", httpRootPath))
	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); r.Success {
		if err := t.rootUserConf(exec); err != nil {
			return err
		}
		exec.ExecShell("mv /etc/mysql/mysql.conf.d/mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf.bak")
		exec.WriteLines(t.mysqldConf(), "/etc/mysql/mysql.conf.d/mysqld.cnf")
		exec.ExecShell(fmt.Sprintf("systemctl restart %s", mysqlService))
		exec.ExecShell(fmt.Sprintf("systemctl enable %s", mysqlService))
		slog.Info("MySQL 安装成功")
		return t.checkStart(exec, mysqlService)
	}
	slog.Error("MySQL 安装失败")
	return errors.New("MySQL 安装失败")
}

func (t *mysqlTask) installCentos(exec executor.Executor, osType osinfo.OsType, httpRootPath, mysqlService string) error {
	if r := exec.ExecShell("rpm -qa | grep mariadb"); r.Success {
		exec.ExecShell("rpm -qa | grep mariadb | xargs rpm -e --nodeps")
	}
	if r := exec.ExecShell("rpm -qa | grep mysql"); r.Success {
		exec.ExecShell("systemctl stop mysqld")
		exec.ExecShell("rpm -qa | grep mysql | xargs rpm -e")
		exec.ExecShell("rm -rf /var/lib/mysql /usr/sbin/mysqld /usr/local/mysql /etc/my.cnf /var/log/mysqld.log /var/log/mysql.log")
	}
	t.mysqlLib(exec, "zlib-devel", "rpm -qa | grep zlib-devel", "yum -y install zlib-devel")
	t.mysqlLib(exec, "bzip2-devel", "rpm -qa | grep bzip2-devel", "yum -y install bzip2-devel")
	t.mysqlLib(exec, "openssl-devel", "rpm -qa | grep openssl-devel", "yum -y install openssl-devel")
	t.mysqlLib(exec, "ncurses-devel", "rpm -qa | grep ncurses-devel", "yum -y install ncurses-devel")
	if osType == osinfo.OsTypeCentos7 {
		t.mysqlLib(exec, "libaio", "rpm -qa | grep libaio", "yum -y install libaio")
	}
	exec.ExecShell(fmt.Sprintf("yum -y localinstall %s/*.rpm", httpRootPath))
	exec.ExecShell("mysqld --initialize --user=mysql")
	exec.ExecShell(fmt.Sprintf("systemctl start %s", mysqlService))
	exec.ExecShell(fmt.Sprintf("systemctl enable %s", mysqlService))
	exec.ExecShell("sleep 2")

	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); r.Success {
		tmpPasswd := strings.TrimSpace(exec.ExecShell("grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}'").Output)
		slog.Info("临时密码已获取，开始修改密码")
		oldCnf := "/tmp/.dsph_mysql_old.cnf"
		// cnf 文件里密码必须加引号：MySQL 客户端的配置文件解析器把不加引号的 # 当注释起始符，
		// 密码含 # 时会被截断，导致后续用这个 cnf 连接时密码错误（Access denied）。
		exec.WriteLines([]string{"[client]", "password=\"" + tmpPasswd + "\""}, oldCnf)
		exec.ExecShell(fmt.Sprintf("chmod 600 %s", oldCnf))
		newSqlPath := "/tmp/.dsph_mysql_init.sql"
		exec.WriteLines([]string{
			fmt.Sprintf("ALTER USER 'root'@'localhost' IDENTIFIED BY '%s';", t.Password),
		}, newSqlPath)
		exec.ExecShell(fmt.Sprintf("chmod 600 %s", newSqlPath))
		// --connect-expired-password：临时密码首次登录时 MySQL 强制要求先修改密码才允许执行
		// 任何 SQL，缺少这个选项会导致这条 ALTER USER 被拒绝执行、root 密码从未真正改变，
		// 但原先不检查返回值，后续步骤仍会照常执行并报告"安装成功"。
		// 注意 --defaults-extra-file 必须是第一个参数，放在其他选项之后会被 MySQL 客户端
		// 误判成普通变量赋值而报 "unknown variable" 错误。
		ir := exec.ExecShell(fmt.Sprintf("mysql --defaults-extra-file=%s --connect-expired-password -uroot < %s", oldCnf, newSqlPath))
		exec.ExecShell(fmt.Sprintf("rm -f %s %s", oldCnf, newSqlPath))
		if !ir.Success {
			return fmt.Errorf("修改 root 初始密码失败: %s", ir.ErrOutput)
		}
		if err := t.rootUserConf(exec); err != nil {
			return err
		}
		exec.ExecShell("mv /etc/my.cnf /etc/my.cnf.bak")
		exec.WriteLines(t.mysqldConf(), "/etc/my.cnf")
		exec.ExecShell(fmt.Sprintf("systemctl restart %s", mysqlService))
		exec.ExecShell(fmt.Sprintf("systemctl enable %s", mysqlService))
		slog.Info("MySQL 安装成功")
		return t.checkStart(exec, mysqlService)
	}
	slog.Error("MySQL 安装失败")
	return errors.New("MySQL 安装失败")
}

func (t *mysqlTask) mysqlLib(exec executor.Executor, name, checkCmd, installCmd string) {
	if r := exec.ExecShell(checkCmd); r.Success {
		slog.Info("依赖已存在", "name", name)
		return
	}
	exec.ExecShell(installCmd)
	if r := exec.ExecShell(checkCmd); r.Success {
		slog.Info("依赖安装成功", "name", name)
	}
}

func (t *mysqlTask) rootUserConf(exec executor.Executor) error {
	newCnf := "/tmp/.dsph_mysql_new.cnf"
	exec.WriteLines([]string{"[client]", "password=\"" + t.Password + "\""}, newCnf)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", newCnf))
	sqlPath := "/tmp/.dsph_mysql_conf.sql"
	exec.WriteLines([]string{
		"UPDATE mysql.user SET host='%' WHERE user='root';",
		"FLUSH PRIVILEGES;",
		fmt.Sprintf("ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;", t.Password),
		fmt.Sprintf("ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';", t.Password),
		"FLUSH PRIVILEGES;",
	}, sqlPath)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", sqlPath))
	r := exec.ExecShell(fmt.Sprintf("mysql --defaults-extra-file=%s -uroot -P%d < %s", newCnf, t.Port, sqlPath))
	exec.ExecShell(fmt.Sprintf("rm -f %s %s", newCnf, sqlPath))
	if !r.Success {
		return fmt.Errorf("配置 root 用户权限失败: %s", r.ErrOutput)
	}
	return nil
}

func (t *mysqlTask) mysqldConf() []string {
	return []string{
		"[mysqld]",
		"character_set_server=utf8mb4",
		"collation_server=utf8mb4_bin",
		"default-storage-engine=INNODB",
		"explicit_defaults_for_timestamp=true",
		"max_connections=3600",
		fmt.Sprintf("port=%d", t.Port),
		"sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION",
	}
}

func (t *mysqlTask) checkStart(exec executor.Executor, mysqlService string) error {
	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); !r.Success {
		return errors.New("mysql 启动状态失败，请检查")
	}
	return nil
}
