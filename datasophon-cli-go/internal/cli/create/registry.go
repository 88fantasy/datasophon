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

type createRegistryCmd struct {
	// 配置文件模式（-c）
	configFile     string
	datasophonPath string
	installPath    string

	// 手动模式（无 -c 时必填）
	regType        string
	node           string
	file           string // -f: nexus 安装包完整路径
	webPort        string
	user           string
	password       string
	dockerHTTPPort int
	repositories   []string

	dryRun bool
}

func NewRegistryCommand(dryRun *bool) *cobra.Command {
	c := &createRegistryCmd{}
	cmd := &cobra.Command{
		Use:   "registry",
		Short: "在 registry 节点上安装 Sonatype Nexus 制品库",
		Long: `安装 Sonatype Nexus 制品库，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 global.registry 读取参数，
     SSH 到 registry 节点远程执行，完成后将 global.registry.enable 置为 true 写回配置文件。
     需同时提供 --datasophonPath 和 --installPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --node / -f / --webPort / --user / --password / --dockerHttpPort / --repositories / --installPath。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	// ── 配置文件模式 ────────────────────────────────────────────────────
	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 global.registry 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	// ── 两种模式公用 ─────────────────────────────────────────────────────
	cmd.Flags().StringVar(&c.installPath, "installPath", "", "Nexus 安装路径（必填）")

	// ── 手动模式 ─────────────────────────────────────────────────────────
	cmd.Flags().StringVar(&c.regType, "type", "nexus", "制品库类型")
	cmd.Flags().StringVar(&c.node, "node", "", "registry 节点 hostname 或 IP（手动模式必填）")
	cmd.Flags().StringVarP(&c.file, "file", "f", "", "Nexus 安装包完整路径（手动模式必填）")
	cmd.Flags().StringVar(&c.webPort, "webPort", "", "Nexus 端口（手动模式必填）")
	cmd.Flags().StringVarP(&c.user, "user", "u", "", "Nexus 用户名（手动模式必填）")
	cmd.Flags().StringVarP(&c.password, "password", "p", "", "Nexus 密码（手动模式必填）")
	cmd.Flags().IntVar(&c.dockerHTTPPort, "dockerHttpPort", 0, "Docker HTTP 端口（手动模式必填）")
	cmd.Flags().StringSliceVarP(&c.repositories, "repositories", "r", nil, "仓库列表（手动模式必填）")

	return cmd
}

func (c *createRegistryCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

// runFromConfig 从配置文件读取 global.registry，SSH 到 registry 节点执行，
// 安装成功后将 global.registry.enable 置为 true 写回配置文件。
func (c *createRegistryCmd) runFromConfig() error {
	if c.datasophonPath == "" {
		return fmt.Errorf("配置文件模式下 --datasophonPath 为必填项")
	}
	if c.installPath == "" {
		return fmt.Errorf("--installPath 为必填项")
	}
	if !strings.HasPrefix(c.datasophonPath, "/") {
		return fmt.Errorf("--datasophonPath 必须是绝对路径（以 / 开头）")
	}
	c.datasophonPath = strings.TrimSuffix(c.datasophonPath, "/")
	packagesPath := c.datasophonPath + "/datasophon-init/packages"

	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	reg := cfg.Global.Registry
	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[reg.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 registry 节点: %s（请检查 global.registry.node 与 nodes 列表是否一致）", reg.Node)
	}

	t := &initcmd.InitRegistry{
		PackagePath:    packagesPath,
		InstallPath:    c.installPath,
		Repositories:   reg.Config.Repositories,
		X86Tar:         cfg.Global.Packages.Nexus.X86_64,
		Aarch64Tar:     cfg.Global.Packages.Nexus.Aarch64,
		WebHost:        reg.Node,
		WebPort:        reg.Config.WebPort,
		Username:       reg.Config.User,
		Password:       reg.Config.Password,
		DockerHTTPPort: reg.Config.DockerHTTPPort,
	}
	t.EnableRegistry = true

	if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
		return err
	}

	// 安装成功后持久化 enable=true
	cfg.Global.Registry.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

// runFromFlags 从命令行参数读取所有字段，在本地节点执行。
func (c *createRegistryCmd) runFromFlags() error {
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
	if c.user == "" {
		missing = append(missing, "--user")
	}
	if c.password == "" {
		missing = append(missing, "--password")
	}
	if c.dockerHTTPPort == 0 {
		missing = append(missing, "--dockerHttpPort")
	}
	if len(c.repositories) == 0 {
		missing = append(missing, "--repositories")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	t := &initcmd.InitRegistry{
		Type:           c.regType,
		PackagePath:    filepath.Dir(c.file),
		InstallPath:    c.installPath,
		Repositories:   c.repositories,
		X86Tar:         filepath.Base(c.file),
		Aarch64Tar:     filepath.Base(c.file),
		WebHost:        c.node,
		WebPort:        c.webPort,
		Username:       c.user,
		Password:       c.password,
		DockerHTTPPort: c.dockerHTTPPort,
	}
	t.EnableRegistry = true

	return t.Run(executor.NewLocalExecutor(c.dryRun))
}
