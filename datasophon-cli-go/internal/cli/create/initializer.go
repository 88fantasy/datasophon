package create

import (
	"fmt"
	"log/slog"
	"net"
	"os"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
	"gopkg.in/yaml.v3"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// nodeInitializer 持有集群模式和独立模式共用的状态与 helper。
// createClusterCmd 和 createNodeCmd 各自 embed 它。
type nodeInitializer struct {
	DatasophonPath         string
	InstallPath            string
	ProductPkgsPath        string
	InitPathOverwriteForce bool

	dryRun bool

	// 运行时推导字段
	initPath       string
	initConfigPath string
	packagesPath   string
	initConfigYaml string
	localIP        string
	sshAuthType    config.SSHAuthType
	globalNodes    map[string]*config.Host
	localHost      *config.Host
}

// bindCommonFlags 注册两个命令共用的 4 个 flag。
func (n *nodeInitializer) bindCommonFlags(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&n.DatasophonPath, "datasophonPath", "p", "", "datasophon 绝对路径 (必填)")
	cmd.Flags().StringVar(&n.InstallPath, "installPath", "", "安装路径 (必填)")
	cmd.Flags().StringVarP(&n.ProductPkgsPath, "productPackagesPath", "n", "", "安装包路径 (必填)")
	cmd.Flags().BoolVar(&n.InitPathOverwriteForce, "initPathOverwriteForce", false, "datasophon-init 目录是否覆盖")
	_ = cmd.MarkFlagRequired("datasophonPath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("productPackagesPath")
}

// setup 集群模式:校验路径、推导运行时字段、加载集群配置。
func (n *nodeInitializer) setup() (*config.ClusterConfig, error) {
	if !strings.HasPrefix(n.DatasophonPath, "/") || !strings.HasPrefix(n.InstallPath, "/") {
		return nil, fmt.Errorf("datasophonPath、installPath 必须是绝对路径（以 / 开头）")
	}
	n.DatasophonPath = strings.TrimSuffix(n.DatasophonPath, "/")

	if _, err := os.Stat(n.DatasophonPath); err != nil {
		return nil, fmt.Errorf("路径不存在: %s", n.DatasophonPath)
	}
	if _, err := os.Stat(n.InstallPath); err != nil {
		if mkErr := os.MkdirAll(n.InstallPath, 0755); mkErr != nil {
			return nil, fmt.Errorf("创建安装路径失败 %s: %w", n.InstallPath, mkErr)
		}
	}

	n.initPath = n.DatasophonPath + "/datasophon-init"
	n.initConfigPath = n.initPath + "/config"
	n.packagesPath = n.initPath + "/packages"
	n.initConfigYaml = n.initConfigPath + "/cluster-sample.yml"

	slog.Info("路径信息",
		"DATASOPHON_PATH", n.DatasophonPath,
		"INIT_PATH", n.initPath,
		"INIT_CONFIG_YAML", n.initConfigYaml)

	cfg, err := config.Load(n.initConfigYaml)
	if err != nil {
		return nil, err
	}
	n.sshAuthType = cfg.Global.SSHAuthType
	n.globalNodes = make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		n.globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}

	n.localIP = getLocalIP()
	n.localHost = &cfg.Nodes[0]
	for i := range cfg.Nodes {
		if cfg.Nodes[i].IP == n.localIP {
			n.localHost = &cfg.Nodes[i]
			slog.Info("本地节点", "host", n.localHost.Hostname)
			break
		}
	}

	return cfg, nil
}

// setupStandalone 独立模式:跳过 config.Load,直接用 CLI host 构造最小状态。
func (n *nodeInitializer) setupStandalone(host *config.Host) error {
	if !strings.HasPrefix(n.DatasophonPath, "/") || !strings.HasPrefix(n.InstallPath, "/") {
		return fmt.Errorf("datasophonPath、installPath 必须是绝对路径（以 / 开头）")
	}
	n.DatasophonPath = strings.TrimSuffix(n.DatasophonPath, "/")

	if _, err := os.Stat(n.DatasophonPath); err != nil {
		return fmt.Errorf("路径不存在: %s", n.DatasophonPath)
	}

	n.initPath = n.DatasophonPath + "/datasophon-init"
	n.initConfigPath = n.initPath + "/config"
	n.packagesPath = n.initPath + "/packages"
	n.initConfigYaml = n.initConfigPath + "/cluster-sample.yml"

	n.sshAuthType = config.SSHAuthTypePassword
	n.globalNodes = map[string]*config.Host{host.Hostname: host}
	n.localHost = nil
	return nil
}

