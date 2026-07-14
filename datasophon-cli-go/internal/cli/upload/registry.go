package upload

import (
	"bytes"
	"crypto/md5"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	initcmd "github.com/88fantasy/datasophon/datasophon-cli-go/internal/cli/init"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// nexusAssetsResponse 对应 /service/rest/v1/search/assets 返回结构。
type nexusAssetsResponse struct {
	Items []struct {
		Checksum struct {
			MD5 string `json:"md5"`
		} `json:"checksum"`
	} `json:"items"`
}

// progressReader 包装 io.Reader，在 HTTP 传输期间向 stderr 打印百分比进度条。
// total 为待传字节数（multipart body 大小），与文件大小近似相等。
type progressReader struct {
	r       io.Reader
	total   int64
	written int64
	name    string // 已截断的文件名，最长 40 字符
}

func newProgressReader(r io.Reader, total int64, name string) *progressReader {
	if len(name) > 40 {
		name = name[:37] + "..."
	}
	return &progressReader{r: r, total: total, name: name}
}

func (p *progressReader) Read(b []byte) (int, error) {
	n, err := p.r.Read(b)
	p.written += int64(n)
	p.render()
	return n, err
}

func (p *progressReader) render() {
	const barWidth = 25
	var pct float64
	filled := 0
	if p.total > 0 {
		pct = float64(p.written) / float64(p.total) * 100
		if pct > 100 {
			pct = 100
		}
		filled = int(pct / 100 * barWidth)
		if filled > barWidth {
			filled = barWidth
		}
	}
	bar := strings.Repeat("=", filled)
	if filled < barWidth {
		bar += ">"
		bar += strings.Repeat(" ", barWidth-filled-1)
	}
	fmt.Fprintf(os.Stderr, "\r%-40s [%s] %5.1f%%  %s / %s   ",
		p.name, bar, pct, formatBytes(p.written), formatBytes(p.total))
}

// finish 在进度行末尾换行，使后续日志不与进度条混排。
func (p *progressReader) finish() {
	fmt.Fprintln(os.Stderr)
}

// formatBytes 将字节数格式化为人类可读字符串（B / KB / MB）。
func formatBytes(n int64) string {
	const (
		mb = 1024 * 1024
		kb = 1024
	)
	switch {
	case n >= mb:
		return fmt.Sprintf("%.1f MB", float64(n)/mb)
	case n >= kb:
		return fmt.Sprintf("%.1f KB", float64(n)/kb)
	default:
		return fmt.Sprintf("%d B", n)
	}
}

// UploadRegistry 将本地安装包批量上传到 Nexus。
type UploadRegistry struct {
	initcmd.TaskBase
	ProductPackagesPath   string
	WebHost               string
	WebPort               string
	Username              string
	Password              string
	IsSuccessDelete       bool
	DisableUploadRegistry bool
	DockerHTTPPort        int
}

func (t *UploadRegistry) Name() string { return "制品库上传" }

func (t *UploadRegistry) Handle(client *ssh.Client, dryRun bool) error {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *UploadRegistry) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "registry",
		Short: "将本地安装包批量上传到 Nexus",
		RunE: func(cmd *cobra.Command, args []string) error {
			return t.doRun(executor.NewLocalExecutor(*dryRun))
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.ProductPackagesPath, "productPackagesPath", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.WebHost, "webHost", "", "Nexus 主机（必填）")
	cmd.Flags().StringVar(&t.WebPort, "webPort", "", "Nexus 端口（必填）")
	cmd.Flags().StringVarP(&t.Username, "username", "u", "", "用户名（必填）")
	cmd.Flags().StringVarP(&t.Password, "password", "p", "", "密码（必填）")
	cmd.Flags().BoolVarP(&t.IsSuccessDelete, "isSuccessDelete", "e", false, "上传成功后删除本地文件")
	cmd.Flags().BoolVar(&t.DisableUploadRegistry, "disableUploadRegistry", false, "禁用上传")
	cmd.Flags().IntVar(&t.DockerHTTPPort, "dockerHttpPort", 0, "Docker HTTP 端口（必填）")
	_ = cmd.MarkFlagRequired("productPackagesPath")
	_ = cmd.MarkFlagRequired("webHost")
	_ = cmd.MarkFlagRequired("webPort")
	_ = cmd.MarkFlagRequired("username")
	_ = cmd.MarkFlagRequired("password")
	_ = cmd.MarkFlagRequired("dockerHttpPort")
	return cmd
}

