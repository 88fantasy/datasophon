package create

import (
	"fmt"
	"log/slog"
	"net"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
)

type createClusterCmd struct {
	DatasophonPath string
	InstallPath    string
	Password       string
	Action         string
	ProductPkgsPath string

	InitPathOverwriteForce bool
	DisableUploadRegistry  bool
	MysqlInstallForce      bool
	EnableRegistry         bool
	OnlyInstallK8s         bool
	KubernetesForce        bool

	dryRun bool

	// 运行时推导字段
	initPath        string
	initConfigPath  string
	packagesPath    string
	initConfigYaml  string
	localIP         string
	sshAuthType     config.SSHAuthType
	globalNodes     map[string]*config.Host
	localHost       *config.Host
}

func NewClusterCommand(dryRun *bool) *cobra.Command {
	c := &createClusterCmd{}
	cmd := &cobra.Command{
		Use:   "cluster",
		Short: "创建或初始化集群",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.DatasophonPath, "datasophonPath", "p", "", "datasophon 绝对路径 (必填)")
	cmd.Flags().StringVar(&c.InstallPath, "installPath", "", "安装路径 (必填)")
	cmd.Flags().StringVar(&c.Password, "cpassword", "", "配置文件加密密钥 (必填)")
	cmd.Flags().StringVarP(&c.Action, "action", "a", "", "执行动作 initALL|initSingleNode (必填)")
	cmd.Flags().StringVar(&c.ProductPkgsPath, "productPackagesPath", "", "安装包路径 (必填)")
	cmd.Flags().BoolVar(&c.InitPathOverwriteForce, "initPathOverwriteForce", false, "datasophon-init 目录是否覆盖")
	cmd.Flags().BoolVar(&c.DisableUploadRegistry, "disableUploadRegistry", false, "禁止上传制品")
	cmd.Flags().BoolVar(&c.MysqlInstallForce, "mysqlInstallForce", false, "MySQL 已存在是否覆盖安装")
	cmd.Flags().BoolVarP(&c.EnableRegistry, "enableRegistry", "e", false, "是否启动制品库")
	cmd.Flags().BoolVar(&c.OnlyInstallK8s, "onlyInstallK8s", false, "仅安装 K8s")
	cmd.Flags().BoolVar(&c.KubernetesForce, "kubernetesForce", false, "K8s 已存在是否覆盖安装")

	// 对应 Java 的 -pn flag
	cmd.Flags().StringVarP(&c.ProductPkgsPath, "pn", "", "", "")
	_ = cmd.Flags().MarkHidden("pn")

	_ = cmd.MarkFlagRequired("datasophonPath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("cpassword")
	_ = cmd.MarkFlagRequired("action")
	_ = cmd.MarkFlagRequired("productPackagesPath")

	return cmd
}

func (c *createClusterCmd) run() error {
	// 路径校验（对应 Java CreateCluster.run()）
	if !strings.HasPrefix(c.DatasophonPath, "/") || !strings.HasPrefix(c.InstallPath, "/") {
		return fmt.Errorf("datasophonPath、installPath 必须是绝对路径（以 / 开头）")
	}
	c.DatasophonPath = strings.TrimSuffix(c.DatasophonPath, "/")

	if _, err := os.Stat(c.DatasophonPath); err != nil {
		return fmt.Errorf("路径不存在: %s", c.DatasophonPath)
	}
	if _, err := os.Stat(c.InstallPath); err != nil {
		_ = os.MkdirAll(c.InstallPath, 0755)
	}

	c.initPath = c.DatasophonPath + "/datasophon-init"
	c.initConfigPath = c.initPath + "/config"
	c.packagesPath = c.initPath + "/packages"
	c.initConfigYaml = c.initConfigPath + "/cluster-sample.yml"

	slog.Info("路径信息",
		"DATASOPHON_PATH", c.DatasophonPath,
		"INIT_PATH", c.initPath,
		"INIT_CONFIG_YAML", c.initConfigYaml)

	cfg, err := config.Load(c.initConfigYaml, c.Password)
	if err != nil {
		return err
	}
	c.sshAuthType = cfg.Global.SSHAuthType
	c.globalNodes = make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		c.globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}

	c.localIP = getLocalIP()
	c.localHost = &cfg.Nodes[0]
	for i := range cfg.Nodes {
		if cfg.Nodes[i].IP == c.localIP {
			c.localHost = &cfg.Nodes[i]
			slog.Info("本地节点", "host", c.localHost.Hostname)
			break
		}
	}

	switch c.Action {
	case "initALL":
		// TODO Phase 3：完整 28 步 DAG
		slog.Warn("initALL 完整 DAG 在 Phase 3 实现，当前仅执行 initSingleNode 子集")
		return c.initSingleNode(cfg)
	case "initSingleNode":
		return c.initSingleNode(cfg)
	default:
		return fmt.Errorf("action[initALL/initSingleNode] not found: %s", c.Action)
	}
}

