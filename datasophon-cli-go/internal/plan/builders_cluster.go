package plan

import (
	"errors"
	"fmt"
	"log/slog"
	"strings"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/upload"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/shellutil"
	"golang.org/x/crypto/ssh"
)

// ntpServerTask 是 plan 包用于 NTP Server 步骤的 handler。
type ntpServerTask struct{}

func (t *ntpServerTask) Name() string { return "ntpserver时钟配置" }

func (t *ntpServerTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *ntpServerTask) doRun(exec executor.Executor) error {
	osType := exec.GetOs()

	checkCmd := "rpm -qa | grep chrony"
	installCmd := "yum -y install chrony"
	chronyConfPath := "/etc/chrony.conf"
	mvCmd := "mv /etc/chrony.conf /etc/chrony.conf.$(date +%Y%m%d.%H%M%S)"
	enableCmd := "systemctl enable chronyd"
	if osType.IsUbuntu() {
		checkCmd = "dpkg --list|grep chrony"
		installCmd = "DEBIAN_FRONTEND=noninteractive apt install chrony -y"
		chronyConfPath = "/etc/chrony/chrony.conf"
		mvCmd = "mv /etc/chrony/chrony.conf /etc/chrony/chrony.conf.$(date +%Y%m%d.%H%M%S)"
		enableCmd = "systemctl enable chrony"
	}

	exec.ExecShell(installCmd)
	if r := exec.ExecShell(checkCmd); !r.Success {
		slog.Error("chrony 安装失败")
		return errors.New("chrony 安装失败")
	}
	exec.ExecShell(mvCmd)

	conf := []string{
		"server 127.0.0.1 iburst",
		"driftfile /var/lib/chrony/drift",
		"makestep 1.0 3",
		"rtcsync",
		"allow all",
		"local stratum 10",
		"keyfile /etc/chrony.keys",
		"leapsectz right/UTC",
		"logdir /var/log/chrony",
	}
	exec.WriteLines(conf, chronyConfPath)
	slog.Info("chrony.conf 已写入", "path", chronyConfPath)

	exec.ExecShell(enableCmd)
	if osType.IsUbuntu() {
		exec.ExecShell("systemctl restart chronyd")
		exec.ExecShell("systemctl restart chrony")
	} else {
		exec.ExecShell("systemctl restart chronyd")
	}
	exec.ExecShell("chronyc sources")
	slog.Info("ntpserver 配置完成")
	return nil
}

// rustfsTask 是 plan 包用于 Rustfs 安装步骤的 handler。
type rustfsTask struct {
	Enable      bool
	PackagePath string
	InstallPath string
	X86Tar      string
	Aarch64Tar  string
	WebHost     string
	WebPort     string
	APIPort     string
	Username    string
	Password    string
	// ObsEndpoint 为空时不导出 RUSTFS_OBS_* 环境变量，RustFS 不上报指标。
	ObsEndpoint string
}

func (t *rustfsTask) Name() string { return "安装rustfs" }