func (t *UploadRegistry) doRun(exec executor.Executor) error {
	if !t.EnableRegistry {
		slog.Info("enableRegistry=false，跳过上传")
		return nil
	}
	if _, err := os.Stat(t.ProductPackagesPath); os.IsNotExist(err) {
		slog.Error("本地目录不存在", "path", t.ProductPackagesPath)
		return errors.New("本地安装包目录不存在")
	}

	baseURL := fmt.Sprintf("http://%s:%s", t.WebHost, t.WebPort)
	if !t.DisableUploadRegistry {
		slog.Info("制品库上传开始", "url", baseURL)
		success, fail := t.repositoryUploadBatch(baseURL)
		t.uploadDocker(exec, baseURL)
		slog.Info("制品库上传完成", "success", success, "fail", fail)
	}
	return nil
}

// repositoryUploadBatch 遍历 productPackagesPath 下的子目录，按仓库类型上传。
// 目录结构约定（与 Java NexusFileUtils 对齐）：
//
//	yum/<arch>/<os>/*.rpm
//	apt/<arch>/<os>/*.deb
//	raw/packages/*
//	helm/*.tgz
//	docker/*.tar  （单独通过 docker push 处理）
func (t *UploadRegistry) repositoryUploadBatch(baseURL string) (int, int) {
	success, fail := 0, 0
	entries, err := os.ReadDir(t.ProductPackagesPath)
	if err != nil {
		slog.Error("读取目录失败", "path", t.ProductPackagesPath, "err", err)
		return 0, 0
	}
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		repoType := strings.ToLower(entry.Name())
		repoDir := filepath.Join(t.ProductPackagesPath, entry.Name())
		switch repoType {
		case "yum", "apt":
			// <arch>/<os>/**（递归遍历，含 repodata/ 等子目录——yum 仓库的
			// repomd.xml/primary.xml.gz 等索引元数据就存在这些子目录里，
			// 缺失时 yum makecache 会因找不到 repodata/repomd.xml 而失败）
			archEntries, _ := os.ReadDir(repoDir)
			for _, archEntry := range archEntries {
				if !archEntry.IsDir() {
					continue
				}
				archDir := filepath.Join(repoDir, archEntry.Name())
				osEntries, _ := os.ReadDir(archDir)
				for _, osEntry := range osEntries {
					if !osEntry.IsDir() {
						continue
					}
					osDir := filepath.Join(archDir, osEntry.Name())
					// directory 基础前缀格式：arch/os，与 Java NexusFileUtils 对齐；
					// 子目录（如 repodata）以相对路径追加在后面。
					baseDir := archEntry.Name() + "/" + osEntry.Name()
					_ = filepath.Walk(osDir, func(path string, info os.FileInfo, err error) error {
						if err != nil || info.IsDir() {
							return nil
						}
						rel, _ := filepath.Rel(osDir, path)
						relDir := filepath.ToSlash(filepath.Dir(rel))
						dir := baseDir
						if relDir != "." {
							dir = baseDir + "/" + relDir
						}
						ok := t.uploadFile(baseURL, repoType, path, dir, false)
						if ok {
							success++
							if t.IsSuccessDelete {
								_ = os.Remove(path)
							}
						} else {
							fail++
						}
						return nil
					})
				}
			}
		case "raw":
			_ = filepath.Walk(repoDir, func(path string, info os.FileInfo, err error) error {
				if err != nil || info.IsDir() {
					return nil
				}
				rel, _ := filepath.Rel(repoDir, path)
				dir := filepath.ToSlash(filepath.Dir(rel))
				if dir == "." {
					dir = ""
				} else {
					dir = "/" + dir
				}
				// 优先用同名 .md5 sidecar 文件做幂等检查
				if data, readErr := os.ReadFile(path + ".md5"); readErr == nil {
					localSum := strings.TrimSpace(string(data))
					assetName := filepath.Base(path)
					if dir != "" {
						assetName = dir + "/" + assetName
					}
					if remoteMD5 := t.nexusMD5(baseURL, repoType, assetName); remoteMD5 != "" && strings.EqualFold(localSum, remoteMD5) {
						slog.Info("MD5 sidecar 一致，跳过上传", "file", filepath.Base(path))
						return nil
					}
				}
				// 无 sidecar 或 MD5 不匹配：强制覆盖上传
				ok := t.uploadFile(baseURL, repoType, path, dir, true)
				if ok {
					success++
					if t.IsSuccessDelete {
						_ = os.Remove(path)
					}
				} else {
					fail++
				}
				return nil
			})
		case "helm":
			files, _ := os.ReadDir(repoDir)
			for _, f := range files {
				if f.IsDir() {
					continue
				}
				ok := t.uploadHelm(baseURL, filepath.Join(repoDir, f.Name()))
				if ok {
					success++
				} else {
					fail++
				}
			}
		case "docker":
			// 由 uploadDocker 处理
		default:
			slog.Info("不支持的目录类型，跳过", "dir", entry.Name())
		}
	}
	return success, fail
}

