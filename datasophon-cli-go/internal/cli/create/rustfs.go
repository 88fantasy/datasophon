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

type createRustfsCmd struct {
	// 配置文件模式（-c）
	configFile     string
	datasophonPath string

	// 两种模式公用
	installPath string

	// 手动模式（无 -c 时必填）
	node     string
	file     string // -f: Rustfs tar 安装包完整路径
	webPort  string
	apiPort  string
	user     string
	password string

	dryRun bool
}

func NewRustfsCommand(dryRun *bool) *cobra.Command {
	c := &createRustfsCmd{}
	cmd := &cobra.Command{
		Use:   "rustfs",
		Short: "在 rustfs 节点上安装并启动 Rustfs 对象存储",
		Long: `安装 Rustfs 对象存储，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 rustfs 读取参数，
     SSH 到 rustfs.nodes 列表中的每个节点依次远程执行；
     安装成功后将 rustfs.enable 置为 true 写回配置文件。
     需同时提供 --datasophonPath 和 --installPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --node / -f / --installPath / --webPort / --apiPort / --user / --password。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	// ── 配置文件模式 ────────────────────────────────────────────────────
	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 rustfs 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	// ── 两种模式公用 ─────────────────────────────────────────────────────
	cmd.Flags().StringVar(&c.installPath, "installPath", "", "Rustfs 安装路径（必填）")

	// ── 手动模式 ─────────────────────────────────────────────────────────
	cmd.Flags().StringVar(&c.node, "node", "", "Rustfs 节点 hostname 或 IP，同时作为服务绑定地址（手动模式必填）")
	cmd.Flags().StringVarP(&c.file, "file", "f", "", "Rustfs tar 安装包完整路径（手动模式必填）")
	cmd.Flags().StringVar(&c.webPort, "webPort", "", "控制台端口（手动模式必填）")
	cmd.Flags().StringVar(&c.apiPort, "apiPort", "", "API 端口（手动模式必填）")
	cmd.Flags().StringVarP(&c.user, "user", "u", "", "访问密钥（手动模式必填）")
	cmd.Flags().StringVarP(&c.password, "password", "p", "", "密钥（手动模式必填）")

	return cmd
}

func (c *createRustfsCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

// runFromConfig 从配置文件读取 rustfs，SSH 到每个节点依次执行，
// 全部成功后将 rustfs.enable 置为 true 写回配置文件。
func (c *createRustfsCmd) runFromConfig() error {
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

	rustfsCfg := cfg.Rustfs
	if len(rustfsCfg.Nodes) == 0 {
		return fmt.Errorf("配置中 rustfs.nodes 为空，至少需要一个节点")
	}

	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}

	// 推导 registry 连接字段（跟随 cfg.Registry.Enable）
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

	// 依次在每个节点安装
	for _, hostname := range rustfsCfg.Nodes {
		node, ok := globalNodes[hostname]
		if !ok {
			return fmt.Errorf("配置中未找到 rustfs 节点: %s（请检查 rustfs.nodes 与 nodes 列表是否一致）", hostname)
		}

		t := &initcmd.InitRustfs{
			Enable:      true,
			PackagePath: packagesPath,
			InstallPath: c.installPath,
			X86Tar:      cfg.Packages.Rustfs.X86_64,
			Aarch64Tar:  cfg.Packages.Rustfs.Aarch64,
			WebHost:     node.IP,
			WebPort:     rustfsCfg.Config.WebPort,
			APIPort:     rustfsCfg.Config.APIPort,
			Username:    rustfsCfg.Config.User,
			Password:    rustfsCfg.Config.Password,
		}
		t.EnableRegistry = cfg.Registry.Enable
		t.RegistryIP = regIP
		t.RegistryPort = regPort
		t.RegistryUsername = regUser
		t.RegistryPassword = regPwd

		if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
			return fmt.Errorf("节点 %s 安装失败: %w", hostname, err)
		}
	}

	// 全部节点安装成功后持久化 enable=true
	cfg.Rustfs.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

// runFromFlags 从命令行参数读取所有字段，在本地节点执行。
func (c *createRustfsCmd) runFromFlags() error {
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
	if c.webPort == "" {
		missing = append(missing, "--webPort")
	}
	if c.apiPort == "" {
		missing = append(missing, "--apiPort")
	}
	if c.user == "" {
		missing = append(missing, "--user")
	}
	if c.password == "" {
		missing = append(missing, "--password")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	t := &initcmd.InitRustfs{
		Enable:      true,
		PackagePath: filepath.Dir(c.file),
		InstallPath: c.installPath,
		X86Tar:      filepath.Base(c.file),
		Aarch64Tar:  filepath.Base(c.file),
		WebHost:     c.node,
		WebPort:     c.webPort,
		APIPort:     c.apiPort,
		Username:    c.user,
		Password:    c.password,
	}
	t.EnableRegistry = false

	return t.Run(executor.NewLocalExecutor(c.dryRun))
}
