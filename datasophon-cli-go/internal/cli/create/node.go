package create

import (
	"fmt"
	"log/slog"
	"os"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/plan"
)

// createNodeCmd 新增节点初始化。
// 支持两种模式：
//   - 配置模式（-c/--config 指定配置文件）：从配置文件读取集群上下文，走 plan 引擎执行 12 步条件化 DAG。
//     条件：cluster-type=hadoop → hadoop_user；ntpServer.enable=true → ntp_slave；global.offline=true → offline_slave。
//     若 --ip 指定的节点已存在于配置文件 nodes 列表中（按 IP 判断）→ 提示并停止。
//   - 手动模式（不传 -c）：不加载配置文件，直接用 CLI 参数初始化目标节点（9~10 步）。
//     -t hadoop → 额外执行 hadoop_user；无 ntp_slave / offline_slave。
type createNodeCmd struct {
	nodeInitializer

	standaloneIP       string
	standaloneUser     string
	standalonePassword string
	standalonePort     int
	standaloneHostname string

	configPath      string // -c/--config 配置文件路径（配置模式）
	clusterTypeFlag string // -t/--cluster-type 手动模式集群类型（可选）
}

func (c *createNodeCmd) run() error {
	if c.configPath != "" {
		return c.runConfigMode()
	}
	return c.runManualMode()
}

// runConfigMode 配置模式：从 -c 指定的配置文件读取全局上下文，走 plan 引擎初始化目标新节点。
func (c *createNodeCmd) runConfigMode() error {
	newNode := &config.Host{
		IP:       c.standaloneIP,
		Port:     c.standalonePort,
		User:     c.standaloneUser,
		Password: c.standalonePassword,
		Hostname: c.standaloneHostname,
	}

	if err := c.setupConfig(c.configPath, newNode); err != nil {
		return err
	}

	ctx := c.toBuildContext()
	pf, err := plan.GeneratePlan("initNode", plan.InitNodeRegistry, ctx)
	if err != nil {
		return fmt.Errorf("生成节点计划失败: %w", err)
	}
	if err := plan.Save(c.initPath, pf); err != nil {
		return err
	}
	slog.Info("节点计划已写入", "path", plan.PlanPath(c.initPath, "initNode"))
	plan.PrintSummary(pf)

	if err := plan.Apply(c.initPath, "initNode", plan.InitNodeRegistry, ctx); err != nil {
		return err
	}

	return c.maybeAppendToYAML(newNode)
}

// runManualMode 手动模式：不加载配置文件，直接用 CLI 参数初始化目标节点（不走 plan 引擎）。
func (c *createNodeCmd) runManualMode() error {
	if c.standaloneUser == "" || c.standalonePassword == "" ||
		c.standaloneHostname == "" || c.standalonePort == 0 {
		return fmt.Errorf("--ip 模式下必须同时提供 --user --password --hostname --port")
	}
	host := &config.Host{
		IP:       c.standaloneIP,
		Port:     c.standalonePort,
		User:     c.standaloneUser,
		Password: c.standalonePassword,
		Hostname: c.standaloneHostname,
	}

	// 解析可选的 -t/--cluster-type
	var clusterType config.ClusterType
	if c.clusterTypeFlag != "" {
		ct, err := config.ParseClusterType(c.clusterTypeFlag)
		if err != nil {
			return err
		}
		clusterType = ct
	}

	if err := c.setupStandalone(host); err != nil {
		return err
	}
	if err := c.initStandaloneNode(host, clusterType); err != nil {
		return err
	}
	return c.maybeAppendToYAML(host)
}

func (c *createNodeCmd) maybeAppendToYAML(host *config.Host) error {
	if _, err := os.Stat(c.initConfigYaml); os.IsNotExist(err) {
		slog.Info("cluster-sample.yml 不存在，跳过写回", "path", c.initConfigYaml)
		return nil
	}
	appended, err := appendNodeToYAML(c.initConfigYaml, host)
	if err != nil {
		return fmt.Errorf("写回 cluster-sample.yml 失败: %w", err)
	}
	if appended {
		slog.Info("节点已追加到 cluster-sample.yml",
			"path", c.initConfigYaml, "ip", host.IP, "hostname", host.Hostname)
	} else {
		slog.Warn("同 IP 或 hostname 节点已存在，跳过写回",
			"ip", host.IP, "hostname", host.Hostname)
	}
	return nil
}

// NewNodeCommand 对应 datasophon-cli create node。
// 配置模式：-c <file> + --ip/--user/--password/--port/--hostname 5 个定位参数。
// 手动模式：--ip/--user/--password/--port/--hostname，可选 -t/--cluster-type。
func NewNodeCommand(dryRun *bool) *cobra.Command {
	c := &createNodeCmd{}
	cmd := &cobra.Command{
		Use:   "node",
		Short: "初始化新增节点（配置模式 -c <file> 或手动模式 --ip 指定目标节点）",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	c.bindCommonFlags(cmd)
	cmd.Flags().StringVar(&c.standaloneIP, "ip", "", "目标节点 IP")
	cmd.Flags().StringVar(&c.standaloneUser, "user", "", "SSH 用户")
	cmd.Flags().StringVar(&c.standalonePassword, "password", "", "SSH 密码")
	cmd.Flags().IntVar(&c.standalonePort, "port", 0, "SSH 端口")
	cmd.Flags().StringVar(&c.standaloneHostname, "hostname", "", "目标节点 hostname")
	cmd.Flags().StringVarP(&c.configPath, "config", "c", "", "配置文件路径（配置模式，提供集群上下文与 ntp/offline 服务端 IP）")
	cmd.Flags().StringVarP(&c.clusterTypeFlag, "cluster-type", "t", "", "集群类型 hadoop|kubernetes（手动模式可选，仅 hadoop 时创建 hadoop 用户）")
	_ = cmd.MarkFlagRequired("ip")
	_ = cmd.MarkFlagRequired("user")
	_ = cmd.MarkFlagRequired("password")
	_ = cmd.MarkFlagRequired("port")
	_ = cmd.MarkFlagRequired("hostname")

	return cmd
}