// nexusMD5 通过 Nexus REST API 查询已上传 asset 的 MD5。
// assetName 为文件在仓库中的完整路径（如 /packages/file.tar.gz）。
// 文件不存在或查询失败时返回空字符串。
func (t *UploadRegistry) nexusMD5(baseURL, repo, assetName string) string {
	queryURL := fmt.Sprintf("%s/service/rest/v1/search/assets?repository=%s&name=%s",
		baseURL, repo, url.QueryEscape(assetName))
	req, err := http.NewRequest(http.MethodGet, queryURL, nil)
	if err != nil {
		return ""
	}
	req.SetBasicAuth(t.Username, t.Password)
	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil || resp.StatusCode != 200 {
		return ""
	}
	defer resp.Body.Close()
	var result nexusAssetsResponse
	if err = json.NewDecoder(resp.Body).Decode(&result); err != nil || len(result.Items) == 0 {
		return ""
	}
	return result.Items[0].Checksum.MD5
}

// localMD5 计算本地文件的 MD5 hex 字符串。
func localMD5(filePath string) (string, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := md5.New()
	if _, err = io.Copy(h, f); err != nil {
		return "", err
	}
	return fmt.Sprintf("%x", h.Sum(nil)), nil
}

// uploadFile 用 multipart/form-data 上传单个文件到 Nexus 内部 UI 接口。
// directory 非空时写入 directory 文本字段（raw/yum 必填，apt 不填）。
// 字段布局与 Java NexusFileUtils 对齐：asset0 / asset0.filename / directory。
// force=true 时跳过远端 MD5 检查，直接覆盖上传。
func (t *UploadRegistry) uploadFile(baseURL, repoType, filePath, directory string, force bool) bool {
	// 构造文件在仓库中的完整路径，用于 MD5 幂等检查
	assetName := filepath.Base(filePath)
	if directory != "" {
		dir := directory
		if !strings.HasPrefix(dir, "/") {
			dir = "/" + dir
		}
		assetName = dir + "/" + assetName
	}
	if !force {
		remoteMD5 := t.nexusMD5(baseURL, repoType, assetName)
		if remoteMD5 != "" {
			if sum, err := localMD5(filePath); err == nil && sum == remoteMD5 {
				slog.Info("文件已存在且 MD5 一致，跳过上传", "file", filepath.Base(filePath))
				return true
			}
		}
	}

	file, err := os.Open(filePath)
	if err != nil {
		slog.Error("打开文件失败", "path", filePath, "err", err)
		return false
	}
	defer file.Close()

	fi, err := file.Stat()
	if err != nil {
		slog.Error("获取文件信息失败", "path", filePath, "err", err)
		return false
	}
	slog.Info("开始上传", "file", filepath.Base(filePath), "size", formatBytes(fi.Size()), "repo", repoType)

	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)

	if directory != "" {
		if err = w.WriteField("directory", directory); err != nil {
			slog.Error("写入 directory 字段失败", "err", err)
			return false
		}
	}
	if err = w.WriteField("asset0.filename", filepath.Base(filePath)); err != nil {
		slog.Error("写入 asset0.filename 字段失败", "err", err)
		return false
	}
	part, err := w.CreateFormFile("asset0", filepath.Base(filePath))
	if err != nil {
		slog.Error("创建 asset0 字段失败", "err", err)
		return false
	}
	if _, err = io.Copy(part, file); err != nil {
		slog.Error("写入文件内容失败", "err", err)
		return false
	}
	_ = w.Close()

	uploadURL := fmt.Sprintf("%s/service/rest/internal/ui/upload/%s", baseURL, repoType)
	const progressThreshold = 20 * 1024 * 1024 // 20 MB
	var pr *progressReader
	var reqBody io.Reader = &buf
	if fi.Size() >= progressThreshold {
		pr = newProgressReader(&buf, int64(buf.Len()), filepath.Base(filePath))
		reqBody = pr
	}
	req, err := http.NewRequest(http.MethodPost, uploadURL, reqBody)
	if err != nil {
		slog.Error("构建上传请求失败", "err", err)
		return false
	}
	req.SetBasicAuth(t.Username, t.Password)
	req.Header.Set("Content-Type", w.FormDataContentType())
	// 强制走 JSON 响应路径（postComponent），避免 RESTEasy 选中缺少 commons-lang 依赖的
	// postComponentWithHtmlResponse（IE 兼容包装），否则 Nexus 端抛 NoClassDefFoundError。
	req.Header.Set("Accept", "application/json")

	client := &http.Client{Timeout: 10 * time.Minute}
	resp, err := client.Do(req)
	if pr != nil {
		pr.finish()
	}
	if err != nil {
		slog.Error("上传文件失败", "path", filePath, "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 200 || resp.StatusCode == 201 || resp.StatusCode == 204 {
		slog.Info("上传成功", "file", filepath.Base(filePath))
		return true
	}
	body, _ := io.ReadAll(resp.Body)
	slog.Error("上传失败", "file", filepath.Base(filePath), "status", resp.StatusCode, "body", string(body))
	return false
}

