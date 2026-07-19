package upload

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestRepositoryUploadBatch_RawUploadsMd5Sidecar 覆盖回归场景：raw 仓库上传必须把 .md5
// sidecar 文件本身也上传到 Nexus，因为 Worker 侧 NexusPackageStorage.readPackageMd5
// 是对 packages/<file>.md5 发起真实 GET 下载，而不是查询 Nexus 资产自带的 checksum。
// 此前的实现在遍历时跳过所有 .md5 文件，导致安装任意服务时报
// "package xxx.md5 does not exists"。
func TestRepositoryUploadBatch_RawUploadsMd5Sidecar(t *testing.T) {
	tmpDir := t.TempDir()
	rawPackagesDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(rawPackagesDir, 0o755))

	pkgPath := filepath.Join(rawPackagesDir, "apache-doris-4.0.6-bin-x64.tar.gz")
	md5Path := pkgPath + ".md5"
	require.NoError(t, os.WriteFile(pkgPath, []byte("fake-package-content"), 0o644))
	require.NoError(t, os.WriteFile(md5Path, []byte("deadbeefdeadbeefdeadbeefdeadbeef"), 0o644))

	var uploadedFilenames []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/service/rest/v1/search/assets":
			// 模拟 Nexus 上尚无任何资产，强制走上传分支。
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"items":[]}`))
		case r.URL.Path == "/service/rest/internal/ui/upload/raw":
			require.NoError(t, r.ParseMultipartForm(10<<20))
			filenames := r.MultipartForm.Value["asset0.filename"]
			require.Len(t, filenames, 1)
			uploadedFilenames = append(uploadedFilenames, filenames[0])
			w.WriteHeader(http.StatusOK)
		default:
			t.Fatalf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
	}))
	defer server.Close()

	task := &UploadRegistry{
		ProductPackagesPath: tmpDir,
		Username:            "admin",
		Password:            "admin",
	}
	success, fail := task.repositoryUploadBatch(server.URL)

	assert.Equal(t, 0, fail)
	assert.Equal(t, 2, success)
	assert.ElementsMatch(t, []string{
		"apache-doris-4.0.6-bin-x64.tar.gz",
		"apache-doris-4.0.6-bin-x64.tar.gz.md5",
	}, uploadedFilenames)
}

// TestRepositoryUploadBatch_DryRunDoesNotHitNetwork 覆盖回归场景：`--dry-run upload
// registry` 此前完全不检查 DryRun 状态，会真实发起 HTTP 上传（曾在生产 Nexus 上误触发
// 过一次真实上传）。DryRun=true 时不应发起任何网络请求，只打印将要上传的文件并视为成功。
func TestRepositoryUploadBatch_DryRunDoesNotHitNetwork(t *testing.T) {
	tmpDir := t.TempDir()
	rawPackagesDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(rawPackagesDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(rawPackagesDir, "valkey-8.1.8.tar.gz"), []byte("fake"), 0o644))
	// 带 .md5 sidecar 的文件会走 repositoryUploadBatch 里独立的幂等预检分支
	// （在调用 uploadFile 之前查询 Nexus），必须一并确认该分支也不发网络请求。
	require.NoError(t, os.WriteFile(filepath.Join(rawPackagesDir, "doris-4.0.6.tar.gz"), []byte("fake"), 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(rawPackagesDir, "doris-4.0.6.tar.gz.md5"), []byte("deadbeef"), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatalf("dry-run 不应发起任何网络请求，但收到了: %s %s", r.Method, r.URL.Path)
	}))
	defer server.Close()

	task := &UploadRegistry{
		ProductPackagesPath: tmpDir,
		Username:            "admin",
		Password:            "admin",
		DryRun:              true,
	}
	success, fail := task.repositoryUploadBatch(server.URL)

	assert.Equal(t, 0, fail)
	assert.Equal(t, 3, success)
}
