package bootstrap

import (
	"fmt"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

// ResetMySQLTemporaryRootPassword 使用 MySQL 初始临时密码设置正式 root 密码。
func ResetMySQLTemporaryRootPassword(exec executor.Executor, temporaryPassword, password string) error {
	oldCnf := "/tmp/.dsph_mysql_old.cnf"
	exec.WriteLines([]string{"[client]", "password=\"" + temporaryPassword + "\""}, oldCnf)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", oldCnf))
	newSQLPath := "/tmp/.dsph_mysql_init.sql"
	exec.WriteLines([]string{
		fmt.Sprintf("ALTER USER 'root'@'localhost' IDENTIFIED BY '%s';", password),
	}, newSQLPath)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", newSQLPath))
	result := exec.ExecShell(fmt.Sprintf(
		"mysql --defaults-extra-file=%s --connect-expired-password -uroot < %s", oldCnf, newSQLPath))
	exec.ExecShell(fmt.Sprintf("rm -f %s %s", oldCnf, newSQLPath))
	if !result.Success {
		return fmt.Errorf("修改 root 初始密码失败: %s", result.ErrOutput)
	}
	return nil
}

// ConfigureMySQLRoot 统一配置 root 远程访问、密码策略和认证插件。
func ConfigureMySQLRoot(exec executor.Executor, password string, port int) error {
	newCnf := "/tmp/.dsph_mysql_new.cnf"
	exec.WriteLines([]string{"[client]", "password=\"" + password + "\""}, newCnf)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", newCnf))
	sqlPath := "/tmp/.dsph_mysql_conf.sql"
	exec.WriteLines([]string{
		"UPDATE mysql.user SET host='%' WHERE user='root';",
		"FLUSH PRIVILEGES;",
		fmt.Sprintf("ALTER USER 'root'@'%%' IDENTIFIED BY '%s' PASSWORD EXPIRE NEVER;", password),
		fmt.Sprintf("ALTER USER 'root'@'%%' IDENTIFIED WITH mysql_native_password BY '%s';", password),
		"FLUSH PRIVILEGES;",
	}, sqlPath)
	exec.ExecShell(fmt.Sprintf("chmod 600 %s", sqlPath))
	result := exec.ExecShell(fmt.Sprintf("mysql --defaults-extra-file=%s -uroot -P%d < %s", newCnf, port, sqlPath))
	exec.ExecShell(fmt.Sprintf("rm -f %s %s", newCnf, sqlPath))
	if !result.Success {
		return fmt.Errorf("配置 root 用户权限失败: %s", result.ErrOutput)
	}
	return nil
}