// initSingleNode 对应 Java CreateCluster.initSingleNode()（第一版 10 个 init 任务子集）。
func (c *createClusterCmd) initSingleNode(cfg *config.ClusterConfig) error {
	nodes := cfg.AddNodes
	if len(nodes) == 0 {
		slog.Warn("addNodes 列表为空，无需执行")
		return nil
	}

	allInitNodes := cfg.Nodes

	// 分发资源包（第一版跳过，仅执行节点初始化）
	// TODO Phase 2: initBinPackage / initTar

	for i := range nodes {
		node := &nodes[i]

		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitBash{}); err != nil {
			return err
		}
		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitFirewall{}); err != nil {
			return err
		}
		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitSelinux{}); err != nil {
			return err
		}
		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitSwap{}); err != nil {
			return err
		}
		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitLibrary{}); err != nil {
			return err
		}
		if err := c.allNodesExec([]*config.Host{node}, &initcmd.InitOsUser{}); err != nil {
			return err
		}

		hostnameTask := &initcmd.InitHostname{Hostname: node.Hostname}
		if err := c.singleNodeExec(node, hostnameTask); err != nil {
			return err
		}

		allHostTask := &initcmd.InitAllHost{}
		allHostTask.ConfigFilePath = c.initConfigYaml
		allHostTask.ConfigPassword = c.Password
		for j := range allInitNodes {
			if err := c.singleNodeExec(&allInitNodes[j], allHostTask); err != nil {
				return err
			}
		}
		if err := c.singleNodeExec(node, allHostTask); err != nil {
			return err
		}
	}
	return nil
}

// allNodesExec 对应 Java CreateCluster.allNodesExec —— 对节点列表串行执行同一 handler。
func (c *createClusterCmd) allNodesExec(nodes []*config.Host, h handler.Handler) error {
	for _, node := range nodes {
		if err := c.singleNodeExec(node, h); err != nil {
			return err
		}
	}
	return nil
}

// singleNodeExec 对应 Java CreateCluster.singleNodesExec。
func (c *createClusterCmd) singleNodeExec(node *config.Host, h handler.Handler) error {
	slog.Info("在节点执行", "ip", node.IP, "hostname", node.Hostname)
	chain := handler.NewChain(node, c.sshAuthType, c.dryRun)
	chain.Add(h)
	return chain.Handle()
}

// workerNodes 过滤掉本地节点（对应 Java CreateCluster 中的 filter != localIP）。
func (c *createClusterCmd) workerNodes(nodes []config.Host) []*config.Host {
	var result []*config.Host
	for i := range nodes {
		if nodes[i].IP != c.localIP {
			result = append(result, &nodes[i])
		}
	}
	return result
}

func getLocalIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return ""
	}
	defer conn.Close()
	return conn.LocalAddr().(*net.UDPAddr).IP.String()
}

// localExecutor 返回一个 LocalExecutor（供 create cluster 中本地步骤使用）。
func localExecutor(dryRun bool) executor.Executor {
	return executor.NewLocalExecutor(dryRun)
}