// uploadHelm 上传 Helm Chart 到 Nexus helm 仓库（使用 Chartmuseum API）。
// URL 与字段名与其他仓库不同，单独处理。
func (t *UploadRegistry) uploadHelm(baseURL, filePath string) bool {
	file, err := os.Open(filePath)
	if err != nil {
		slog.Error("打开文件失败", "path", filePath, "err", err)
		return false
	}
	defer file.Close()

	fi, err := file.Stat()
	if err != nil {
		slog.Error("获取文件信息失败", "path", filePath, "err", err)
		return false
	}
	slog.Info("开始上传", "file", filepath.Base(filePath), "size", formatBytes(fi.Size()), "repo", "helm")

	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	part, err := w.CreateFormFile("chart", filepath.Base(filePath))
	if err != nil {
		slog.Error("创建 chart 字段失败", "err", err)
		return false
	}
	if _, err = io.Copy(part, file); err != nil {
		slog.Error("写入文件内容失败", "err", err)
		return false
	}
	_ = w.Close()

	uploadURL := fmt.Sprintf("%s/repository/helm/api/charts", baseURL)
	const progressThreshold = 20 * 1024 * 1024 // 20 MB
	var pr *progressReader
	var reqBody io.Reader = &buf
	if fi.Size() >= progressThreshold {
		pr = newProgressReader(&buf, int64(buf.Len()), filepath.Base(filePath))
		reqBody = pr
	}
	req, err := http.NewRequest(http.MethodPost, uploadURL, reqBody)
	if err != nil {
		slog.Error("构建 helm 上传请求失败", "err", err)
		return false
	}
	req.SetBasicAuth(t.Username, t.Password)
	req.Header.Set("Content-Type", w.FormDataContentType())
	req.Header.Set("Accept", "application/json")

	client := &http.Client{Timeout: 10 * time.Minute}
	resp, err := client.Do(req)
	if pr != nil {
		pr.finish()
	}
	if err != nil {
		slog.Error("上传 helm chart 失败", "path", filePath, "err", err)
		return false
	}
	defer resp.Body.Close()
	if resp.StatusCode == 200 || resp.StatusCode == 201 || resp.StatusCode == 204 {
		slog.Info("helm chart 上传成功", "file", filepath.Base(filePath))
		return true
	}
	body, _ := io.ReadAll(resp.Body)
	slog.Error("helm chart 上传失败", "file", filepath.Base(filePath), "status", resp.StatusCode, "body", string(body))
	return false
}

// uploadDocker 将 docker/ 目录下的 .tar 镜像 load 并 push 到私有仓库。
func (t *UploadRegistry) uploadDocker(exec executor.Executor, baseURL string) {
	dockerDir := filepath.Join(t.ProductPackagesPath, "docker")
	if !exec.Exists(dockerDir).Success {
		return
	}
	files, err := os.ReadDir(dockerDir)
	if err != nil {
		return
	}
	for _, f := range files {
		if f.IsDir() {
			continue
		}
		absPath := filepath.Join(dockerDir, f.Name())
		imageID := strings.TrimSpace(exec.ExecShell(fmt.Sprintf("docker load -i %s | cut -d' ' -f3", absPath)).Output)
		imageName := imageID
		if idx := strings.LastIndex(imageID, "/"); idx >= 0 {
			imageName = imageID[idx+1:]
		}
		rImageName := fmt.Sprintf("%s:%d/docker/%s", t.WebHost, t.DockerHTTPPort, imageName)
		exec.ExecShell(fmt.Sprintf("docker tag %s %s", imageID, rImageName))
		r := exec.ExecShell(fmt.Sprintf("docker push %s", rImageName))
		if r.Success {
			slog.Info("docker push 成功", "file", absPath)
		} else {
			slog.Error("docker push 失败", "file", absPath)
		}
	}
}
