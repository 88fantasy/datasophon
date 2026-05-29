package create

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

type createYumServerCmd struct {
	// 配置文件模式（-c）
	configFile     string
	datasophonPath string

	// 手动模式（无 -c 时必填）
	packagePath string
	serverIP    string
	serverPort  string

	dryRun bool
}

func NewYumServerCommand(dryRun *bool) *cobra.Command {
	c := &createYumServerCmd{}
	cmd := &cobra.Command{
		Use:   "yum-server",
		Short: "在 yumServer 节点上配置 httpd/apache2 离线包源",
		Long: `配置 httpd/apache2 作为 yum/apt 离线包源，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 yumServer 读取参数，
     SSH 到目标节点远程执行；安装成功后将 yumServer.enable 置为 true 写回配置文件。
     需同时提供 --datasophonPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --packagePath / --serverIp / --serverPort。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	// ── 配置文件模式 ────────────────────────────────────────────────────
	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 yumServer 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	// ── 手动模式 ─────────────────────────────────────────────────────────
	cmd.Flags().StringVarP(&c.packagePath, "packagePath", "p", "", "安装包目录（手动模式必填，需包含 os/ 子目录）")
	cmd.Flags().StringVar(&c.serverIP, "serverIp", "", "httpd 服务绑定 IP（手动模式必填）")
	cmd.Flags().StringVar(&c.serverPort, "serverPort", "", "httpd 服务端口（手动模式必填）")

	return cmd
}

func (c *createYumServerCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

func (c *createYumServerCmd) runFromConfig() error {
	if c.datasophonPath == "" {
		return fmt.Errorf("配置文件模式下 --datasophonPath 为必填项")
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

	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[cfg.YumServer.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 yumServer 节点: %s（请检查 yumServer.node 与 nodes 列表是否一致）", cfg.YumServer.Node)
	}

	t := &initcmd.InitOfflineServer{
		PackagePath: packagesPath,
		ServerIP:    node.IP,
		ServerPort:  cfg.YumServer.ListenPort,
	}
	// 主动触发，跳过 doRun 内部 enableRegistry=true 的守卫
	t.EnableRegistry = false

	if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
		return err
	}

	cfg.YumServer.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createYumServerCmd) runFromFlags() error {
	missing := []string{}
	if c.packagePath == "" {
		missing = append(missing, "--packagePath")
	}
	if c.serverIP == "" {
		missing = append(missing, "--serverIp")
	}
	if c.serverPort == "" {
		missing = append(missing, "--serverPort")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	t := &initcmd.InitOfflineServer{
		PackagePath: c.packagePath,
		ServerIP:    c.serverIP,
		ServerPort:  c.serverPort,
	}
	t.EnableRegistry = false

	return t.Run(executor.NewLocalExecutor(c.dryRun))
}
