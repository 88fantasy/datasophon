package create

import (
	"fmt"
	"log/slog"
	"net"
	"os"
	"strings"

	"github.com/spf13/cobra"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

type createClusterCmd struct {
	DatasophonPath  string
	InstallPath     string
	ProductPkgsPath string

	InitPathOverwriteForce bool
	DisableUploadRegistry  bool
	MysqlInstallForce      bool
	EnableRegistry         bool
	OnlyInstallK8s         bool
	KubernetesForce        bool

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
	cmd.Flags().StringVarP(&c.ProductPkgsPath, "productPackagesPath", "n", "", "安装包路径 (必填)")
	cmd.Flags().BoolVar(&c.InitPathOverwriteForce, "initPathOverwriteForce", false, "datasophon-init 目录是否覆盖")
	cmd.Flags().BoolVar(&c.DisableUploadRegistry, "disableUploadRegistry", false, "禁止上传制品")
	cmd.Flags().BoolVarP(&c.MysqlInstallForce, "mysqlInstallForce", "f", false, "MySQL 已存在是否覆盖安装")
	cmd.Flags().BoolVarP(&c.EnableRegistry, "enableRegistry", "e", false, "是否启动制品库")
	cmd.Flags().BoolVar(&c.OnlyInstallK8s, "onlyInstallK8s", false, "仅安装 K8s")
	cmd.Flags().BoolVar(&c.KubernetesForce, "kubernetesForce", false, "K8s 已存在是否覆盖安装")

	_ = cmd.MarkFlagRequired("datasophonPath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("productPackagesPath")

	return cmd
}

// setup 校验路径、推导运行时字段、加载集群配置，供 run() 和 createNodeCmd.run() 共用。
func (c *createClusterCmd) setup() (*config.ClusterConfig, error) {
	if !strings.HasPrefix(c.DatasophonPath, "/") || !strings.HasPrefix(c.InstallPath, "/") {
		return nil, fmt.Errorf("datasophonPath、installPath 必须是绝对路径（以 / 开头）")
	}
	c.DatasophonPath = strings.TrimSuffix(c.DatasophonPath, "/")

	if _, err := os.Stat(c.DatasophonPath); err != nil {
		return nil, fmt.Errorf("路径不存在: %s", c.DatasophonPath)
	}
	if _, err := os.Stat(c.InstallPath); err != nil {
		if mkErr := os.MkdirAll(c.InstallPath, 0755); mkErr != nil {
			return nil, fmt.Errorf("创建安装路径失败 %s: %w", c.InstallPath, mkErr)
		}
	}

	c.initPath = c.DatasophonPath + "/datasophon-init"
	c.initConfigPath = c.initPath + "/config"
	c.packagesPath = c.initPath + "/packages"
	c.initConfigYaml = c.initConfigPath + "/cluster-sample.yml"

	slog.Info("路径信息",
		"DATASOPHON_PATH", c.DatasophonPath,
		"INIT_PATH", c.initPath,
		"INIT_CONFIG_YAML", c.initConfigYaml)

	cfg, err := config.Load(c.initConfigYaml)
	if err != nil {
		return nil, err
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

	return cfg, nil
}

func (c *createClusterCmd) run() error {
	cfg, err := c.setup()
	if err != nil {
		return err
	}
	return c.initALL(cfg)
}

// ─────────────────────────────────────────────────────────────────────────────
// initALL 完整 28 步 DAG（对应 Java CreateCluster.initALL()）
// ─────────────────────────────────────────────────────────────────────────────

func (c *createClusterCmd) initALL(cfg *config.ClusterConfig) error {
	nodes := cfg.Nodes
	if len(nodes) == 0 {
		return nil
	}

	if c.OnlyInstallK8s {
		slog.Info("仅安装 K8s 集群")
		return c.initK8s(cfg)
	}

	slog.Info("分发资源包")
	if err := c.doInitBinPackage(cfg, nodes); err != nil {
		return err
	}

	slog.Info("shell bash 设置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitBash{}); err != nil {
		return err
	}

	slog.Info("安装 tar")
	if err := c.doInitTar(cfg, nodes); err != nil {
		return err
	}

	if c.EnableRegistry {
		slog.Info("安装 rustfs")
		if err := c.doInitRustfs(cfg); err != nil {
			return err
		}

		slog.Info("安装 registry")
		if err := c.doInitRegistry(cfg); err != nil {
			return err
		}

		if cfg.Global.Kubernetes.Enable {
			if err := c.doInitDocker(cfg); err != nil {
				return err
			}
		}

		slog.Info("安装 registryUpload")
		if err := c.doInitRegistryUpload(cfg); err != nil {
			return err
		}
	}

	slog.Info("安装 jdk8")
	if err := c.doInitJdk8(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 jdk17")
	if err := c.doInitJdk17(cfg, nodes); err != nil {
		return err
	}

	slog.Info("创建 hadoop 用户和组")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsUser{}); err != nil {
		return err
	}

	slog.Info("关闭防火墙")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitFirewall{}); err != nil {
		return err
	}

	slog.Info("关闭 selinux")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSelinux{}); err != nil {
		return err
	}

	slog.Info("关闭 swap")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSwap{}); err != nil {
		return err
	}

	slog.Info("yum/apt 离线源服务配置")
	if err := c.doInitOfflineServer(cfg); err != nil {
		return err
	}

	slog.Info("yum/apt 离线源节点配置")
	if err := c.doInitOfflineNodes(cfg, nodes); err != nil {
		return err
	}

	slog.Info("初始化依赖库")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitLibrary{}); err != nil {
		return err
	}

	slog.Info("安全配置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsSafeConf{}); err != nil {
		return err
	}

	slog.Info("优化系统配置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSystemConf{}); err != nil {
		return err
	}

	slog.Info("配置 all hostname")
	if err := c.doInitHostname(nodes); err != nil {
		return err
	}

	slog.Info("配置 all hosts")
	if err := c.doInitAllHost(cfg, nodes); err != nil {
		return err
	}

	slog.Info("nmap 安装")
	if err := c.doInitNmap(cfg); err != nil {
		return err
	}

	slog.Info("配置 ntpServer")
	if err := c.doInitNtpServer(cfg); err != nil {
		return err
	}

	slog.Info("配置 ntpSlave")
	if err := c.doInitNtpSlave(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 mysql")
	if err := c.doInitMysql(cfg); err != nil {
		return err
	}

	slog.Info("初始化 mysql 数据库和账号密码")
	if err := c.doInitMysqlAppDb(cfg); err != nil {
		return err
	}

	if cfg.Global.Kubernetes.Enable {
		slog.Info("安装 K8s 集群")
		if err := c.initK8s(cfg); err != nil {
			return err
		}
	}

	slog.Info("关闭透明大页")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitHugePage{}); err != nil {
		return err
	}

	return nil
}

