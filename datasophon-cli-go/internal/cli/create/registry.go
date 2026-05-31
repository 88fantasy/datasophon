package create

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

const registryNexusEula = "Use of Sonatype Nexus Repository - Community Edition is governed by the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula. By returning the value from 'accepted:false' to 'accepted:true', you acknowledge that you have read and agree to the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula."

// registryTask 安装 Sonatype Nexus 制品库并初始化仓库。
type registryTask struct {
	EnableRegistry bool
	Type           string
	PackagePath    string
	InstallPath    string
	X86Tar         string
	Aarch64Tar     string
	WebHost        string
	WebPort        string
	Username       string
	Password       string
	Repositories   []string
	DockerHTTPPort int
}

func (t *registryTask) Name() string { return "安装制品库registry" }

func (t *registryTask) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *registryTask) doRun(exec executor.Executor) error {
	if !t.EnableRegistry {
		slog.Info("enableRegistry=false，跳过 registry 安装")
		return nil
	}

	if !exec.Exists(t.InstallPath).Success {
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", t.InstallPath))
	}
	home := fmt.Sprintf("%s/nexusDir", t.InstallPath)
	nexusPath := fmt.Sprintf("%s/nexus", home)
	nexusPropertiesPath := fmt.Sprintf("%s/etc/nexus-default.properties", nexusPath)
	sonatypePath := fmt.Sprintf("%s/sonatype-work", home)
	passwordPath := fmt.Sprintf("%s/nexus3/admin.password", sonatypePath)
	baseURL := fmt.Sprintf("http://%s:%s", t.WebHost, t.WebPort)

	tarPath := fmt.Sprintf("%s/%s", t.PackagePath, t.X86Tar)
	if exec.GetArch() == osinfo.ArchAarch64 {
		tarPath = fmt.Sprintf("%s/%s", t.PackagePath, t.Aarch64Tar)
	}
	if !exec.Exists(tarPath).Success {
		slog.Error("安装包不存在", "path", tarPath)
		return errors.New("安装包不存在")
	}

	if exec.Exists(nexusPath).Success {
		slog.Info("nexus 目录已存在", "path", nexusPath)
	} else {
		exec.ExecShell(fmt.Sprintf("mkdir -p %s", home))
		exec.ExecShell(fmt.Sprintf("tar xzf %s -C %s", tarPath, home))
		exec.ExecShell(fmt.Sprintf("mv %s/nexus-* %s", home, nexusPath))
		exec.WriteLines([]string{
			fmt.Sprintf("application-port=%s", t.WebPort),
			"application-host=0.0.0.0",
			"nexus-args=${jetty.etc}/jetty.xml,${jetty.etc}/jetty-http.xml,${jetty.etc}/jetty-requestlog.xml",
			"nexus-context-path=/",
		}, nexusPropertiesPath)
		slog.Info("nexus-default.properties 已生成", "path", nexusPropertiesPath)
	}

	if !t.checkStart(exec) {
		exec.ExecShell(fmt.Sprintf("%s/bin/nexus start", nexusPath))
		for i := 0; i < 20; i++ {
			if exec.Exists(passwordPath).Success {
				break
			}
			slog.Info("等待 nexus 初始化...", "retry", i+1)
			exec.ExecShell("sleep 10")
		}
	}

	if t.checkStart(exec) {
		if exec.Exists(passwordPath).Success {
			oldPassword := strings.TrimSpace(exec.GetFileString(passwordPath).Output)
			t.changePassword(baseURL, oldPassword)
		}
		t.systemEula(baseURL)
		t.repoCreateByList(baseURL)
		slog.Info("nexus 安装成功", "path", home)
		return nil
	}
	slog.Error("nexus 安装失败", "path", home)
	return errors.New("nexus 安装失败")
}

func (t *registryTask) checkStart(exec executor.Executor) bool {
	r := exec.ExecShell("ps -ef | grep nexus | grep sonatype-work | grep -v datasophon-cli | grep -v grep")
	if r.Success {
		slog.Info("nexus 已在运行")
		return true
	}
	slog.Info("nexus 未在运行")
	return false
}

func (t *registryTask) changePassword(baseURL, oldPassword string) bool {
	url := baseURL + "/service/rest/v1/security/users/admin/change-password"
	req, err := http.NewRequest(http.MethodPut, url, strings.NewReader(t.Password))
	if err != nil {
		slog.Error("构建修改密码请求失败", "err", err)
		return false
	}
	req.SetBasicAuth(t.Username, oldPassword)
	req.Header.Set("Content-Type", "text/plain")
	req.Header.Set("Accept", "application/json")

	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		slog.Error("修改密码请求失败", "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 204 {
		slog.Info("修改密码成功")
		return true
	}
	body, _ := io.ReadAll(resp.Body)
	slog.Error("修改密码失败", "status", resp.StatusCode, "body", string(body))
	return false
}