// ─────────────────────────────────────────────────────────────────────────────
// initSingleNode 新增节点流程（对应 Java CreateCluster.initSingleNode()）
// ─────────────────────────────────────────────────────────────────────────────

func (n *nodeInitializer) initSingleNode(cfg *config.ClusterConfig) error {
	nodes := cfg.AddNodes
	if len(nodes) == 0 {
		slog.Warn("addNodes 列表为空，无需执行")
		return nil
	}
	allInitNodes := cfg.Nodes

	slog.Info("分发资源包")
	if err := n.doInitBinPackage(cfg, nodes); err != nil {
		return err
	}

	slog.Info("shell bash 设置")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitBash{}); err != nil {
		return err
	}

	slog.Info("安装 tar")
	if err := n.doInitTar(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 jdk8")
	if err := n.doInitJdk8(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 jdk17")
	if err := n.doInitJdk17(cfg, nodes); err != nil {
		return err
	}

	slog.Info("创建 hadoop 用户和组")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsUser{}); err != nil {
		return err
	}

	slog.Info("关闭防火墙")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitFirewall{}); err != nil {
		return err
	}

	slog.Info("关闭 selinux")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitSelinux{}); err != nil {
		return err
	}

	slog.Info("关闭 swap")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitSwap{}); err != nil {
		return err
	}

	slog.Info("离线 yum/apt 仓库配置")
	if err := n.doInitOfflineNodes(cfg, nodes); err != nil {
		return err
	}

	slog.Info("初始化依赖库")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitLibrary{}); err != nil {
		return err
	}

	slog.Info("安全配置")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsSafeConf{}); err != nil {
		return err
	}

	slog.Info("优化系统配置")
	if err := n.allNodesExec(hostsToPtr(nodes), &initcmd.InitSystemConf{}); err != nil {
		return err
	}

	slog.Info("配置 all hostname")
	if err := n.doInitHostname(nodes); err != nil {
		return err
	}

	slog.Info("配置 all hosts")
	// 先更新所有原有节点的 hosts（Java: initAllHost(config, initNodes)）
	if err := n.doInitAllHost(cfg, allInitNodes); err != nil {
		return err
	}
	// 再更新新节点的 hosts（Java: initAllHost(config, nodes)）
	if err := n.doInitAllHost(cfg, nodes); err != nil {
		return err
	}

	slog.Info("配置 ntpSlave")
	if err := n.doInitNtpSlave(cfg, nodes); err != nil {
		return err
	}

	slog.Info("关闭透明大页")
	return n.allNodesExec(hostsToPtr(nodes), &initcmd.InitHugePage{})
}

// ─────────────────────────────────────────────────────────────────────────────
// initStandaloneNode 独立模式 10 步节点级 DAG（不依赖集群上下文）
// ─────────────────────────────────────────────────────────────────────────────

func (n *nodeInitializer) initStandaloneNode(host *config.Host) error {
	nodes := []*config.Host{host}

	slog.Info("shell bash 设置")
	if err := n.allNodesExec(nodes, &initcmd.InitBash{}); err != nil {
		return err
	}

	slog.Info("创建 hadoop 用户和组")
	if err := n.allNodesExec(nodes, &initcmd.InitOsUser{}); err != nil {
		return err
	}

	slog.Info("关闭防火墙")
	if err := n.allNodesExec(nodes, &initcmd.InitFirewall{}); err != nil {
		return err
	}

	slog.Info("关闭 selinux")
	if err := n.allNodesExec(nodes, &initcmd.InitSelinux{}); err != nil {
		return err
	}

	slog.Info("关闭 swap")
	if err := n.allNodesExec(nodes, &initcmd.InitSwap{}); err != nil {
		return err
	}

	slog.Info("初始化依赖库")
	if err := n.allNodesExec(nodes, &initcmd.InitLibrary{}); err != nil {
		return err
	}

	slog.Info("安全配置")
	if err := n.allNodesExec(nodes, &initcmd.InitOsSafeConf{}); err != nil {
		return err
	}

	slog.Info("优化系统配置")
	if err := n.allNodesExec(nodes, &initcmd.InitSystemConf{}); err != nil {
		return err
	}

	slog.Info("配置 hostname")
	if err := n.singleNodeExec(host, &initcmd.InitHostname{Hostname: host.Hostname}); err != nil {
		return err
	}

	slog.Info("关闭透明大页")
	return n.allNodesExec(nodes, &initcmd.InitHugePage{})
}