// ─────────────────────────────────────────────────────────────────────────────
// initSingleNode 新增节点流程（对应 Java CreateCluster.initSingleNode()）
// ─────────────────────────────────────────────────────────────────────────────

func (c *createClusterCmd) initSingleNode(cfg *config.ClusterConfig) error {
	nodes := cfg.AddNodes
	if len(nodes) == 0 {
		slog.Warn("addNodes 列表为空，无需执行")
		return nil
	}
	allInitNodes := cfg.Nodes

	slog.Info("分发资源包")
	if err := c.doInitBinPackage(cfg, nodes); err != nil {
		return err
	}

	slog.Info("shell bash 设置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitBash{}); err != nil {
		return err
	}

	slog.Info("安装 tar")
	if err := c.doInitTar(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 jdk8")
	if err := c.doInitJdk8(cfg, nodes); err != nil {
		return err
	}

	slog.Info("安装 jdk17")
	if err := c.doInitJdk17(cfg, nodes); err != nil {
		return err
	}

	slog.Info("创建 hadoop 用户和组")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsUser{}); err != nil {
		return err
	}

	slog.Info("关闭防火墙")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitFirewall{}); err != nil {
		return err
	}

	slog.Info("关闭 selinux")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSelinux{}); err != nil {
		return err
	}

	slog.Info("关闭 swap")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSwap{}); err != nil {
		return err
	}

	slog.Info("离线 yum/apt 仓库配置")
	if err := c.doInitOfflineNodes(cfg, nodes); err != nil {
		return err
	}

	slog.Info("初始化依赖库")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitLibrary{}); err != nil {
		return err
	}

	slog.Info("安全配置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitOsSafeConf{}); err != nil {
		return err
	}

	slog.Info("优化系统配置")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitSystemConf{}); err != nil {
		return err
	}

	slog.Info("配置 all hostname")
	if err := c.doInitHostname(nodes); err != nil {
		return err
	}

	slog.Info("配置 all hosts")
	// 先更新所有原有节点的 hosts（Java: initAllHost(config, initNodes)）
	if err := c.doInitAllHost(cfg, allInitNodes); err != nil {
		return err
	}
	// 再更新新节点的 hosts（Java: initAllHost(config, nodes)）
	if err := c.doInitAllHost(cfg, nodes); err != nil {
		return err
	}

	slog.Info("配置 ntpSlave")
	if err := c.doInitNtpSlave(cfg, nodes); err != nil {
		return err
	}

	slog.Info("关闭透明大页")
	if err := c.allNodesExec(hostsToPtr(nodes), &initcmd.InitHugePage{}); err != nil {
		return err
	}

	return nil
}

