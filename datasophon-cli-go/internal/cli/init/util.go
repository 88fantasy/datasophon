package initcmd

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

// DownloadFromRegistry 对应 Java CliUtil.downRegistryFile。
// 若本地文件已存在 (isCheckExist=true) 则跳过；否则从 Nexus raw 仓库下载并写入 distPath。
func DownloadFromRegistry(
	exec executor.Executor,
	enableRegistry bool,
	registryIP, registryPort, registryUsername, registryPassword string,
	sourceName, distPath string,
	isCheckExist bool,
) {
	if isCheckExist && exec.Exists(distPath).Success {
		slog.Info("制品已存在，跳过下载", "path", distPath)
		return
	}
	if !enableRegistry {
		slog.Info("enableRegistry=false，跳过下载", "source", sourceName)
		return
	}
	url := fmt.Sprintf("http://%s:%s/repository/raw/packages/%s", registryIP, registryPort, sourceName)
	slog.Info("制品下载开始", "source", sourceName, "url", url)

	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		panic(fmt.Sprintf("构建下载请求失败: %v", err))
	}
	if registryUsername != "" {
		req.SetBasicAuth(registryUsername, registryPassword)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		panic(fmt.Sprintf("下载制品失败 url=%s: %v", url, err))
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		panic(fmt.Sprintf("下载制品 HTTP %d url=%s", resp.StatusCode, url))
	}
	result := exec.WriteFromStream(resp.Body, distPath)
	if !result.Success {
		panic(fmt.Sprintf("写入制品失败 path=%s: %s", distPath, result.ErrOutput))
	}
	slog.Info("制品下载完成", "path", distPath)
}

// nexusHTTPPost 向 Nexus REST API 发送 POST 请求（JSON body）。
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

// nexusHTTPPut 向 Nexus REST API 发送 PUT 请求。
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
