package initcmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

const nexusEula = "Use of Sonatype Nexus Repository - Community Edition is governed by the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula. By returning the value from 'accepted:false' to 'accepted:true', you acknowledge that you have read and agree to the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula."

// InitRegistry 对应 Java InitRegistry — 安装 Sonatype Nexus 制品库并初始化仓库。
type InitRegistry struct {
	TaskBase
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

func (t *InitRegistry) Name() string { return "安装制品库registry" }

func (t *InitRegistry) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitRegistry) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "registry",
		Short: "安装 Sonatype Nexus 制品库",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.Type, "type", "nexus", "制品类型")
	cmd.Flags().StringVar(&t.PackagePath, "packagePath", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.InstallPath, "installPath", "", "安装路径（必填）")
	cmd.Flags().StringVarP(&t.X86Tar, "x86Tar", "x", "", "x86_64 包（必填）")
	cmd.Flags().StringVarP(&t.Aarch64Tar, "aarch64Tar", "a", "", "aarch64 包（必填）")
	cmd.Flags().StringVar(&t.WebHost, "webHost", "", "Web 主机（必填）")
	cmd.Flags().StringVar(&t.WebPort, "webPort", "", "Web 端口（必填）")
	cmd.Flags().StringVarP(&t.Username, "username", "u", "", "用户名（必填）")
	cmd.Flags().StringVarP(&t.Password, "password", "p", "", "密码（必填）")
	cmd.Flags().StringSliceVarP(&t.Repositories, "repositories", "r", nil, "仓库列表（必填）")
	cmd.Flags().IntVar(&t.DockerHTTPPort, "dockerHttpPort", 0, "Docker HTTP 端口（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("x86Tar")
	_ = cmd.MarkFlagRequired("aarch64Tar")
	_ = cmd.MarkFlagRequired("webHost")
	_ = cmd.MarkFlagRequired("webPort")
	_ = cmd.MarkFlagRequired("username")
	_ = cmd.MarkFlagRequired("password")
	_ = cmd.MarkFlagRequired("repositories")
	_ = cmd.MarkFlagRequired("dockerHttpPort")
	return cmd
}

func (t *InitRegistry) doRun(exec executor.Executor) bool {
	if !t.EnableRegistry {
		slog.Info("enableRegistry=false，跳过 registry 安装")
		return true
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
		return false
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

	// 检测并启动
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

	// 初始化
	if t.checkStart(exec) {
		if exec.Exists(passwordPath).Success {
			oldPassword := strings.TrimSpace(exec.GetFileString(passwordPath).Output)
			t.changePassword(baseURL, oldPassword)
		}
		t.systemEula(baseURL)
		t.repoCreateByList(baseURL)
		slog.Info("nexus 安装成功", "path", home)
		return true
	}
	slog.Error("nexus 安装失败", "path", home)
	return false
}

func (t *InitRegistry) checkStart(exec executor.Executor) bool {
	r := exec.ExecShell("ps -ef | grep nexus | grep sonatype-work | grep -v datasophon-cli | grep -v grep")
	if r.Success {
		slog.Info("nexus 已在运行")
		return true
	}
	slog.Info("nexus 未在运行")
	return false
}

func (t *InitRegistry) changePassword(baseURL, oldPassword string) bool {
	url := baseURL + "/service/rest/v1/security/users/admin/change-password"
	req, _ := http.NewRequest(http.MethodPut, url, strings.NewReader(t.Password))
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

func (t *InitRegistry) systemEula(baseURL string) bool {
	type eulaReq struct {
		Accepted   bool   `json:"accepted"`
		Disclaimer string `json:"disclaimer"`
	}
	payload, _ := json.Marshal(eulaReq{Accepted: true, Disclaimer: nexusEula})
	resp, err := nexusHTTPPost(baseURL, "/service/rest/v1/system/eula",
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

func (t *InitRegistry) repoCreateByList(baseURL string) {
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

func (t *InitRegistry) postRepo(baseURL, path string, payload interface{}) bool {
	body, _ := json.Marshal(payload)
	resp, err := nexusHTTPPost(baseURL, path, t.Username, t.Password, "application/json", bytes.NewReader(body))
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

func (t *InitRegistry) rawRepoCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/raw/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}})
}

func (t *InitRegistry) yumRepoCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/yum/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}, "yum": map[string]interface{}{"repodataDepth": 0}})
}

func (t *InitRegistry) aptRepoCreate(baseURL, repoName, distribution string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/apt/hosted",
		map[string]interface{}{"name": repoName, "online": true, "storage": map[string]interface{}{"blobStoreName": "default", "strictContentTypeValidation": false, "writePolicy": "allow"}, "apt": map[string]interface{}{"distribution": distribution}, "aptSigning": map[string]interface{}{"keypair": "key", "passphrase": ""}})
}

func (t *InitRegistry) helmCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/helm/hosted",
		map[string]interface{}{"name": repoName, "online": true})
}

func (t *InitRegistry) dockerCreate(baseURL, repoName string) bool {
	return t.postRepo(baseURL, "/service/rest/v1/repositories/docker/hosted",
		map[string]interface{}{"name": repoName, "online": true, "docker": map[string]interface{}{"httpPort": t.DockerHTTPPort, "forceBasicAuth": true}})
}

func (t *InitRegistry) realmsDocker(baseURL string) bool {
	payload, _ := json.Marshal([]string{"DockerToken", "NexusAuthenticatingRealm"})
	resp, err := nexusHTTPPut(baseURL, "/service/rest/v1/security/realms/active",
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