func (t *rustfsTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *rustfsTask) doRun(exec executor.Executor) error {
	if !t.Enable {
		slog.Info("rustfs enable=false，跳过")
		return nil
	}
	if executor.InspectPath(exec, t.InstallPath) == executor.PathMissing {
		slog.Error("安装目录不存在", "path", t.InstallPath)
		return errors.New("rustfs 安装目录不存在")
	}

	home := fmt.Sprintf("%s/rustfs", t.InstallPath)
	dataPath := fmt.Sprintf("%s/data", home)
	logsPath := fmt.Sprintf("%s/logs", home)

	if executor.InspectPath(exec, home) == executor.PathExists {
		slog.Info("rustfs 目录已存在", "path", home)
	} else {
		tarName := t.X86Tar
		if exec.GetArch() == osinfo.ArchAarch64 {
			tarName = t.Aarch64Tar
		}
		tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
		if err := ensurePackageOnTarget(exec, tarPath, "rustfs"); err != nil {
			return err
		}
		// rustfs 官方发布物是 .zip，包内只有裸 rustfs 二进制（无版本号顶层目录，
		// 与 tar.gz 发布物的目录结构不同），直接解压到 home 即为 home/rustfs，无需 mv。
		if result := exec.ExecShell("mkdir -p " + shellutil.Quote(home)); !result.Success {
			return fmt.Errorf("创建 rustfs 目录失败: %s", result.ErrOutput)
		}
		if result := exec.ExecShell(fmt.Sprintf("unzip -o %s -d %s", shellutil.Quote(tarPath), shellutil.Quote(home))); !result.Success {
			return fmt.Errorf("解压 rustfs 失败: %s", result.ErrOutput)
		}
		if result := exec.ExecShell("chmod +x " + shellutil.Quote(home+"/rustfs")); !result.Success {
			return fmt.Errorf("设置 rustfs 执行权限失败: %s", result.ErrOutput)
		}
	}

	alreadyRunning := !executor.IsDryRun(exec) && t.checkStart(exec, home)
	// 始终按当前配置重写 start.sh；运行中进程的端点不一致时受控重启。
	scriptChanged, err := t.writeStartScript(exec, home, dataPath, logsPath)
	if err != nil {
		return err
	}
	if alreadyRunning && (scriptChanged || !t.runtimeMatchesObsEndpoint(exec, home)) {
		slog.Info("rustfs 运行配置发生变化，执行受控重启", "path", home)
		if !t.stop(exec, home) {
			return errors.New("rustfs 为应用 obsEndpoint 重启时停止失败")
		}
		alreadyRunning = false
	}
	if !alreadyRunning {
		exec.ExecShell("bash " + shellutil.Quote(home+"/start.sh"))
		exec.ExecShell("sleep 3")
	}

	if executor.IsDryRun(exec) || t.checkStart(exec, home) {
		slog.Info("rustfs 安装成功", "path", home)
		return nil
	}
	slog.Error("rustfs 启动失败", "path", home)
	return errors.New("rustfs 启动失败")
}

func (t *rustfsTask) runtimeMatchesObsEndpoint(exec executor.Executor, home string) bool {
	findPID := fmt.Sprintf("ps -eo pid=,args= | awk -v bin=%s '$2 == bin {print $1; exit}'",
		shellutil.Quote(home+"/rustfs"))
	command := ""
	if t.ObsEndpoint == "" {
		command = fmt.Sprintf("pid=$(%s); [ -n \"$pid\" ] && ! tr '\\0' '\\n' < /proc/$pid/environ | grep -q '^RUSTFS_OBS_ENDPOINT='", findPID)
	} else {
		expected := shellutil.Quote("RUSTFS_OBS_ENDPOINT=" + t.ObsEndpoint)
		command = fmt.Sprintf("pid=$(%s); [ -n \"$pid\" ] && tr '\\0' '\\n' < /proc/$pid/environ | grep -Fqx %s",
			findPID, expected)
	}
	return exec.ExecShell(command).Success
}

func (t *rustfsTask) stop(exec executor.Executor, home string) bool {
	findPID := fmt.Sprintf("ps -eo pid=,args= | awk -v bin=%s '$2 == bin {print $1; exit}'",
		shellutil.Quote(home+"/rustfs"))
	command := fmt.Sprintf("pid=$(%s); [ -z \"$pid\" ] || { kill \"$pid\" && "+
		"i=0; while kill -0 \"$pid\" 2>/dev/null && [ $i -lt 10 ]; do sleep 1; i=$((i+1)); done; "+
		"kill -0 \"$pid\" 2>/dev/null && kill -9 \"$pid\" || true; }", findPID)
	return exec.ExecShell(command).Success
}

func (t *rustfsTask) checkStart(exec executor.Executor, home string) bool {
	findPID := fmt.Sprintf("ps -eo pid=,args= | awk -v bin=%s '$2 == bin {print $1; exit}'",
		shellutil.Quote(home+"/rustfs"))
	r := exec.ExecShell(fmt.Sprintf("pid=$(%s); [ -n \"$pid\" ]", findPID))
	if r.Success {
		slog.Info("rustfs 已在运行")
		return true
	}
	slog.Info("rustfs 未在运行")
	return false
}

func (t *rustfsTask) writeStartScript(exec executor.Executor, home, data, logs string) (bool, error) {
	startCmd := fmt.Sprintf(
		"%s/rustfs --address %s:%s --console-enable --console-address %s:%s"+
			" --access-key %s --secret-key %s %s > %s/rustfs.log 2>&1 &",
		home, t.WebHost, t.APIPort, t.WebHost, t.WebPort,
		shellutil.Quote(t.Username), shellutil.Quote(t.Password), data, logs,
	)
	lines := []string{
		fmt.Sprintf("mkdir -p %s", shellutil.Quote(data)),
		fmt.Sprintf("mkdir -p %s", shellutil.Quote(logs)),
	}
	if t.ObsEndpoint != "" {
		lines = append(lines,
			fmt.Sprintf("export RUSTFS_OBS_ENDPOINT=%s", shellutil.Quote(t.ObsEndpoint)),
			"export RUSTFS_OBS_SERVICE_NAME=rustfs",
		)
	}
	lines = append(lines, startCmd)
	result := executor.WriteFileAtomic(exec, []byte(strings.Join(lines, "\n")+"\n"), fmt.Sprintf("%s/start.sh", home), 0o700)
	if !result.Success {
		return false, fmt.Errorf("写入 rustfs start.sh 失败: %s", result.ErrOutput)
	}
	return result.Output == "changed", nil
}

