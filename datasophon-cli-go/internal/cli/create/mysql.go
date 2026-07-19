package create

import (
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/bootstrap"
	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// mysqlTask 安装 MySQL 8（本地 rpm/deb 包）。
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
		if err := bootstrap.ConfigureMySQLRoot(exec, t.Password, t.Port); err != nil {
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
		if err := bootstrap.ResetMySQLTemporaryRootPassword(exec, tmpPasswd, t.Password); err != nil {
			return err
		}
		if err := bootstrap.ConfigureMySQLRoot(exec, t.Password, t.Port); err != nil {
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

// ── 命令实现 ────────────────────────────────────────────────────────────────

type createMysqlCmd struct {
	configFile     string
	datasophonPath string

	installPath string

	node     string
	file     string
	password string
	port     int

	dryRun bool
}

func NewMysqlCommand(dryRun *bool) *cobra.Command {
	c := &createMysqlCmd{}
	cmd := &cobra.Command{
		Use:   "mysql",
		Short: "在 mysql 节点上安装 MySQL 8",
		Long: `安装 MySQL 8，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 mysql 读取参数，
     SSH 到 mysql.node 节点远程执行；安装成功后将 mysql.enable 置为 true 写回配置文件。
     同时依次创建 mysql.appDbs 中的所有应用数据库与账号。
     需同时提供 --datasophonPath 和 --installPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --node / -f / --installPath / --password；不创建 appDbs。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 mysql 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	cmd.Flags().StringVar(&c.installPath, "installPath", "", "MySQL 安装路径（必填）")

	cmd.Flags().StringVar(&c.node, "node", "", "MySQL 节点 hostname 或 IP（手动模式必填）")
	cmd.Flags().StringVarP(&c.file, "file", "f", "", "MySQL tar 安装包完整路径（手动模式必填）")
	cmd.Flags().StringVarP(&c.password, "password", "p", "", "MySQL root 密码（手动模式必填）")
	cmd.Flags().IntVar(&c.port, "port", 3306, "MySQL 端口（手动模式，默认 3306）")

	return cmd
}

func (c *createMysqlCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

func (c *createMysqlCmd) runFromConfig() error {
	if c.datasophonPath == "" {
		return fmt.Errorf("配置文件模式下 --datasophonPath 为必填项")
	}
	if c.installPath == "" {
		return fmt.Errorf("--installPath 为必填项")
	}
	if !strings.HasPrefix(c.datasophonPath, "/") {
		return fmt.Errorf("--datasophonPath 必须是绝对路径（以 / 开头）")
	}
	if !strings.HasPrefix(c.installPath, "/") {
		return fmt.Errorf("--installPath 必须是绝对路径（以 / 开头）")
	}
	c.datasophonPath = strings.TrimSuffix(c.datasophonPath, "/")
	packagesPath := c.datasophonPath + "/datasophon-init/packages"

	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	mysqlCfg := cfg.Mysql
	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[mysqlCfg.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 mysql 节点: %s（请检查 mysql.node 与 nodes 列表是否一致）", mysqlCfg.Node)
	}

	var regIP, regPort, regUser, regPwd string
	if cfg.Registry.Enable {
		regNode, found := globalNodes[cfg.Registry.Node]
		if !found {
			return fmt.Errorf("配置中未找到 registry 节点: %s（registry.enable=true 时需要）", cfg.Registry.Node)
		}
		regIP = regNode.IP
		regPort = cfg.Registry.Config.WebPort
		regUser = cfg.Registry.Config.User
		regPwd = cfg.Registry.Config.Password
	}

	installer := &mysqlTask{
		Password:         mysqlCfg.Password,
		Force:            mysqlCfg.Force,
		PackagePath:      packagesPath,
		InstallPath:      c.installPath,
		X86Tar:           cfg.Packages.Mysql.X86_64,
		Aarch64Tar:       cfg.Packages.Mysql.Aarch64,
		Port:             mysqlCfg.Port,
		EnableRegistry:   cfg.Registry.Enable,
		RegistryIP:       regIP,
		RegistryPort:     regPort,
		RegistryUsername: regUser,
		RegistryPassword: regPwd,
	}

	ch := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun)
	ch.Add(installer)
	for _, db := range mysqlCfg.AppDbs {
		ch.Add(&initcmd.InitMysqlAppDb{
			RootPassword: mysqlCfg.Password,
			Account:      db.Account,
			Password:     db.Password,
			DBName:       db.DbName,
			Port:         mysqlCfg.Port,
		})
	}
	if err := ch.Handle(); err != nil {
		return err
	}

	cfg.Mysql.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createMysqlCmd) runFromFlags() error {
	missing := []string{}
	if c.installPath == "" {
		missing = append(missing, "--installPath")
	}
	if c.node == "" {
		missing = append(missing, "--node")
	}
	if c.file == "" {
		missing = append(missing, "--file / -f")
	}
	if c.password == "" {
		missing = append(missing, "--password")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	installer := &mysqlTask{
		Password:       c.password,
		Force:          false,
		PackagePath:    filepath.Dir(c.file),
		InstallPath:    c.installPath,
		X86Tar:         filepath.Base(c.file),
		Aarch64Tar:     filepath.Base(c.file),
		Port:           c.port,
		EnableRegistry: false,
	}
	return installer.doRun(executor.NewLocalExecutor(c.dryRun))
}
