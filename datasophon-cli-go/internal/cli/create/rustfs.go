package create

import (
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// rustfsTask 安装并启动 Rustfs 对象存储。
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
	// ObsEndpoint 为空时不导出 RUSTFS_OBS_* 环境变量，rustfs 不上报任何指标（向后兼容默认行为）。
	// 非空时应为本节点 OTel Collector 的 OTLP HTTP 端点，如 http://127.0.0.1:4318。
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
	if !exec.Exists(t.InstallPath).Success {
		slog.Error("安装目录不存在", "path", t.InstallPath)
		return errors.New("rustfs 安装目录不存在")
	}

	home := fmt.Sprintf("%s/rustfs", t.InstallPath)
	dataPath := fmt.Sprintf("%s/data", home)
	logsPath := fmt.Sprintf("%s/logs", home)

	if exec.Exists(home).Success {
		slog.Info("rustfs 目录已存在", "path", home)
	} else {
		tarName := t.X86Tar
		if exec.GetArch() == osinfo.ArchAarch64 {
			tarName = t.Aarch64Tar
		}
		tarPath := fmt.Sprintf("%s/%s", t.PackagePath, tarName)
		if !exec.Exists(tarPath).Success {
			slog.Error("安装包不存在", "path", tarPath)
			return errors.New("rustfs 安装包不存在")
		}
		exec.ExecShell(fmt.Sprintf("tar xvz -f %s -C %s", tarPath, t.InstallPath))
		exec.ExecShell(fmt.Sprintf("mv %s/rustfs-* %s", t.InstallPath, home))
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", dataPath))
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", logsPath))
	}

	if !t.checkStart(exec) {
		t.start(exec, home, dataPath, logsPath)
		exec.ExecShell("sleep 3")
	}

	if t.checkStart(exec) {
		slog.Info("rustfs 安装成功", "path", home)
		return nil
	}
	slog.Error("rustfs 启动失败", "path", home)
	return errors.New("rustfs 启动失败")
}

func (t *rustfsTask) checkStart(exec executor.Executor) bool {
	r := exec.ExecShell("ps -ef | grep rustfs | grep -v datasophon-cli | grep -v grep")
	if r.Success {
		slog.Info("rustfs 已在运行")
		return true
	}
	slog.Info("rustfs 未在运行")
	return false
}

func (t *rustfsTask) start(exec executor.Executor, home, data, logs string) bool {
	startCmd := fmt.Sprintf(
		"%s/rustfs --address %s:%s --console-enable --console-address %s:%s"+
			" --access-key %s --secret-key %s %s > %s/rustfs.log 2>&1 &",
		home, t.WebHost, t.APIPort, t.WebHost, t.WebPort,
		t.Username, t.Password, data, logs,
	)
	var lines []string
	if t.ObsEndpoint != "" {
		// rustfs-obs crate 读取的环境变量，metrics 走 OTLP/HTTP 上报到本节点 OTel Collector。
		lines = append(lines,
			fmt.Sprintf("export RUSTFS_OBS_ENDPOINT=%s", t.ObsEndpoint),
			"export RUSTFS_OBS_SERVICE_NAME=rustfs",
		)
	}
	lines = append(lines, startCmd)
	startPath := fmt.Sprintf("%s/start.sh", home)
	exec.WriteLines(lines, startPath)
	r := exec.ExecShell(fmt.Sprintf("bash %s", startPath))
	return r.Success
}

// ── 命令实现 ────────────────────────────────────────────────────────────────

type createRustfsCmd struct {
	configFile     string
	datasophonPath string

	installPath string

	node        string
	file        string
	webPort     string
	apiPort     string
	user        string
	password    string
	obsEndpoint string

	dryRun bool
}