// buildRustfs 安装 rustfs（单节点）。
func buildRustfs(ctx *BuildContext) ([]Action, error) {
	rs := ctx.Cfg.Rustfs
	if len(rs.Nodes) == 0 {
		return nil, nil
	}
	node, err := requireNode(ctx.GlobalNodes, rs.Nodes[0])
	if err != nil {
		return nil, fmt.Errorf("rustfs 节点: %w", err)
	}
	obsEndpoint := ""
	if ctx.Cfg.BaseOtelCollector.Enable {
		resolved, err := resolveBaseObservability(ctx)
		if err != nil {
			return nil, err
		}
		obsEndpoint = fmt.Sprintf("http://%s:%s", resolved.CollectorNode.IP, resolved.Config.OtlpHTTPPort)
	}
	t := &rustfsTask{
		Enable:      rs.Enable,
		PackagePath: ctx.PackagesPath,
		InstallPath: ctx.InstallPath,
		X86Tar:      ctx.Cfg.Packages.Rustfs.X86_64,
		Aarch64Tar:  ctx.Cfg.Packages.Rustfs.Aarch64,
		WebHost:     node.IP,
		WebPort:     rs.Config.WebPort,
		APIPort:     rs.Config.APIPort,
		Username:    rs.Config.User,
		Password:    rs.Config.Password,
		ObsEndpoint: obsEndpoint,
	}
	return singleHostAction(node, t), nil
}

// buildRegistry 安装 Nexus Registry（单节点）。
func buildRegistry(ctx *BuildContext) ([]Action, error) {
	reg := ctx.Cfg.Registry
	node, err := requireNode(ctx.GlobalNodes, reg.Node)
	if err != nil {
		return nil, fmt.Errorf("registry 节点: %w", err)
	}
	metricsUser := ""
	metricsPassword := ""
	if ctx.Cfg.BaseOtelCollector.Enable {
		resolved, resolveErr := resolveBaseObservability(ctx)
		if resolveErr != nil {
			return nil, resolveErr
		}
		metricsUser = resolved.Config.NexusMetrics.MetricsUser
		metricsPassword = resolved.Config.NexusMetrics.MetricsPassword
	}
	t := &registryTask{
		EnableRegistry:  true,
		EnableMetrics:   ctx.Cfg.BaseOtelCollector.Enable,
		PackagePath:     ctx.PackagesPath,
		InstallPath:     ctx.InstallPath,
		Repositories:    reg.Config.Repositories,
		X86Tar:          ctx.Cfg.Packages.Nexus.X86_64,
		Aarch64Tar:      ctx.Cfg.Packages.Nexus.Aarch64,
		WebHost:         node.IP,
		WebPort:         reg.Config.WebPort,
		Username:        reg.Config.User,
		Password:        reg.Config.Password,
		DockerHTTPPort:  reg.Config.DockerHTTPPort,
		MetricsUser:     metricsUser,
		MetricsPassword: metricsPassword,
	}
	return singleHostAction(node, t), nil
}

