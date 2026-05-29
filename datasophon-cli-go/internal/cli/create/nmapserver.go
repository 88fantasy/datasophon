package create

import (
	"fmt"

	"github.com/spf13/cobra"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

type createNmapServerCmd struct {
	configFile string
	dryRun     bool
}

func NewNmapServerCommand(dryRun *bool) *cobra.Command {
	c := &createNmapServerCmd{}
	cmd := &cobra.Command{
		Use:   "nmap-server",
		Short: "在 nmapServer 节点上安装 nmap",
		Long: `安装 nmap，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 nmapServer 读取节点，
     SSH 到目标节点远程执行；安装成功后将 nmapServer.enable 置为 true 写回配置文件。

  2. 手动模式（不指定 -c）：在本地节点直接执行安装。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 nmapServer 读取节点）")
	return cmd
}

func (c *createNmapServerCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runLocally()
}

func (c *createNmapServerCmd) runFromConfig() error {
	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[cfg.NmapServer.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 nmapServer 节点: %s（请检查 nmapServer.node 与 nodes 列表是否一致）", cfg.NmapServer.Node)
	}

	t := &initcmd.InitNmap{}
	if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
		return err
	}

	cfg.NmapServer.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createNmapServerCmd) runLocally() error {
	t := &initcmd.InitNmap{}
	return t.Run(executor.NewLocalExecutor(c.dryRun))
}