func (t *registryTask) systemEula(baseURL string) bool {
	type eulaReq struct {
		Accepted   bool   `json:"accepted"`
		Disclaimer string `json:"disclaimer"`
	}
	payload, _ := json.Marshal(eulaReq{Accepted: true, Disclaimer: registryNexusEula})
	resp, err := registryHTTPPost(baseURL, "/service/rest/v1/system/eula",
		t.Username, t.Password, "application/json", bytes.NewReader(payload))
	if err != nil {
		slog.Error("eula 协议请求失败", "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 204 {
		slog.Info("eula 协议设置成功")
		return true
	}
	slog.Error("eula 协议设置失败", "status", resp.StatusCode)
	return false
}

func (t *registryTask) repoCreateByList(baseURL string) {
	slog.Info("初始化 nexus 仓库")
	for _, repo := range t.Repositories {
		lower := strings.ToLower(repo)
		switch {
		case strings.HasPrefix(lower, "apt"):
			t.aptRepoCreate(baseURL, repo, "jammy")
		case strings.HasPrefix(lower, "raw"):
			t.rawRepoCreate(baseURL, repo)
		case strings.HasPrefix(lower, "yum"):
			t.yumRepoCreate(baseURL, repo)
		case strings.HasPrefix(lower, "docker"):
			t.dockerCreate(baseURL, repo)
			t.realmsDocker(baseURL)
		case strings.HasPrefix(lower, "helm"):
			t.helmCreate(baseURL, repo)
		default:
			slog.Info("不支持的仓库类型，跳过", "repo", repo)
		}
	}
}

func (t *registryTask) postRepo(baseURL, path string, payload interface{}) bool {
	body, _ := json.Marshal(payload)
	resp, err := registryHTTPPost(baseURL, path, t.Username, t.Password, "application/json", bytes.NewReader(body))
	if err != nil {
		slog.Error("创建仓库请求失败", "path", path, "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 201 {
		slog.Info("创建仓库成功", "path", path)
		return true
	}
	respBody, _ := io.ReadAll(resp.Body)
	slog.Error("创建仓库失败", "path", path, "status", resp.StatusCode, "body", string(respBody))
	return false
}

func (t *registryTask) rawRepoCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/raw/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}})
}

func (t *registryTask) yumRepoCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/yum/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}, "yum": map[string]interface{}{"repodataDepth": 0}})
}

func (t *registryTask) aptRepoCreate(baseURL, repoName, distribution string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/apt/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}, "apt": map[string]interface{}{"distribution": distribution}, "aptSigning": map[string]interface{}{"keypair": "key", "passphrase": ""}})
}

func (t *registryTask) helmCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/helm/hosted",
		map[string]interface{}{"name": repoName, "online": true})
}

func (t *registryTask) dockerCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/docker/hosted",
		map[string]interface{}{"name": repoName, "online": true, "docker": map[string]interface{}{"httpPort": t.DockerHTTPPort, "forceBasicAuth": true}})
}