// buildRegistryUpload 上传安装包到 Registry（本地节点执行）。
func buildRegistryUpload(ctx *BuildContext) ([]Action, error) {
	reg := ctx.Cfg.Registry
	t := &upload.UploadRegistry{
		ProductPackagesPath:   ctx.ProductPkgsPath,
		WebHost:               resolveIP(ctx.GlobalNodes, reg.Node),
		WebPort:               reg.Config.WebPort,
		Username:              reg.Config.User,
		Password:              reg.Config.Password,
		DisableUploadRegistry: reg.DisableUpload,
		DockerHTTPPort:        reg.Config.DockerHTTPPort,
	}
	applyRegistry(&t.TaskBase, &reg, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildContainerd 安装 containerd + runc + CNI（本地节点执行）。
func buildContainerd(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitContainerd{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		KubernetesForce:  ctx.Cfg.Kubernetes.Force,
		Offline:          ctx.Cfg.Global.Offline,
		PackagePath:      ctx.PackagesPath,
		DockerHTTPPort:   ctx.Cfg.Registry.Config.DockerHTTPPort,
		ContainerdX86Tar: ctx.Cfg.Packages.Containerd.X86_64,
		ContainerdArmTar: ctx.Cfg.Packages.Containerd.Aarch64,
		RuncX86Bin:       ctx.Cfg.Packages.Runc.X86_64,
		RuncArmBin:       ctx.Cfg.Packages.Runc.Aarch64,
		CniX86Tar:        ctx.Cfg.Packages.Cni.X86_64,
		CniArmTar:        ctx.Cfg.Packages.Cni.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildDocker 安装 Docker（本地节点执行）。
func buildDocker(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitDocker{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Packages.Docker.X86_64,
		Aarch64Tar:       ctx.Cfg.Packages.Docker.Aarch64,
		DockerHTTPPort:   ctx.Cfg.Registry.Config.DockerHTTPPort,
		KubernetesForce:  ctx.Cfg.Kubernetes.Force,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildOfflineServer 配置 yum/apt 离线源服务端（单节点）。
func buildOfflineServer(ctx *BuildContext) ([]Action, error) {
	ys := ctx.Cfg.YumServer
	t := &initcmd.InitOfflineServer{
		PackagePath: ctx.PackagesPath,
		ServerIP:    ys.Node,
		ServerPort:  ys.ListenPort,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	node, err := requireNode(ctx.GlobalNodes, ys.Node)
	if err != nil {
		return nil, fmt.Errorf("yumServer 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildNmap 安装 nmap（单节点）。
func buildNmap(ctx *BuildContext) ([]Action, error) {
	nm := ctx.Cfg.NmapServer
	node, err := requireNode(ctx.GlobalNodes, nm.Node)
	if err != nil {
		return nil, fmt.Errorf("nmapServer 节点: %w", err)
	}
	return singleHostAction(node, &initcmd.InitNmap{}), nil
}

// buildNtpServer 配置 NTP Server（单节点）。
func buildNtpServer(ctx *BuildContext) ([]Action, error) {
	ntp := ctx.Cfg.NtpServer
	node, err := requireNode(ctx.GlobalNodes, ntp.Node)
	if err != nil {
		return nil, fmt.Errorf("ntpServer 节点: %w", err)
	}
	return singleHostAction(node, &ntpServerTask{}), nil
}

// buildMysql 安装 MySQL（单节点）。
func buildMysql(ctx *BuildContext) ([]Action, error) {
	mc := ctx.Cfg.Mysql
	t := &mysqlTask{
		Password:    mc.Password,
		Force:       mc.Force,
		PackagePath: ctx.PackagesPath,
		InstallPath: ctx.InstallPath,
		X86Tar:      ctx.Cfg.Packages.Mysql.X86_64,
		Aarch64Tar:  ctx.Cfg.Packages.Mysql.Aarch64,
		Port:        mc.Port,
	}
	if ctx.Cfg.Registry.Enable {
		t.EnableRegistry = true
		t.RegistryIP = resolveIP(ctx.GlobalNodes, ctx.Cfg.Registry.Node)
		t.RegistryPort = ctx.Cfg.Registry.Config.WebPort
		t.RegistryUsername = ctx.Cfg.Registry.Config.User
		t.RegistryPassword = ctx.Cfg.Registry.Config.Password
	}
	node, err := requireNode(ctx.GlobalNodes, mc.Node)
	if err != nil {
		return nil, fmt.Errorf("mysql 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildMysqlAppDb 初始化 MySQL 数据库账号（多个 AppDb 每个一个 action）。
func buildMysqlAppDb(ctx *BuildContext) ([]Action, error) {
	mc := ctx.Cfg.Mysql
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

// buildMysqldExporter 在 MySQL 节点创建监控账号并安装 exporter。
func buildMysqldExporter(ctx *BuildContext) ([]Action, error) {
	mc := ctx.Cfg.Mysql
	resolved, err := resolveBaseObservability(ctx)
	if err != nil {
		return nil, err
	}
	exporter := resolved.Config.MysqldExporter
	node := resolved.MySQLNode
	t := &mysqldExporterTask{
		Enable:          exporter.Enable,
		PackagePath:     ctx.PackagesPath,
		InstallPath:     ctx.InstallPath,
		X86Tar:          ctx.Cfg.Packages.MysqldExporter.X86_64,
		Aarch64Tar:      ctx.Cfg.Packages.MysqldExporter.Aarch64,
		NodeIP:          node.IP,
		Port:            exporter.Port,
		MySQLPort:       mc.Port,
		RootPassword:    mc.Password,
		MonitorUser:     exporter.MonitorUser,
		MonitorPassword: exporter.MonitorPassword,
	}
	return singleHostAction(node, t), nil
}

// buildK8sBaseServices 安装 K8s 基础服务（master 节点）。
func buildK8sBaseServices(ctx *BuildContext) ([]Action, error) {
	k8s := ctx.Cfg.Kubernetes
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
		SealosX86Tar:     ctx.Cfg.Packages.Sealos.X86_64,
		SealosArmTar:     ctx.Cfg.Packages.Sealos.Aarch64,
		Kubernetes:       bs.KubernetesI,
		KubernetesX86Tar: ctx.Cfg.Packages.KubernetesI.X86_64,
		KubernetesArmTar: ctx.Cfg.Packages.KubernetesI.Aarch64,
		Helm:             bs.HelmI,
		HelmX86Tar:       ctx.Cfg.Packages.HelmI.X86_64,
		HelmArmTar:       ctx.Cfg.Packages.HelmI.Aarch64,
		Calico:           bs.CalicoI,
		CalicoX86Tar:     ctx.Cfg.Packages.CalicoI.X86_64,
		CalicoArmTar:     ctx.Cfg.Packages.CalicoI.Aarch64,
		Ingress:          bs.IngressI,
		IngressX86Tar:    ctx.Cfg.Packages.IngressI.X86_64,
		IngressArmTar:    ctx.Cfg.Packages.IngressI.Aarch64,
		PackagePath:      ctx.PackagesPath,
		SSHPort:          ctx.LocalHost.Port,
		SSHPasswd:        ctx.LocalHost.Password,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	masterNode, err := requireNode(ctx.GlobalNodes, bs.Masters[0])
	if err != nil {
		return nil, fmt.Errorf("第一个 master 节点: %w", err)
	}
	return singleHostAction(masterNode, t), nil
}

// buildK8sKuboard 安装 Kuboard（单节点）。
func buildK8sKuboard(ctx *BuildContext) ([]Action, error) {
	kb := ctx.Cfg.Kubernetes.KuboardI
	t := &initcmd.InitK8sKuboard{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		KuboardX86Tar:    ctx.Cfg.Packages.KuboardI.X86_64,
		KuboardArmTar:    ctx.Cfg.Packages.KuboardI.Aarch64,
		PackagePath:      ctx.PackagesPath,
		Etcds:            kb.EtcdNodes,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	node, err := requireNode(ctx.GlobalNodes, kb.Node)
	if err != nil {
		return nil, fmt.Errorf("kuboard 节点: %w", err)
	}
	return singleHostAction(node, t), nil
}

// buildK8sRegistryConf 配置 K8s 节点的 Registry（K8s master + worker 节点）。
func buildK8sRegistryConf(ctx *BuildContext) ([]Action, error) {
	bs := ctx.Cfg.Kubernetes.BaseServices
	t := &initcmd.InitK8sRegistryConf{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		DockerHTTPPort:   ctx.Cfg.Registry.Config.DockerHTTPPort,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)

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
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Packages.Kubectl.X86_64,
		Aarch64Tar:       ctx.Cfg.Packages.Kubectl.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildHelm 安装 Helm（本地节点）。
func buildHelm(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitHelm{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Packages.Helm.X86_64,
		Aarch64Tar:       ctx.Cfg.Packages.Helm.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}

// buildHelmify 安装 Helmify（本地节点）。
func buildHelmify(ctx *BuildContext) ([]Action, error) {
	t := &initcmd.InitHelmify{
		EnableK8sCluster: ctx.Cfg.Kubernetes.Enable,
		PackagePath:      ctx.PackagesPath,
		InstallPath:      ctx.InstallPath,
		X86Tar:           ctx.Cfg.Packages.Helmify.X86_64,
		Aarch64Tar:       ctx.Cfg.Packages.Helmify.Aarch64,
	}
	applyConfig(&t.TaskBase, ctx.ConfigYaml)
	applyRegistry(&t.TaskBase, &ctx.Cfg.Registry, ctx.GlobalNodes)
	return singleHostAction(ctx.LocalHost, t), nil
}
