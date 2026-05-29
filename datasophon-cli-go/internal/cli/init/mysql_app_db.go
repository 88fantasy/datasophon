package initcmd

import (
	"fmt"
	"log/slog"
	"strings"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitMysqlAppDb 对应 Java InitMysqlAppDb — 创建数据库与应用账号。
type InitMysqlAppDb struct {
	TaskBase
	RootPassword string
	Account      string
	Password     string
	DBName       string
	Port         int
}

func (t *InitMysqlAppDb) Name() string { return "创建数据库与账号密码" }

func (t *InitMysqlAppDb) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitMysqlAppDb) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "mysql_app_db",
		Short: "创建数据库与应用账号",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.RootPassword, "rootPassword", "", "root 密码（必填）")
	cmd.Flags().StringVarP(&t.Account, "account", "a", "", "应用账号（必填）")
	cmd.Flags().StringVarP(&t.Password, "p", "p", "", "账号密码（必填）")
	cmd.Flags().StringVarP(&t.DBName, "dbName", "d", "", "数据库名（必填）")
	cmd.Flags().IntVar(&t.Port, "mysqlPort", 3306, "端口（必填）")
	_ = cmd.MarkFlagRequired("rootPassword")
	_ = cmd.MarkFlagRequired("account")
	_ = cmd.MarkFlagRequired("p")
	_ = cmd.MarkFlagRequired("dbName")
	return cmd
}

func (t *InitMysqlAppDb) doRun(exec executor.Executor) error {
	osType := exec.GetOs()
	mysqlService := "mysqld"
	if osType.IsUbuntu() {
		mysqlService = "mysql"
	}

	r := exec.ExecShell(fmt.Sprintf("systemctl status %s | grep running | wc -l", mysqlService))
	if strings.TrimSpace(r.Output) == "1" {
		t.initCommonAccount(exec)
	} else {
		exec.ExecShell(fmt.Sprintf("systemctl restart %s", mysqlService))
	}
	return nil
}

func (t *InitMysqlAppDb) initCommonAccount(exec executor.Executor) {
	exec.ExecShell(fmt.Sprintf(
		"mysql -uroot -P'%d' -p'%s' -e \"CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;\"",
		t.Port, t.RootPassword, t.DBName))
	exec.ExecShell(fmt.Sprintf(
		"mysql -uroot -P'%d' -p'%s' -e \"CREATE USER '%s'@'%%' IDENTIFIED BY '%s';\"",
		t.Port, t.RootPassword, t.Account, t.Password))
	exec.ExecShell(fmt.Sprintf(
		"mysql -uroot -P'%d' -p'%s' -e \"ALTER USER '%s'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;\"",
		t.Port, t.RootPassword, t.Account, t.Password))
	exec.ExecShell(fmt.Sprintf(
		"mysql -uroot -P'%d' -p'%s' -e \"ALTER USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';\"",
		t.Port, t.RootPassword, t.Account, t.Password))
	if t.Account == "bigdata" {
		exec.ExecShell(fmt.Sprintf(
			"mysql -uroot -P'%d' -p'%s' -e \"GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';\"",
			t.Port, t.RootPassword, t.DBName, t.Account))
	} else {
		exec.ExecShell(fmt.Sprintf(
			"mysql -uroot -P'%d' -p'%s' -e \"GRANT ALL PRIVILEGES ON *.* TO '%s'@'%%';\"",
			t.Port, t.RootPassword, t.Account))
	}
	exec.ExecShell(fmt.Sprintf(
		"mysql -uroot -P'%d' -p'%s' -e \"FLUSH PRIVILEGES;\"",
		t.Port, t.RootPassword))
	slog.Info("数据库账号创建完成", "account", t.Account, "db", t.DBName)
}

// Run 导出 doRun，供 create 包配置模式直接调用。
func (t *InitMysqlAppDb) Run(exec executor.Executor) error { return t.doRun(exec) }
