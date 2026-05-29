package create

import (
	"fmt"
	"log/slog"
	"os"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// createNodeCmd 新增节点初始化。
// embed nodeInitializer 复用公共路径状态和 helper；不再 embed createClusterCmd，
// 因此不携带 cluster-only 的 flag 字段。
type createNodeCmd struct {
	nodeInitializer

	standaloneIP       string
	standaloneUser     string
	standalonePassword string
	standalonePort     int
	standaloneHostname string
}

func (c *createNodeCmd) run() error {
	return c.runStandalone()
}

// runStandalone 独立模式:不加载配置文件，直接用 CLI 参数初始化目标节点（10 步节点级 DAG）。
// 初始化成功后若 cluster-sample.yml 存在，则把新节点追加到其 nodes 列表。
func (c *createNodeCmd) runStandalone() error {
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
	if err := c.setupStandalone(host); err != nil {
		return err
	}
	if err := c.initStandaloneNode(host); err != nil {
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
// 通过 --ip/--user/--password/--port/--hostname 直接对目标节点执行基础初始化。
func NewNodeCommand(dryRun *bool) *cobra.Command {
	c := &createNodeCmd{}
	cmd := &cobra.Command{
		Use:   "node",
		Short: "初始化新增节点（通过 --ip 指定目标节点）",
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
	_ = cmd.MarkFlagRequired("ip")
	_ = cmd.MarkFlagRequired("user")
	_ = cmd.MarkFlagRequired("password")
	_ = cmd.MarkFlagRequired("port")
	_ = cmd.MarkFlagRequired("hostname")

	return cmd
}
