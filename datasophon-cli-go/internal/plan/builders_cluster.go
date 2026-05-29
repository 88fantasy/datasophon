package plan

import (
	"fmt"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/upload"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// buildRustfs 安装 rustfs（单节点）。
func buildRustfs(ctx *BuildContext) ([]Action, error) {
	rs := ctx.Cfg.Global.Rustfs
	if len(rs.Nodes) == 0 {
		return nil, nil
	}
	t := &initcmd.InitRustfs{
		Enable:      rs.Enable,
		PackagePath: ctx.PackagesPath,
		InstallPath: ctx.InstallPath,
		X86Tar:      ctx.Cfg.Global.Packages.Rustfs.X86_64,
		Aarch64Tar:  ctx.Cfg.Global.Packages.Rustfs.Aarch64,
		WebHost:     rs.Nodes[0],
		WebPort:     rs.Config.WebPort,
		APIPort:     rs.Config.APIPort,
		Username:    rs.Config.User,
		Password:    rs.Config.Password,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	node, err := requireNode(ctx.GlobalNodes, rs.Nodes[0])
	if err != nil {
		return nil, fmt.Errorf("rustfs 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildRegistry 安装 Nexus Registry（单节点）。
func buildRegistry(ctx *BuildContext) ([]Action, error) {
	reg := ctx.Cfg.Global.Registry
	t := &initcmd.InitRegistry{
		PackagePath:    ctx.PackagesPath,
		InstallPath:    ctx.InstallPath,
		Repositories:   reg.Config.Repositories,
		X86Tar:         ctx.Cfg.Global.Packages.Nexus.X86_64,
		Aarch64Tar:     ctx.Cfg.Global.Packages.Nexus.Aarch64,
		WebHost:        reg.Node,
		WebPort:        reg.Config.WebPort,
		Username:       reg.Config.User,
		Password:       reg.Config.Password,
		DockerHTTPPort: reg.Config.DockerHTTPPort,
	}
	applyRegistry(&t.TaskBase, &reg)
	node, err := requireNode(ctx.GlobalNodes, reg.Node)
	if err != nil {
		return nil, fmt.Errorf("registry 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildRegistryUpload 上传安装包到 Registry（本地节点执行）。
func buildRegistryUpload(ctx *BuildContext) ([]Action, error) {
	reg := ctx.Cfg.Global.Registry
	t := &upload.UploadRegistry{
		ProductPackagesPath:   ctx.ProductPkgsPath,
		WebHost:               reg.Node,
		WebPort:               reg.Config.WebPort,
		Username:              reg.Config.User,
		Password:              reg.Config.Password,
		DisableUploadRegistry: reg.DisableUpload,
		DockerHTTPPort:        reg.Config.DockerHTTPPort,
	}
	applyRegistry(&t.TaskBase, &reg)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildDocker 安装 Docker（本地节点执行）。
func buildDocker(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitDocker{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Global.Packages.Docker.X86_64,
		Aarch64Tar:       ctx.Cfg.Global.Packages.Docker.Aarch64,
		DockerHTTPPort:   ctx.Cfg.Global.Registry.Config.DockerHTTPPort,
		KubernetesForce:  ctx.Cfg.Global.Kubernetes.Force,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildOfflineServer 配置 yum/apt 离线源服务端（单节点）。
func buildOfflineServer(ctx *BuildContext) ([]Action, error) {
	ys := ctx.Cfg.Global.YumServer
	t := &initcmd.InitOfflineServer{
		PackagePath: ctx.PackagesPath,
		ServerIP:    ys.Node,
		ServerPort:  ys.ListenPort,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	node, err := requireNode(ctx.GlobalNodes, ys.Node)
	if err != nil {
		return nil, fmt.Errorf("yumServer 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildNmap 安装 nmap（单节点）。
func buildNmap(ctx *BuildContext) ([]Action, error) {
	nm := ctx.Cfg.Global.NmapServer
	node, err := requireNode(ctx.GlobalNodes, nm.Node)
	if err != nil {
		return nil, fmt.Errorf("nmapServer 节点: %w", err)
	}
	return singleHostAction(node, &initcmd.InitNmap{}), nil
}

// buildNtpServer 配置 NTP Server（单节点）。
func buildNtpServer(ctx *BuildContext) ([]Action, error) {
	ntp := ctx.Cfg.Global.NtpServer
	t := &initcmd.InitNtpServer{}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	node, err := requireNode(ctx.GlobalNodes, ntp.Node)
	if err != nil {
		return nil, fmt.Errorf("ntpServer 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildMysql 安装 MySQL（单节点）。
func buildMysql(ctx *BuildContext) ([]Action, error) {
	mc := ctx.Cfg.Global.Mysql
	t := &initcmd.InitMysql{
		Password:    mc.Password,
		Force:       mc.Force,
		PackagePath: ctx.PackagesPath,
		InstallPath: ctx.InstallPath,
		X86Tar:      ctx.Cfg.Global.Packages.Mysql.X86_64,
		Aarch64Tar:  ctx.Cfg.Global.Packages.Mysql.Aarch64,
		Port:        mc.Port,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	node, err := requireNode(ctx.GlobalNodes, mc.Node)
	if err != nil {
		return nil, fmt.Errorf("mysql 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildMysqlAppDb 初始化 MySQL 数据库账号（多个 AppDb 每个一个 action）。
func buildMysqlAppDb(ctx *BuildContext) ([]Action, error) {
	mc := ctx.Cfg.Global.Mysql
	node, err := requireNode(ctx.GlobalNodes, mc.Node)
	if err != nil {
		return nil, fmt.Errorf("mysql 节点: %w", err)
	}
	actions := make([]Action, 0, len(mc.AppDbs))
	for _, appDb := range mc.AppDbs {
		t := &initcmd.InitMysqlAppDb{
			RootPassword: mc.Password,
			Account:      appDb.Account,
			Password:     appDb.Password,
			DBName:       appDb.DbName,
			Port:         mc.Port,
		}
		applyConfig(&t.TaskBase, ctx.ConfigYaml)
		actions = append(actions, Action{HostKey: node.Hostname, Host: node, Handler: t})
	}
	return actions, nil
}

// buildK8sBaseServices 安装 K8s 基础服务（master 节点）。
func buildK8sBaseServices(ctx *BuildContext) ([]Action, error) {
	k8s := ctx.Cfg.Global.Kubernetes
	bs := k8s.BaseServices
	if len(bs.Masters) == 0 {
		return nil, nil
	}
	masters := make([]string, 0, len(bs.Masters))
	for _, h := range bs.Masters {
		node, err := requireNode(ctx.GlobalNodes, h)
		if err != nil {
			return nil, fmt.Errorf("k8s master 节点: %w", err)
		}
		masters = append(masters, node.IP)
	}
	k8snodes := make([]string, 0, len(bs.Nodes))
	for _, h := range bs.Nodes {
		node, err := requireNode(ctx.GlobalNodes, h)
		if err != nil {
			return nil, fmt.Errorf("k8s node: %w", err)
		}
		k8snodes = append(k8snodes, node.IP)
	}
	t := &initcmd.InitK8sBaseServices{
		EnableK8sCluster: k8s.Enable,
		KubernetesForce:  k8s.Force,
		Namespaces:       bs.Namespaces,
		Masters:          masters,
		Nodes:            k8snodes,
		Sealos:           bs.Sealos,
		SealosX86Tar:     ctx.Cfg.Global.Packages.Sealos.X86_64,
		SealosArmTar:     ctx.Cfg.Global.Packages.Sealos.Aarch64,
		Kubernetes:       bs.KubernetesI,
		KubernetesX86Tar: ctx.Cfg.Global.Packages.KubernetesI.X86_64,
		KubernetesArmTar: ctx.Cfg.Global.Packages.KubernetesI.Aarch64,
		Helm:             bs.HelmI,
		HelmX86Tar:       ctx.Cfg.Global.Packages.HelmI.X86_64,
		HelmArmTar:       ctx.Cfg.Global.Packages.HelmI.Aarch64,
		Calico:           bs.CalicoI,
		CalicoX86Tar:     ctx.Cfg.Global.Packages.CalicoI.X86_64,
		CalicoArmTar:     ctx.Cfg.Global.Packages.CalicoI.Aarch64,
		Ingress:          bs.IngressI,
		IngressX86Tar:    ctx.Cfg.Global.Packages.IngressI.X86_64,
		IngressArmTar:    ctx.Cfg.Global.Packages.IngressI.Aarch64,
		PackagePath:      ctx.PackagesPath,
		SSHPort:          ctx.LocalHost.Port,
		SSHPasswd:        ctx.LocalHost.Password,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	masterNode, err := requireNode(ctx.GlobalNodes, bs.Masters[0])
	if err != nil {
		return nil, fmt.Errorf("第一个 master 节点: %w", err)
	}
	return singleHostAction(masterNode, t), nil
}

// buildK8sKuboard 安装 Kuboard（单节点）。
func buildK8sKuboard(ctx *BuildContext) ([]Action, error) {
	kb := ctx.Cfg.Global.Kubernetes.KuboardI
	t := &initcmd.InitK8sKuboard{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		KuboardX86Tar:    ctx.Cfg.Global.Packages.KuboardI.X86_64,
		KuboardArmTar:    ctx.Cfg.Global.Packages.KuboardI.Aarch64,
		PackagePath:      ctx.PackagesPath,
		Etcds:            kb.EtcdNodes,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	node, err := requireNode(ctx.GlobalNodes, kb.Node)
	if err != nil {
		return nil, fmt.Errorf("kuboard 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildK8sRegistryConf 配置 K8s 节点的 Registry（K8s master + worker 节点）。
func buildK8sRegistryConf(ctx *BuildContext) ([]Action, error) {
	bs := ctx.Cfg.Global.Kubernetes.BaseServices
	t := &initcmd.InitK8sRegistryConf{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		DockerHTTPPort:   ctx.Cfg.Global.Registry.Config.DockerHTTPPort,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)

	var k8sNodes []*config.Host
	for _, h := range bs.Masters {
		if node, ok := ctx.GlobalNodes[h]; ok {
			k8sNodes = append(k8sNodes, node)
		}
	}
	for _, h := range bs.Nodes {
		if node, ok := ctx.GlobalNodes[h]; ok {
			k8sNodes = append(k8sNodes, node)
		}
	}
	return hostsToActions(k8sNodes, t), nil
}

// buildKubectl 安装 kubectl（本地节点）。
func buildKubectl(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitKubectl{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Global.Packages.Kubectl.X86_64,
		Aarch64Tar:       ctx.Cfg.Global.Packages.Kubectl.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildHelm 安装 Helm（本地节点）。
func buildHelm(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitHelm{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Global.Packages.Helm.X86_64,
		Aarch64Tar:       ctx.Cfg.Global.Packages.Helm.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildHelmify 安装 Helmify（本地节点）。
func buildHelmify(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitHelmify{
		EnableK8sCluster: ctx.Cfg.Global.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Global.Packages.Helmify.X86_64,
		Aarch64Tar:       ctx.Cfg.Global.Packages.Helmify.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Global.Registry)
	return singleHostAction(ctx.LocalHost, t), nil
}