// ─────────────────────────────────────────────────────────────────────────────
// initK8s 子 DAG（对应 Java CreateCluster.initK8s()）
// ─────────────────────────────────────────────────────────────────────────────

func (c *createClusterCmd) initK8s(cfg *config.ClusterConfig) error {
	if err := c.doInitK8sBaseServices(cfg); err != nil {
		return err
	}
	if err := c.doInitK8sKuboard(cfg); err != nil {
		return err
	}
	if err := c.doInitK8sRegistryConf(cfg); err != nil {
		return err
	}
	if err := c.doInitDocker(cfg); err != nil {
		return err
	}
	if err := c.doInitKubectl(cfg); err != nil {
		return err
	}
	if err := c.doInitHelm(cfg); err != nil {
		return err
	}
	return c.doInitHelmify(cfg)
}

// ─────────────────────────────────────────────────────────────────────────────
// 私有 init 方法（对应 Java 的 private void initXxx(ClusterConfig, [List<Host>]) 方法）
// ─────────────────────────────────────────────────────────────────────────────

func (c *createClusterCmd) doInitBinPackage(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitBinPackage{
		DatasophonInitPath:     c.initPath,
		InstallPath:            c.InstallPath,
		InitPathOverwriteForce: c.InitPathOverwriteForce,
	}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	// 只对 worker 节点（非本地）执行
	return c.allNodesExec(c.workerNodes(nodes), t)
}

func (c *createClusterCmd) doInitTar(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitTar{PackagePath: c.packagesPath}
	return c.allNodesExec(c.workerNodes(nodes), t)
}