// ─────────────────────────────────────────────────────────────────────────────
// 共用 init helper（initSingleNode 与 initALL 都用）
// ─────────────────────────────────────────────────────────────────────────────

func (n *nodeInitializer) doInitBinPackage(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitBinPackage{
		DatasophonInitPath:     n.initPath,
		InstallPath:            n.InstallPath,
		InitPathOverwriteForce: n.InitPathOverwriteForce,
	}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return n.allNodesExec(n.workerNodes(nodes), t)
}

func (n *nodeInitializer) doInitTar(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitTar{PackagePath: n.packagesPath}
	return n.allNodesExec(n.workerNodes(nodes), t)
}

func (n *nodeInitializer) doInitJdk8(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitJdk8{PackagePath: n.packagesPath}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return n.allNodesExec(n.workerNodes(nodes), t)
}

func (n *nodeInitializer) doInitJdk17(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitJdk17{PackagePath: n.packagesPath}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return n.allNodesExec(n.workerNodes(nodes), t)
}

func (n *nodeInitializer) doInitOfflineNodes(cfg *config.ClusterConfig, nodes []config.Host) error {
	ys := cfg.Global.YumServer
	reg := cfg.Global.Registry
	t := &initcmd.InitOfflineSlave{
		ServerIP:   ys.Node,
		ServerPort: ys.ListenPort,
	}
	applyConfig(&t.TaskBase, n.initConfigYaml)
	if reg.Enable {
		t.ServerIP = reg.Node
		t.ServerPort = reg.Config.WebPort
		applyRegistry(&t.TaskBase, &reg)
	}
	return n.allNodesExec(hostsToPtr(nodes), t)
}

func (n *nodeInitializer) doInitHostname(nodes []config.Host) error {
	for i := range nodes {
		t := &initcmd.InitHostname{Hostname: nodes[i].Hostname}
		if err := n.singleNodeExec(&nodes[i], t); err != nil {
			return err
		}
	}
	return nil
}

func (n *nodeInitializer) doInitAllHost(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitAllHost{}
	applyConfig(&t.TaskBase, n.initConfigYaml)
	for i := range nodes {
		if err := n.singleNodeExec(&nodes[i], t); err != nil {
			return err
		}
	}
	return nil
}

func (n *nodeInitializer) doInitNtpSlave(cfg *config.ClusterConfig, nodes []config.Host) error {
	ntp := cfg.Global.NtpServer
	serverNode, ok := n.globalNodes[ntp.Node]
	if !ok {
		return fmt.Errorf("ntpServer 节点 %q 不在 nodes 列表中", ntp.Node)
	}
	t := &initcmd.InitNtpSlave{NtpServerIP: serverNode.IP}
	applyConfig(&t.TaskBase, n.initConfigYaml)
	return n.slavesNodesExec(serverNode, hostsToPtr(nodes), t)
}

// ─────────────────────────────────────────────────────────────────────────────
// 执行辅助方法
// ─────────────────────────────────────────────────────────────────────────────

func (n *nodeInitializer) allNodesExec(nodes []*config.Host, h handler.Handler) error {
	for _, node := range nodes {
		if err := n.singleNodeExec(node, h); err != nil {
			return err
		}
	}
	return nil
}

