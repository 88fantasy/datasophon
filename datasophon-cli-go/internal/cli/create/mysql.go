package create

import (
	"fmt"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

type createMysqlCmd struct {
	// 配置文件模式（-c）
	configFile     string
	datasophonPath string

	// 两种模式公用
	installPath string

	// 手动模式（无 -c 时必填）
	node     string
	file     string // -f: MySQL tar 安装包完整路径
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

	// ── 配置文件模式 ────────────────────────────────────────────────────
	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 mysql 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	// ── 两种模式公用 ─────────────────────────────────────────────────────
	cmd.Flags().StringVar(&c.installPath, "installPath", "", "MySQL 安装路径（必填）")

	// ── 手动模式 ─────────────────────────────────────────────────────────
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

// runFromConfig 从配置文件读取 mysql，SSH 到 mysql.node 执行，
// 安装成功后将 mysql.enable 置为 true 写回配置文件，并依次创建所有 appDbs。
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

	// 推导 registry 连接字段（EnableRegistry 跟随 cfg.Registry.Enable）
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

	installer := &initcmd.InitMysql{
		Password:    mysqlCfg.Password,
		Force:       mysqlCfg.Force,
		PackagePath: packagesPath,
		InstallPath: c.installPath,
		X86Tar:      cfg.Packages.Mysql.X86_64,
		Aarch64Tar:  cfg.Packages.Mysql.Aarch64,
		Port:        mysqlCfg.Port,
	}
	installer.EnableRegistry = cfg.Registry.Enable
	installer.RegistryIP = regIP
	installer.RegistryPort = regPort
	installer.RegistryUsername = regUser
	installer.RegistryPassword = regPwd

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

	// 安装成功后持久化 enable=true
	cfg.Mysql.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

// runFromFlags 从命令行参数读取所有字段，在本地节点执行（不创建 appDbs）。
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

	installer := &initcmd.InitMysql{
		Password:    c.password,
		Force:       false,
		PackagePath: filepath.Dir(c.file),
		InstallPath: c.installPath,
		X86Tar:      filepath.Base(c.file),
		Aarch64Tar:  filepath.Base(c.file),
		Port:        c.port,
	}
	installer.EnableRegistry = false

	return installer.Run(executor.NewLocalExecutor(c.dryRun))
}