func (c *createClusterCmd) doInitRustfs(cfg *config.ClusterConfig) error {
	rs := cfg.Global.Rustfs
	if len(rs.Nodes) == 0 {
		slog.Warn("rustfs.nodes 为空，跳过")
		return nil
	}
	t := &initcmd.InitRustfs{
		Enable:      rs.Enable,
		PackagePath: c.packagesPath,
		InstallPath: c.InstallPath,
		X86Tar:      cfg.Global.Packages.Rustfs.X86_64,
		Aarch64Tar:  cfg.Global.Packages.Rustfs.Aarch64,
		WebHost:     rs.Nodes[0],
		WebPort:     rs.Config.WebPort,
		APIPort:     rs.Config.APIPort,
		Username:    rs.Config.User,
		Password:    rs.Config.Password,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	return c.singleNodeExec(c.globalNodes[rs.Nodes[0]], t)
}

func (c *createClusterCmd) doInitRegistry(cfg *config.ClusterConfig) error {
	reg := cfg.Global.Registry
	t := &initcmd.InitRegistry{
		PackagePath:    c.packagesPath,
		InstallPath:    c.InstallPath,
		Repositories:   reg.Config.Repositories,
		X86Tar:         cfg.Global.Packages.Nexus.X86_64,
		Aarch64Tar:     cfg.Global.Packages.Nexus.Aarch64,
		WebHost:        reg.Node,
		WebPort:        reg.Config.WebPort,
		Username:       reg.Config.User,
		Password:       reg.Config.Password,
		DockerHTTPPort: reg.Config.DockerHTTPPort,
	}
	applyRegistry(&t.TaskBase, &reg)
	node, ok := c.globalNodes[reg.Node]
	if !ok {
		return fmt.Errorf("registry 节点 %q 不在 nodes 列表中", reg.Node)
	}
	return c.singleNodeExec(node, t)
}

func (c *createClusterCmd) doInitRegistryUpload(cfg *config.ClusterConfig) error {
	reg := cfg.Global.Registry
	t := &initcmd.InitRegistryUpload{
		ProductPackagesPath:   c.ProductPkgsPath,
		WebHost:               reg.Node,
		WebPort:               reg.Config.WebPort,
		Username:              reg.Config.User,
		Password:              reg.Config.Password,
		DisableUploadRegistry: c.DisableUploadRegistry,
		DockerHTTPPort:        reg.Config.DockerHTTPPort,
	}
	applyRegistry(&t.TaskBase, &reg)
	return c.singleNodeExec(c.localHost, t)
}

func (c *createClusterCmd) doInitJdk8(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitJdk8{PackagePath: c.packagesPath}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.allNodesExec(c.workerNodes(nodes), t)
}

func (c *createClusterCmd) doInitJdk17(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitJdk17{PackagePath: c.packagesPath}
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.allNodesExec(c.workerNodes(nodes), t)
}

func (c *createClusterCmd) doInitOfflineServer(cfg *config.ClusterConfig) error {
	ys := cfg.Global.YumServer
	t := &initcmd.InitOfflineServer{
		PackagePath: c.packagesPath,
		ServerIP:    ys.Node,
		ServerPort:  ys.ListenPort,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	node, ok := c.globalNodes[ys.Node]
	if !ok {
		return fmt.Errorf("yumServer 节点 %q 不在 nodes 列表中", ys.Node)
	}
	return c.singleNodeExec(node, t)
}

func (c *createClusterCmd) doInitOfflineNodes(cfg *config.ClusterConfig, nodes []config.Host) error {
	ys := cfg.Global.YumServer
	reg := cfg.Global.Registry
	t := &initcmd.InitOfflineSlave{
		ServerIP:   ys.Node,
		ServerPort: ys.ListenPort,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	if reg.Enable {
		// Registry 启用时使用 registry 地址覆盖 yumServer 地址（对应 Java 逻辑）
		t.ServerIP = reg.Node
		t.ServerPort = reg.Config.WebPort
		applyRegistry(&t.TaskBase, &reg)
	}
	return c.allNodesExec(hostsToPtr(nodes), t)
}

func (c *createClusterCmd) doInitHostname(nodes []config.Host) error {
	for i := range nodes {
		t := &initcmd.InitHostname{Hostname: nodes[i].Hostname}
		if err := c.singleNodeExec(&nodes[i], t); err != nil {
			return err
		}
	}
	return nil
}

func (c *createClusterCmd) doInitAllHost(cfg *config.ClusterConfig, nodes []config.Host) error {
	t := &initcmd.InitAllHost{}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	for i := range nodes {
		if err := c.singleNodeExec(&nodes[i], t); err != nil {
			return err
		}
	}
	return nil
}

func (c *createClusterCmd) doInitNmap(cfg *config.ClusterConfig) error {
	nm := cfg.Global.NmapServer
	node, ok := c.globalNodes[nm.Node]
	if !ok {
		return fmt.Errorf("nmapServer 节点 %q 不在 nodes 列表中", nm.Node)
	}
	return c.singleNodeExec(node, &initcmd.InitNmap{})
}

func (c *createClusterCmd) doInitNtpServer(cfg *config.ClusterConfig) error {
	ntp := cfg.Global.NtpServer
	t := &initcmd.InitNtpServer{}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	node, ok := c.globalNodes[ntp.Node]
	if !ok {
		return fmt.Errorf("ntpServer 节点 %q 不在 nodes 列表中", ntp.Node)
	}
	return c.singleNodeExec(node, t)
}

func (c *createClusterCmd) doInitNtpSlave(cfg *config.ClusterConfig, nodes []config.Host) error {
	ntp := cfg.Global.NtpServer
	serverNode, ok := c.globalNodes[ntp.Node]
	if !ok {
		return fmt.Errorf("ntpServer 节点 %q 不在 nodes 列表中", ntp.Node)
	}
	t := &initcmd.InitNtpSlave{NtpServerIP: serverNode.IP}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	return c.slavesNodesExec(serverNode, hostsToPtr(nodes), t)
}

func (c *createClusterCmd) doInitMysql(cfg *config.ClusterConfig) error {
	mc := cfg.Global.Mysql
	t := &initcmd.InitMysql{
		Password:    mc.Password,
		Force:       c.MysqlInstallForce,
		PackagePath: c.packagesPath,
		InstallPath: c.InstallPath,
		X86Tar:      cfg.Global.Packages.Mysql.X86_64,
		Aarch64Tar:  cfg.Global.Packages.Mysql.Aarch64,
		Port:        mc.Port,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	node, ok := c.globalNodes[mc.Node]
	if !ok {
		return fmt.Errorf("mysql 节点 %q 不在 nodes 列表中", mc.Node)
	}
	return c.singleNodeExec(node, t)
}

func (c *createClusterCmd) doInitMysqlAppDb(cfg *config.ClusterConfig) error {
	mc := cfg.Global.Mysql
	node, ok := c.globalNodes[mc.Node]
	if !ok {
		return fmt.Errorf("mysql 节点 %q 不在 nodes 列表中", mc.Node)
	}
	for _, appDb := range mc.AppDbs {
		t := &initcmd.InitMysqlAppDb{
			RootPassword: mc.Password,
			Account:      appDb.Account,
			Password:     appDb.Password,
			DBName:       appDb.DbName,
			Port:         mc.Port,
		}
		applyConfig(&t.TaskBase, c.initConfigYaml)
		if err := c.singleNodeExec(node, t); err != nil {
			return err
		}
	}
	return nil
}

func (c *createClusterCmd) doInitK8sBaseServices(cfg *config.ClusterConfig) error {
	k8s := cfg.Global.Kubernetes
	bs := k8s.BaseServices
	if len(bs.Masters) == 0 {
		slog.Warn("kubernetes.baseServices.masters 为空，跳过 K8s 基础服务安装")
		return nil
	}

	// 将 hostname 解析为 IP（对应 Java stream map x -> globalNodes.get(x).getIp()）
	masters := make([]string, 0, len(bs.Masters))
	for _, h := range bs.Masters {
		node, ok := c.globalNodes[h]
		if !ok {
			return fmt.Errorf("master 节点 %q 不在 nodes 列表中", h)
		}
		masters = append(masters, node.IP)
	}
	k8snodes := make([]string, 0, len(bs.Nodes))
	for _, h := range bs.Nodes {
		node, ok := c.globalNodes[h]
		if !ok {
			return fmt.Errorf("k8s node %q 不在 nodes 列表中", h)
		}
		k8snodes = append(k8snodes, node.IP)
	}

	t := &initcmd.InitK8sBaseServices{
		EnableK8sCluster: k8s.Enable,
		KubernetesForce:  c.KubernetesForce,
		Namespaces:       bs.Namespaces,
		Masters:          masters,
		Nodes:            k8snodes,
		Sealos:           bs.Sealos,
		SealosX86Tar:     cfg.Global.Packages.Sealos.X86_64,
		SealosArmTar:     cfg.Global.Packages.Sealos.Aarch64,
		Kubernetes:       bs.KubernetesI,
		KubernetesX86Tar: cfg.Global.Packages.KubernetesI.X86_64,
		KubernetesArmTar: cfg.Global.Packages.KubernetesI.Aarch64,
		Helm:             bs.HelmI,
		HelmX86Tar:       cfg.Global.Packages.HelmI.X86_64,
		HelmArmTar:       cfg.Global.Packages.HelmI.Aarch64,
		Calico:           bs.CalicoI,
		CalicoX86Tar:     cfg.Global.Packages.CalicoI.X86_64,
		CalicoArmTar:     cfg.Global.Packages.CalicoI.Aarch64,
		Ingress:          bs.IngressI,
		IngressX86Tar:    cfg.Global.Packages.IngressI.X86_64,
		IngressArmTar:    cfg.Global.Packages.IngressI.Aarch64,
		PackagePath:      c.packagesPath,
		SSHPort:          c.localHost.Port,
		SSHPasswd:        c.localHost.Password,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)

	masterNode, ok := c.globalNodes[bs.Masters[0]]
	if !ok {
		return fmt.Errorf("第一个 master 节点 %q 不在 nodes 列表中", bs.Masters[0])
	}
	return c.singleNodeExec(masterNode, t)
}

func (c *createClusterCmd) doInitK8sKuboard(cfg *config.ClusterConfig) error {
	kb := cfg.Global.Kubernetes.KuboardI
	t := &initcmd.InitK8sKuboard{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		KuboardX86Tar:    cfg.Global.Packages.KuboardI.X86_64,
		KuboardArmTar:    cfg.Global.Packages.KuboardI.Aarch64,
		PackagePath:      c.packagesPath,
		Etcds:            kb.EtcdNodes,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	node, ok := c.globalNodes[kb.Node]
	if !ok {
		return fmt.Errorf("kuboard 节点 %q 不在 nodes 列表中", kb.Node)
	}
	return c.singleNodeExec(node, t)
}

func (c *createClusterCmd) doInitK8sRegistryConf(cfg *config.ClusterConfig) error {
	bs := cfg.Global.Kubernetes.BaseServices
	t := &initcmd.InitK8sRegistryConf{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		DockerHTTPPort:   cfg.Global.Registry.Config.DockerHTTPPort,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)

	// 所有 master + node 节点
	var k8sNodes []*config.Host
	for _, h := range bs.Masters {
		if node, ok := c.globalNodes[h]; ok {
			k8sNodes = append(k8sNodes, node)
		}
	}
	for _, h := range bs.Nodes {
		if node, ok := c.globalNodes[h]; ok {
			k8sNodes = append(k8sNodes, node)
		}
	}
	return c.allNodesExec(k8sNodes, t)
}

func (c *createClusterCmd) doInitDocker(cfg *config.ClusterConfig) error {
	t := &initcmd.InitDocker{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		PackagePath:      c.packagesPath,
		InstallPath:      c.InstallPath,
		X86Tar:           cfg.Global.Packages.Docker.X86_64,
		Aarch64Tar:       cfg.Global.Packages.Docker.Aarch64,
		DockerHTTPPort:   cfg.Global.Registry.Config.DockerHTTPPort,
		KubernetesForce:  c.KubernetesForce,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.singleNodeExec(c.localHost, t)
}

func (c *createClusterCmd) doInitKubectl(cfg *config.ClusterConfig) error {
	t := &initcmd.InitKubectl{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		PackagePath:      c.packagesPath,
		InstallPath:      c.InstallPath,
		X86Tar:           cfg.Global.Packages.Kubectl.X86_64,
		Aarch64Tar:       cfg.Global.Packages.Kubectl.Aarch64,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.singleNodeExec(c.localHost, t)
}

func (c *createClusterCmd) doInitHelm(cfg *config.ClusterConfig) error {
	t := &initcmd.InitHelm{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		PackagePath:      c.packagesPath,
		InstallPath:      c.InstallPath,
		X86Tar:           cfg.Global.Packages.Helm.X86_64,
		Aarch64Tar:       cfg.Global.Packages.Helm.Aarch64,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.singleNodeExec(c.localHost, t)
}

func (c *createClusterCmd) doInitHelmify(cfg *config.ClusterConfig) error {
	t := &initcmd.InitHelmify{
		EnableK8sCluster: cfg.Global.Kubernetes.Enable,
		PackagePath:      c.packagesPath,
		InstallPath:      c.InstallPath,
		X86Tar:           cfg.Global.Packages.Helmify.X86_64,
		Aarch64Tar:       cfg.Global.Packages.Helmify.Aarch64,
	}
	applyConfig(&t.TaskBase, c.initConfigYaml)
	applyRegistry(&t.TaskBase, &cfg.Global.Registry)
	return c.singleNodeExec(c.localHost, t)
}

// ─────────────────────────────────────────────────────────────────────────────
// 执行辅助方法
// ─────────────────────────────────────────────────────────────────────────────

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
	if node == nil {
		slog.Warn("节点为 nil，跳过", "handler", h.Name())
		return nil
	}
	slog.Info("在节点执行", "ip", node.IP, "hostname", node.Hostname)
	chain := handler.NewChain(node, c.sshAuthType, c.dryRun)
	chain.Add(h)
	return chain.Handle()
}

// slavesNodesExec 对应 Java CreateCluster.slavesNodesExec —— 过滤掉 serverNode。
func (c *createClusterCmd) slavesNodesExec(serverNode *config.Host, allNodes []*config.Host, h handler.Handler) error {
	for _, node := range allNodes {
		if node.IP == serverNode.IP {
			continue
		}
		if err := c.singleNodeExec(node, h); err != nil {
			return err
		}
	}
	return nil
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

// ─────────────────────────────────────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────────────────────────────────────

// applyRegistry 当 registry.Enable 时，向 TaskBase 填充制品库字段。
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

// applyConfig 向 TaskBase 填充配置文件路径。
func applyConfig(tb *initcmd.TaskBase, configFilePath string) {
	tb.ConfigFilePath = configFilePath
}

// hostsToPtr 将 []config.Host 转为 []*config.Host（避免复制）。
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
