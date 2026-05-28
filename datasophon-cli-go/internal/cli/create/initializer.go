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
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/plan"
)

// nodeInitializer 持有集群模式和独立模式共用的状态与 helper。
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
	currentCfg     *config.ClusterConfig // setup() 后赋值
}

func (n *nodeInitializer) bindCommonFlags(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&n.DatasophonPath, "datasophonPath", "p", "", "datasophon 绝对路径 (必填)")
	cmd.Flags().StringVar(&n.InstallPath, "installPath", "", "安装路径 (必填)")
	cmd.Flags().StringVarP(&n.ProductPkgsPath, "productPackagesPath", "n", "", "安装包路径 (必填)")
	cmd.Flags().BoolVar(&n.InitPathOverwriteForce, "initPathOverwriteForce", false, "datasophon-init 目录是否覆盖")
	_ = cmd.MarkFlagRequired("datasophonPath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("productPackagesPath")
}

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

	if len(cfg.Nodes) == 0 {
		return nil, fmt.Errorf("cluster-sample.yml 中 nodes 列表不能为空")
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

	n.currentCfg = cfg
	return cfg, nil
}

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

// toBuildContext 组装 plan.BuildContext。
func (n *nodeInitializer) toBuildContext() *plan.BuildContext {
	return &plan.BuildContext{
		Cfg:                    n.currentCfg,
		InitPath:               n.initPath,
		PackagesPath:           n.packagesPath,
		InstallPath:            n.InstallPath,
		ProductPkgsPath:        n.ProductPkgsPath,
		ConfigYaml:             n.initConfigYaml,
		LocalHost:              n.localHost,
		LocalIP:                n.localIP,
		GlobalNodes:            n.globalNodes,
		SSHAuthType:            n.sshAuthType,
		InitPathOverwriteForce: n.InitPathOverwriteForce,
		DryRun:                 n.dryRun,
	}
}

// initSingleNode 新增节点：生成 plan → 执行。
func (n *nodeInitializer) initSingleNode(cfg *config.ClusterConfig) error {
	if len(cfg.AddNodes) == 0 {
		slog.Warn("addNodes 列表为空，无需执行")
		return nil
	}
	ctx := n.toBuildContext()
	pf, err := plan.GeneratePlan("initSingleNode", plan.InitSingleNodeRegistry, ctx)
	if err != nil {
		return fmt.Errorf("生成 initSingleNode 计划失败: %w", err)
	}
	if err := plan.Save(n.initPath, pf); err != nil {
		return err
	}
	plan.PrintSummary(pf)
	return plan.Apply(n.initPath, "initSingleNode", plan.InitSingleNodeRegistry, ctx)
}

// initStandaloneNode 独立模式 10 步节点级 DAG（不依赖集群上下文，不走 plan 引擎）。
func (n *nodeInitializer) initStandaloneNode(host *config.Host) error {
	steps := []struct {
		name string
		fn   func() error
	}{
		{"shell bash 设置", func() error { return n.singleNodeExecDirect(host, &initcmd.InitBash{}) }},
		{"创建 hadoop 用户和组", func() error { return n.singleNodeExecDirect(host, &initcmd.InitOsUser{}) }},
		{"关闭防火墙", func() error { return n.singleNodeExecDirect(host, &initcmd.InitFirewall{}) }},
		{"关闭 selinux", func() error { return n.singleNodeExecDirect(host, &initcmd.InitSelinux{}) }},
		{"关闭 swap", func() error { return n.singleNodeExecDirect(host, &initcmd.InitSwap{}) }},
		{"初始化依赖库", func() error { return n.singleNodeExecDirect(host, &initcmd.InitLibrary{}) }},
		{"安全配置", func() error { return n.singleNodeExecDirect(host, &initcmd.InitOsSafeConf{}) }},
		{"优化系统配置", func() error { return n.singleNodeExecDirect(host, &initcmd.InitSystemConf{}) }},
		{"配置 hostname", func() error {
			return n.singleNodeExecDirect(host, &initcmd.InitHostname{Hostname: host.Hostname})
		}},
		{"关闭透明大页", func() error { return n.singleNodeExecDirect(host, &initcmd.InitHugePage{}) }},
	}

	for _, s := range steps {
		slog.Info(s.name)
		if err := s.fn(); err != nil {
			return err
		}
	}
	return nil
}

// singleNodeExecDirect 为独立模式直接建 chain 执行（不经过 plan 引擎）。
func (n *nodeInitializer) singleNodeExecDirect(node *config.Host, h handler.Handler) error {
	if node == nil {
		slog.Warn("节点为 nil，跳过", "handler", h.Name())
		return nil
	}
	slog.Info("在节点执行", "ip", node.IP, "hostname", node.Hostname)
	ch := handler.NewChain(node, n.sshAuthType, n.dryRun)
	ch.Add(h)
	return ch.Handle()
}

// ─── helpers ─────────────────────────────────────────────────────────────────

func getLocalIP() string {
	interfaces, err := net.Interfaces()
	if err != nil {
		return ""
	}

	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		if iface.Flags&net.FlagLoopback != 0 {
			continue
		}

		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}

		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}

			if ip == nil || ip.IsLoopback() {
				continue
			}

			ip = ip.To4()
			if ip != nil {
				return ip.String()
			}
		}
	}

	return ""
}

// appendNodeToYAML 追加节点到 cluster-sample.yml（保持注释格式）。
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