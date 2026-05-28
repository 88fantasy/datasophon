package plan

import (
	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// InitALLRegistry 对应 initALL 的 33 步 DAG。
// 顺序即执行顺序；Condition 全部从 cfg.Global.* 读。
var InitALLRegistry = []Step{
	{ID: "init-bin-package", Name: "分发资源包",
		Build: buildBinPackage(allNodes)},

	{ID: "init-bash", Name: "shell bash 设置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitBash{} }, allNodes)},

	{ID: "init-tar", Name: "安装 tar",
		Build: buildTar(allNodes)},

	{ID: "init-rustfs", Name: "安装 rustfs",
		Condition: func(ctx *BuildContext) bool {
			return ctx.Cfg.Global.Registry.Enable && ctx.Cfg.Global.Rustfs.Enable
		},
		Build: buildRustfs},

	{ID: "init-registry", Name: "安装 registry",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Registry.Enable },
		Build:     buildRegistry},

	{ID: "init-docker-for-registry", Name: "安装 docker（registry 阶段）",
		Condition: func(ctx *BuildContext) bool {
			return ctx.Cfg.Global.Registry.Enable && ctx.Cfg.Global.Kubernetes.Enable
		},
		Build: buildDocker},

	{ID: "init-registry-upload", Name: "上传安装包到 registry",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Registry.Enable },
		Build:     buildRegistryUpload},

	{ID: "init-jdk8", Name: "安装 JDK 8",
		Build: buildJdk8(allNodes)},

	{ID: "init-jdk17", Name: "安装 JDK 17",
		Build: buildJdk17(allNodes)},

	{ID: "init-osuser", Name: "创建 hadoop 用户和组",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitOsUser{} }, allNodes)},

	{ID: "init-firewall", Name: "关闭防火墙",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitFirewall{} }, allNodes)},

	{ID: "init-selinux", Name: "关闭 selinux",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSelinux{} }, allNodes)},

	{ID: "init-swap", Name: "关闭 swap",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSwap{} }, allNodes)},

	{ID: "init-offline-server", Name: "yum/apt 离线源服务配置",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.YumServer.Enable },
		Build:     buildOfflineServer},

	{ID: "init-offline-nodes", Name: "yum/apt 离线源节点配置",
		Condition: func(ctx *BuildContext) bool {
			return ctx.Cfg.Global.YumServer.Enable || ctx.Cfg.Global.Registry.Enable
		},
		Build: buildOfflineNodes(allNodes)},

	{ID: "init-library", Name: "初始化依赖库",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitLibrary{} }, allNodes)},

	{ID: "init-os-safe-conf", Name: "安全配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitOsSafeConf{} }, allNodes)},

	{ID: "init-system-conf", Name: "优化系统配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSystemConf{} }, allNodes)},

	{ID: "init-hostname", Name: "配置 hostname",
		Build: buildHostname(allNodes)},

	{ID: "init-all-host", Name: "配置 /etc/hosts",
		Build: buildAllHost(allNodes)},

	{ID: "init-nmap", Name: "安装 nmap",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.NmapServer.Enable },
		Build:     buildNmap},

	{ID: "init-ntp-server", Name: "配置 NTP Server",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.NtpServer.Enable },
		Build:     buildNtpServer},

	{ID: "init-ntp-slave", Name: "配置 NTP Slave",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.NtpServer.Enable },
		Build:     buildNtpSlave(allNodes)},

	{ID: "init-mysql", Name: "安装 MySQL",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Mysql.Enable },
		Build:     buildMysql},

	{ID: "init-mysql-app-db", Name: "初始化 MySQL 数据库和账号",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Mysql.Enable },
		Build:     buildMysqlAppDb},

	{ID: "k8s-base-services", Name: "安装 K8s 集群",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildK8sBaseServices},

	{ID: "k8s-kuboard", Name: "安装 Kuboard",
		Condition: func(ctx *BuildContext) bool {
			return ctx.Cfg.Global.Kubernetes.Enable && ctx.Cfg.Global.Kubernetes.KuboardI.Enable
		},
		Build: buildK8sKuboard},

	{ID: "k8s-registry-conf", Name: "配置 K8s Registry",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildK8sRegistryConf},

	{ID: "k8s-docker", Name: "安装 Docker（K8s 阶段）",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildDocker},

	{ID: "k8s-kubectl", Name: "安装 kubectl",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildKubectl},

	{ID: "k8s-helm", Name: "安装 Helm",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildHelm},

	{ID: "k8s-helmify", Name: "安装 Helmify",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.Kubernetes.Enable },
		Build:     buildHelmify},

	{ID: "init-hugepage", Name: "关闭透明大页",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitHugePage{} }, allNodes)},
}

// InitSingleNodeRegistry 对应 initSingleNode 的 17 步 DAG（新增节点）。
var InitSingleNodeRegistry = []Step{
	{ID: "init-bin-package", Name: "分发资源包",
		Build: buildBinPackage(addNodes)},

	{ID: "init-bash", Name: "shell bash 设置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitBash{} }, addNodes)},

	{ID: "init-tar", Name: "安装 tar",
		Build: buildTar(addNodes)},

	{ID: "init-jdk8", Name: "安装 JDK 8",
		Build: buildJdk8(addNodes)},

	{ID: "init-jdk17", Name: "安装 JDK 17",
		Build: buildJdk17(addNodes)},

	{ID: "init-osuser", Name: "创建 hadoop 用户和组",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitOsUser{} }, addNodes)},

	{ID: "init-firewall", Name: "关闭防火墙",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitFirewall{} }, addNodes)},

	{ID: "init-selinux", Name: "关闭 selinux",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSelinux{} }, addNodes)},

	{ID: "init-swap", Name: "关闭 swap",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSwap{} }, addNodes)},

	{ID: "init-offline-nodes", Name: "yum/apt 离线源节点配置",
		Condition: func(ctx *BuildContext) bool {
			return ctx.Cfg.Global.YumServer.Enable || ctx.Cfg.Global.Registry.Enable
		},
		Build: buildOfflineNodes(addNodes)},

	{ID: "init-library", Name: "初始化依赖库",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitLibrary{} }, addNodes)},

	{ID: "init-os-safe-conf", Name: "安全配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitOsSafeConf{} }, addNodes)},

	{ID: "init-system-conf", Name: "优化系统配置",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitSystemConf{} }, addNodes)},

	{ID: "init-hostname", Name: "配置 hostname",
		Build: buildHostname(addNodes)},

	{ID: "init-all-host", Name: "配置 /etc/hosts（全部节点）",
		Build: buildAllHostBoth},

	{ID: "init-ntp-slave", Name: "配置 NTP Slave",
		Condition: func(ctx *BuildContext) bool { return ctx.Cfg.Global.NtpServer.Enable },
		Build:     buildNtpSlave(addNodes)},

	{ID: "init-hugepage", Name: "关闭透明大页",
		Build: simpleAllNodes(func() handler.Handler { return &initcmd.InitHugePage{} }, addNodes)},
}