func NewRustfsCommand(dryRun *bool) *cobra.Command {
	c := &createRustfsCmd{}
	cmd := &cobra.Command{
		Use:   "rustfs",
		Short: "在 rustfs 节点上安装并启动 Rustfs 对象存储",
		Long: `安装 Rustfs 对象存储，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 rustfs 读取参数，
     SSH 到 rustfs.nodes 列表中的每个节点依次远程执行；
     安装成功后将 rustfs.enable 置为 true 写回配置文件。
     需同时提供 --datasophonPath 和 --installPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --node / -f / --installPath / --webPort / --apiPort / --user / --password。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 rustfs 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	cmd.Flags().StringVar(&c.installPath, "installPath", "", "Rustfs 安装路径（必填）")

	cmd.Flags().StringVar(&c.node, "node", "", "Rustfs 节点 hostname 或 IP，同时作为服务绑定地址（手动模式必填）")
	cmd.Flags().StringVarP(&c.file, "file", "f", "", "Rustfs tar 安装包完整路径（手动模式必填）")
	cmd.Flags().StringVar(&c.webPort, "webPort", "", "控制台端口（手动模式必填）")
	cmd.Flags().StringVar(&c.apiPort, "apiPort", "", "API 端口（手动模式必填）")
	cmd.Flags().StringVarP(&c.user, "user", "u", "", "访问密钥（手动模式必填）")
	cmd.Flags().StringVarP(&c.password, "password", "p", "", "密钥（手动模式必填）")
	cmd.Flags().StringVar(&c.obsEndpoint, "obsEndpoint", "", "OTel Collector OTLP/HTTP 端点（选填，如 http://127.0.0.1:4318；留空则不上报指标）")

	return cmd
}

func (c *createRustfsCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

func (c *createRustfsCmd) runFromConfig() error {
	if c.datasophonPath == "" {
		return fmt.Errorf("配置文件模式下 --datasophonPath 为必填项")
	}
	if c.installPath == "" {
		return fmt.Errorf("--installPath 为必填项")
	}
	if !strings.HasPrefix(c.datasophonPath, "/") {
		return fmt.Errorf("--datasophonPath 必须是绝对路径（以 / 开头）")
	}
	if !strings.HasPrefix(c.installPath, "/") {
		return fmt.Errorf("--installPath 必须是绝对路径（以 / 开头）")
	}
	c.datasophonPath = strings.TrimSuffix(c.datasophonPath, "/")
	packagesPath := c.datasophonPath + "/datasophon-init/packages"

	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	rustfsCfg := cfg.Rustfs
	if len(rustfsCfg.Nodes) == 0 {
		return fmt.Errorf("配置中 rustfs.nodes 为空，至少需要一个节点")
	}

	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}

	for _, hostname := range rustfsCfg.Nodes {
		node, ok := globalNodes[hostname]
		if !ok {
			return fmt.Errorf("配置中未找到 rustfs 节点: %s（请检查 rustfs.nodes 与 nodes 列表是否一致）", hostname)
		}

		t := &rustfsTask{
			Enable:      true,
			PackagePath: packagesPath,
			InstallPath: c.installPath,
			X86Tar:      cfg.Packages.Rustfs.X86_64,
			Aarch64Tar:  cfg.Packages.Rustfs.Aarch64,
			WebHost:     node.IP,
			WebPort:     rustfsCfg.Config.WebPort,
			APIPort:     rustfsCfg.Config.APIPort,
			Username:    rustfsCfg.Config.User,
			Password:    rustfsCfg.Config.Password,
			ObsEndpoint: rustfsCfg.Config.ObsEndpoint,
		}
		if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
			return fmt.Errorf("节点 %s 安装失败: %w", hostname, err)
		}
	}

	cfg.Rustfs.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createRustfsCmd) runFromFlags() error {
	missing := []string{}
	if c.installPath == "" {
		missing = append(missing, "--installPath")
	}
	if c.node == "" {
		missing = append(missing, "--node")
	}
	if c.file == "" {
		missing = append(missing, "--file / -f")
	}
	if c.webPort == "" {
		missing = append(missing, "--webPort")
	}
	if c.apiPort == "" {
		missing = append(missing, "--apiPort")
	}
	if c.user == "" {
		missing = append(missing, "--user")
	}
	if c.password == "" {
		missing = append(missing, "--password")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	t := &rustfsTask{
		Enable:      true,
		PackagePath: filepath.Dir(c.file),
		InstallPath: c.installPath,
		X86Tar:      filepath.Base(c.file),
		Aarch64Tar:  filepath.Base(c.file),
		WebHost:     c.node,
		WebPort:     c.webPort,
		APIPort:     c.apiPort,
		Username:    c.user,
		Password:    c.password,
		ObsEndpoint: c.obsEndpoint,
	}
	return t.doRun(executor.NewLocalExecutor(c.dryRun))
}