func (t *registryTask) realmsDocker(baseURL string) bool {
	payload, _ := json.Marshal([]string{"DockerToken", "NexusAuthenticatingRealm"})
	resp, err := registryHTTPPut(baseURL, "/service/rest/v1/security/realms/active",
		t.Username, t.Password, "application/json", bytes.NewReader(payload))
	if err != nil {
		slog.Error("realms 配置请求失败", "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 204 {
		slog.Info("realms 配置成功")
		return true
	}
	slog.Error("realms 配置失败", "status", resp.StatusCode)
	return false
}

func registryHTTPPost(baseURL, path, username, password, contentType string, body io.Reader) (*http.Response, error) {
	url := baseURL + path
	req, err := http.NewRequest(http.MethodPost, url, body)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(username, password)
	req.Header.Set("Content-Type", contentType)
	return http.DefaultClient.Do(req)
}

func registryHTTPPut(baseURL, path, username, password, contentType string, body io.Reader) (*http.Response, error) {
	url := baseURL + path
	req, err := http.NewRequest(http.MethodPut, url, body)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(username, password)
	req.Header.Set("Content-Type", contentType)
	return http.DefaultClient.Do(req)
}

// ── 命令实现 ────────────────────────────────────────────────────────────────

type createRegistryCmd struct {
	configFile     string
	datasophonPath string
	installPath    string

	regType        string
	node           string
	file           string
	webPort        string
	user           string
	password       string
	dockerHTTPPort int
	repositories   []string

	dryRun bool
}

func NewRegistryCommand(dryRun *bool) *cobra.Command {
	c := &createRegistryCmd{}
	cmd := &cobra.Command{
		Use:   "registry",
		Short: "在 registry 节点上安装 Sonatype Nexus 制品库",
		Long: `安装 Sonatype Nexus 制品库，支持两种模式：

  1. 配置文件模式（指定 -c）：从 cluster-sample.yml 的 registry 读取参数，
     SSH 到 registry 节点远程执行，完成后将 registry.enable 置为 true 写回配置文件。
     需同时提供 --datasophonPath 和 --installPath。

  2. 手动模式（不指定 -c）：所有参数通过命令行传入，在本地节点执行。
     需提供 --node / -f / --webPort / --user / --password / --dockerHttpPort / --repositories / --installPath。`,
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.configFile, "config", "c", "", "配置文件路径（指定后从 registry 读取参数）")
	cmd.Flags().StringVar(&c.datasophonPath, "datasophonPath", "", "datasophon 绝对路径（配置文件模式下必填，推导安装包路径）")

	cmd.Flags().StringVar(&c.installPath, "installPath", "", "Nexus 安装路径（必填）")

	cmd.Flags().StringVar(&c.regType, "type", "nexus", "制品库类型")
	cmd.Flags().StringVar(&c.node, "node", "", "registry 节点 hostname 或 IP（手动模式必填）")
	cmd.Flags().StringVarP(&c.file, "file", "f", "", "Nexus 安装包完整路径（手动模式必填）")
	cmd.Flags().StringVar(&c.webPort, "webPort", "", "Nexus 端口（手动模式必填）")
	cmd.Flags().StringVarP(&c.user, "user", "u", "", "Nexus 用户名（手动模式必填）")
	cmd.Flags().StringVarP(&c.password, "password", "p", "", "Nexus 密码（手动模式必填）")
	cmd.Flags().IntVar(&c.dockerHTTPPort, "dockerHttpPort", 0, "Docker HTTP 端口（手动模式必填）")
	cmd.Flags().StringSliceVarP(&c.repositories, "repositories", "r", nil, "仓库列表（手动模式必填）")

	return cmd
}

func (c *createRegistryCmd) run() error {
	if c.configFile != "" {
		return c.runFromConfig()
	}
	return c.runFromFlags()
}

func (c *createRegistryCmd) runFromConfig() error {
	if c.datasophonPath == "" {
		return fmt.Errorf("配置文件模式下 --datasophonPath 为必填项")
	}
	if c.installPath == "" {
		return fmt.Errorf("--installPath 为必填项")
	}
	if !strings.HasPrefix(c.datasophonPath, "/") {
		return fmt.Errorf("--datasophonPath 必须是绝对路径（以 / 开头）")
	}
	c.datasophonPath = strings.TrimSuffix(c.datasophonPath, "/")
	packagesPath := c.datasophonPath + "/datasophon-init/packages"

	cfg, err := config.Load(c.configFile)
	if err != nil {
		return fmt.Errorf("加载配置文件失败: %w", err)
	}

	reg := cfg.Registry
	globalNodes := make(map[string]*config.Host, len(cfg.Nodes))
	for i := range cfg.Nodes {
		globalNodes[cfg.Nodes[i].Hostname] = &cfg.Nodes[i]
	}
	node, ok := globalNodes[reg.Node]
	if !ok {
		return fmt.Errorf("配置中未找到 registry 节点: %s（请检查 registry.node 与 nodes 列表是否一致）", reg.Node)
	}

	t := &registryTask{
		EnableRegistry: true,
		PackagePath:    packagesPath,
		InstallPath:    c.installPath,
		Repositories:   reg.Config.Repositories,
		X86Tar:         cfg.Packages.Nexus.X86_64,
		Aarch64Tar:     cfg.Packages.Nexus.Aarch64,
		WebHost:        reg.Node,
		WebPort:        reg.Config.WebPort,
		Username:       reg.Config.User,
		Password:       reg.Config.Password,
		DockerHTTPPort: reg.Config.DockerHTTPPort,
	}
	if err := handler.NewChain(node, cfg.Global.SSHAuthType, c.dryRun).Add(t).Handle(); err != nil {
		return err
	}

	cfg.Registry.Enable = true
	if err := config.Save(c.configFile, cfg); err != nil {
		return fmt.Errorf("安装成功但写回配置文件失败: %w", err)
	}
	return nil
}

func (c *createRegistryCmd) runFromFlags() error {
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
	if c.user == "" {
		missing = append(missing, "--user")
	}
	if c.password == "" {
		missing = append(missing, "--password")
	}
	if c.dockerHTTPPort == 0 {
		missing = append(missing, "--dockerHttpPort")
	}
	if len(c.repositories) == 0 {
		missing = append(missing, "--repositories")
	}
	if len(missing) > 0 {
		return fmt.Errorf("手动模式下以下参数为必填项: %s", strings.Join(missing, ", "))
	}

	t := &registryTask{
		EnableRegistry: true,
		Type:           c.regType,
		PackagePath:    filepath.Dir(c.file),
		InstallPath:    c.installPath,
		Repositories:   c.repositories,
		X86Tar:         filepath.Base(c.file),
		Aarch64Tar:     filepath.Base(c.file),
		WebHost:        c.node,
		WebPort:        c.webPort,
		Username:       c.user,
		Password:       c.password,
		DockerHTTPPort: c.dockerHTTPPort,
	}
	return t.doRun(executor.NewLocalExecutor(c.dryRun))
}
