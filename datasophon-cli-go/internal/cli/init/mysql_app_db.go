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
	if strings.TrimSpace(r.Output) != "1" {
		exec.ExecShell(fmt.Sprintf("systemctl restart %s", mysqlService))
		return fmt.Errorf("mysql 服务未运行，无法创建应用数据库账号")
	}
	return t.initCommonAccount(exec)
}

func (t *InitMysqlAppDb) initCommonAccount(exec executor.Executor) error {
	runSQL := func(sql string) error {
		r := exec.ExecShell(fmt.Sprintf("mysql -uroot -P'%d' -p'%s' -e \"%s\"", t.Port, t.RootPassword, sql))
		if !r.Success {
			return fmt.Errorf("执行 SQL 失败（%s): %s", sql, r.ErrOutput)
		}
		return nil
	}

	if err := runSQL(fmt.Sprintf("CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;", t.DBName)); err != nil {
		return err
	}
	if err := runSQL(fmt.Sprintf("CREATE USER '%s'@'%%' IDENTIFIED BY '%s';", t.Account, t.Password)); err != nil {
		return err
	}
	if err := runSQL(fmt.Sprintf("ALTER USER '%s'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;", t.Account, t.Password)); err != nil {
		return err
	}
	if err := runSQL(fmt.Sprintf("ALTER USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';", t.Account, t.Password)); err != nil {
		return err
	}
	if t.Account == "bigdata" {
		if err := runSQL(fmt.Sprintf("GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%%';", t.DBName, t.Account)); err != nil {
			return err
		}
	} else {
		if err := runSQL(fmt.Sprintf("GRANT ALL PRIVILEGES ON *.* TO '%s'@'%%';", t.Account)); err != nil {
			return err
		}
	}
	if err := runSQL("FLUSH PRIVILEGES;"); err != nil {
		return err
	}
	slog.Info("数据库账号创建完成", "account", t.Account, "db", t.DBName)
	return nil
}

// Run 导出 doRun，供 create 包配置模式直接调用。
func (t *InitMysqlAppDb) Run(exec executor.Executor) error { return t.doRun(exec) }
