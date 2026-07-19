package plan

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/bootstrap"
)

// registryTask 是 plan 包用于 Nexus Registry 安装步骤的 handler。
type registryTask struct {
	EnableRegistry bool
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
			if !t.changePassword(baseURL, oldPassword) {
				return errors.New("nexus 修改管理员密码失败")
			}
		}
		if err := bootstrap.AcceptNexusEULA(baseURL, t.Username, t.Password,
			nexusHTTPGet, nexusHTTPPost); err != nil {
			return fmt.Errorf("nexus 接受 EULA 失败: %w", err)
		}
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

func nexusHTTPGet(baseURL, path, username, password string) (*http.Response, error) {
	req, err := http.NewRequest(http.MethodGet, baseURL+path, nil)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(username, password)
	return http.DefaultClient.Do(req)
}

func nexusHTTPPost(baseURL, path, username, password, contentType string, body io.Reader) (*http.Response, error) {
	url := baseURL + path
	req, err := http.NewRequest(http.MethodPost, url, body)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(username, password)
	req.Header.Set("Content-Type", contentType)
	return http.DefaultClient.Do(req)
}

func nexusHTTPPut(baseURL, path, username, password, contentType string, body io.Reader) (*http.Response, error) {
	url := baseURL + path
	req, err := http.NewRequest(http.MethodPut, url, body)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(username, password)
	req.Header.Set("Content-Type", contentType)
	return http.DefaultClient.Do(req)
}
