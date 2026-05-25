package initcmd

import (
	"errors"
	"fmt"
	"log/slog"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitMysql 对应 Java InitMysql — 安装 MySQL 8（本地 rpm/deb 包）。
type InitMysql struct {
	TaskBase
	Password    string
	Force       bool
	PackagePath string
	InstallPath string
	X86Tar      string
	Aarch64Tar  string
	Port        int
}

func (t *InitMysql) Name() string { return "安装mysql" }

func (t *InitMysql) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitMysql) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "mysql",
		Short: "安装 MySQL 8（本地 rpm/deb 包）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.Password, "password", "p", "", "MySQL root 密码（必填）")
	cmd.Flags().BoolVarP(&t.Force, "force", "f", false, "MySQL 已存在时是否覆盖")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	cmd.Flags().StringVarP(&t.X86Tar, "x86Tar", "x", "", "x86_64 包（必填）")
	cmd.Flags().StringVarP(&t.Aarch64Tar, "aarch64Tar", "a", "", "aarch64 包（必填）")
	cmd.Flags().IntVar(&t.Port, "mysqlPort", 3306, "端口（必填）")
	_ = cmd.MarkFlagRequired("password")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("x86Tar")
	_ = cmd.MarkFlagRequired("aarch64Tar")
	return cmd
}

func (t *InitMysql) doRun(exec executor.Executor) error {
	tarName := t.X86Tar
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarName = t.Aarch64Tar
	}
	osType := exec.GetOs()
	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
	httpRootPath := fmt.Sprintf("%s/tmp/mysql", t.InstallPath)

	if err := DownloadFromRegistry(exec, t.EnableRegistry,
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

func (t *InitMysql) installUbuntu(exec executor.Executor, httpRootPath, mysqlService string) error {
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
		t.rootUserConf(exec)
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

func (t *InitMysql) installCentos(exec executor.Executor, osType osinfo.OsType, httpRootPath, mysqlService string) error {
	// 卸载 mariadb
	if r := exec.ExecShell("rpm -qa | grep mariadb"); r.Success {
		exec.ExecShell("rpm -qa | grep mariadb | xargs rpm -e --nodeps")
	}
	// 卸载 mysql
	if r := exec.ExecShell("rpm -qa | grep mysql"); r.Success {
		exec.ExecShell("systemctl stop mysqld")
		exec.ExecShell("rpm -qa | grep mysql | xargs rpm -e")
		exec.ExecShell("rm -rf /var/lib/mysql /usr/sbin/mysqld /usr/local/mysql /etc/my.cnf /var/log/mysqld.log /var/log/mysql.log")
	}
	// 安装依赖
	t.mysqlLib(exec, "zlib-devel", "rpm -qa | grep zlib-devel", "yum -y install zlib-devel")
	t.mysqlLib(exec, "bzip2-devel", "rpm -qa | grep bzip2-devel", "yum -y install bzip2-devel")
	t.mysqlLib(exec, "openssl-devel", "rpm -qa | grep openssl-devel", "yum -y install openssl-devel")
	t.mysqlLib(exec, "ncurses-devel", "rpm -qa | grep ncurses-devel", "yum -y install ncurses-devel")
	if osType == osinfo.OsTypeCentos7 {
		t.mysqlLib(exec, "libaio", "rpm -qa | grep libaio", "yum -y install libaio")
	}
	// 安装
	exec.ExecShell(fmt.Sprintf("yum -y localinstall %s/*.rpm", httpRootPath))
	exec.ExecShell("mysqld --initialize --user=mysql")
	exec.ExecShell(fmt.Sprintf("systemctl start %s", mysqlService))
	exec.ExecShell(fmt.Sprintf("systemctl enable %s", mysqlService))
	exec.ExecShell("sleep 2")

	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); r.Success {
		tmpPasswd := strings.TrimSpace(exec.ExecShell("grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}'").Output)
		slog.Info("临时密码已获取，开始修改密码")
		exec.ExecShell(fmt.Sprintf("/usr/bin/mysqladmin -uroot -p'%s' password '%s'", tmpPasswd, t.Password))
		t.rootUserConf(exec)
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

func (t *InitMysql) mysqlLib(exec executor.Executor, name, checkCmd, installCmd string) {
	if r := exec.ExecShell(checkCmd); r.Success {
		slog.Info("依赖已存在", "name", name)
		return
	}
	exec.ExecShell(installCmd)
	if r := exec.ExecShell(checkCmd); r.Success {
		slog.Info("依赖安装成功", "name", name)
	}
}

func (t *InitMysql) rootUserConf(exec executor.Executor) {
	exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"update mysql.user set host='%%' where user ='root';\"", t.Port, t.Password))
	exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"FLUSH PRIVILEGES;\"", t.Port, t.Password))
	exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"", t.Port, t.Password, t.Password))
	exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"", t.Port, t.Password, t.Password))
	exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"FLUSH PRIVILEGES;\"", t.Port, t.Password))
}

func (t *InitMysql) mysqldConf() []string {
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

func (t *InitMysql) checkStart(exec executor.Executor, mysqlService string) error {
	if r := exec.ExecShell(fmt.Sprintf("systemctl status %s", mysqlService)); !r.Success {
		return errors.New("mysql 启动状态失败，请检查")
	}
	return nil
}