func (n *nodeInitializer) singleNodeExec(node *config.Host, h handler.Handler) error {
	if node == nil {
		slog.Warn("节点为 nil，跳过", "handler", h.Name())
		return nil
	}
	slog.Info("在节点执行", "ip", node.IP, "hostname", node.Hostname)
	chain := handler.NewChain(node, n.sshAuthType, n.dryRun)
	chain.Add(h)
	return chain.Handle()
}

func (n *nodeInitializer) slavesNodesExec(serverNode *config.Host, allNodes []*config.Host, h handler.Handler) error {
	for _, node := range allNodes {
		if node.IP == serverNode.IP {
			continue
		}
		if err := n.singleNodeExec(node, h); err != nil {
			return err
		}
	}
	return nil
}

func (n *nodeInitializer) workerNodes(nodes []config.Host) []*config.Host {
	var result []*config.Host
	for i := range nodes {
		if nodes[i].IP != n.localIP {
			result = append(result, &nodes[i])
		}
	}
	return result
}

// ─────────────────────────────────────────────────────────────────────────────
// 包级工具函数
// ─────────────────────────────────────────────────────────────────────────────

func applyRegistry(tb *initcmd.TaskBase, registry *config.Registry) {
	if registry == nil || !registry.Enable {
		return
	}
	tb.EnableRegistry = true
	tb.RegistryIP = registry.Node
	tb.RegistryPort = registry.Config.WebPort
	tb.RegistryUsername = registry.Config.User
	tb.RegistryPassword = registry.Config.Password
}

func applyConfig(tb *initcmd.TaskBase, configFilePath string) {
	tb.ConfigFilePath = configFilePath
}

func hostsToPtr(hosts []config.Host) []*config.Host {
	result := make([]*config.Host, len(hosts))
	for i := range hosts {
		result[i] = &hosts[i]
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

// appendNodeToYAML 用 *yaml.Node 树操作追加节点到 cluster-sample.yml 的 nodes 序列,
// 保留原文件注释和格式。同 IP 或同 hostname 已存在时返回 (false, nil)。
func appendNodeToYAML(path string, host *config.Host) (bool, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return false, err
	}

	var root yaml.Node
	if err := yaml.Unmarshal(data, &root); err != nil {
		return false, err
	}
	if root.Kind != yaml.DocumentNode || len(root.Content) == 0 {
		return false, fmt.Errorf("cluster-sample.yml 不是合法 YAML 文档")
	}
	doc := root.Content[0]
	if doc.Kind != yaml.MappingNode {
		return false, fmt.Errorf("cluster-sample.yml 顶层不是 mapping")
	}

	var nodesSeq *yaml.Node
	for i := 0; i+1 < len(doc.Content); i += 2 {
		if doc.Content[i].Value == "nodes" {
			nodesSeq = doc.Content[i+1]
			break
		}
	}
	if nodesSeq == nil || nodesSeq.Kind != yaml.SequenceNode {
		return false, fmt.Errorf("cluster-sample.yml 找不到 nodes 序列")
	}

	for _, item := range nodesSeq.Content {
		if item.Kind != yaml.MappingNode {
			continue
		}
		var ip, hn string
		for j := 0; j+1 < len(item.Content); j += 2 {
			switch item.Content[j].Value {
			case "ip":
				ip = item.Content[j+1].Value
			case "hostname":
				hn = item.Content[j+1].Value
			}
		}
		if ip == host.IP || hn == host.Hostname {
			return false, nil
		}
	}

	newItem := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
	addKV := func(k, v string) {
		newItem.Content = append(newItem.Content,
			&yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: k},
			&yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: v},
		)
	}
	addKVInt := func(k string, v int) {
		newItem.Content = append(newItem.Content,
			&yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: k},
			&yaml.Node{Kind: yaml.ScalarNode, Tag: "!!int", Value: strconv.Itoa(v)},
		)
	}
	addKV("ip", host.IP)
	addKVInt("port", host.Port)
	addKV("user", host.User)
	addKV("password", host.Password)
	addKV("hostname", host.Hostname)
	nodesSeq.Content = append(nodesSeq.Content, newItem)

	out, err := yaml.Marshal(&root)
	if err != nil {
		return false, err
	}
	return true, os.WriteFile(path, out, 0644)
}
