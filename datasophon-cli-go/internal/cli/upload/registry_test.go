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

// TestResolveRepoTypeAndDir 覆盖 --files 路径推导规则：yum/apt 需要 <arch>/<os>/<file>
// 至少三段，raw 允许直接在根目录下，helm 无 directory，docker 与未知前缀均判定不支持。
func TestResolveRepoTypeAndDir(t *testing.T) {
	cases := []struct {
		name          string
		relFile       string
		wantRepoType  string
		wantDirectory string
		wantOK        bool
	}{
		{"raw 带子目录", "raw/meta/datacluster-physical/DORIS/service_ddl.json", "raw", "/meta/datacluster-physical/DORIS", true},
		{"raw 根目录文件", "raw/packages/jdk.tar.gz", "raw", "/packages", true},
		{"yum 三段", "yum/x86_64/openEuler22.03/foo.rpm", "yum", "x86_64/openEuler22.03", true},
		{"yum 带 repodata 子目录", "yum/x86_64/openEuler22.03/repodata/repomd.xml", "yum", "x86_64/openEuler22.03/repodata", true},
		{"yum 段数不够", "yum/x86_64/foo.rpm", "yum", "", false},
		{"apt 三段", "apt/x86_64/ubuntu22.04/foo.deb", "apt", "x86_64/ubuntu22.04", true},
		{"helm", "helm/mychart-1.0.0.tgz", "helm", "", true},
		{"docker 不支持", "docker/image.tar", "docker", "", false},
		{"未知前缀", "conf/foo.yml", "", "", false},
		{"无前缀单段", "foo.txt", "", "", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			repoType, directory, ok := resolveRepoTypeAndDir(c.relFile)
			assert.Equal(t, c.wantRepoType, repoType)
			assert.Equal(t, c.wantDirectory, directory)
			assert.Equal(t, c.wantOK, ok)
		})
	}
}

// TestUploadSpecificFiles_RawFileForcesUploadRegardlessOfRemoteMd5 覆盖回归场景：--files
// 指定的文件必须无条件强制上传，即使 Nexus 上已有同名资产且 MD5 相同（模拟内容被改过但
// 文件名未变的元数据文件场景），不能因为幂等检查而被静默跳过。
func TestUploadSpecificFiles_RawFileForcesUploadRegardlessOfRemoteMd5(t *testing.T) {
	tmpDir := t.TempDir()
	rawDir := filepath.Join(tmpDir, "raw", "meta", "datacluster-physical", "DORIS")
	require.NoError(t, os.MkdirAll(rawDir, 0o755))
	ddlPath := filepath.Join(rawDir, "service_ddl.json")
	require.NoError(t, os.WriteFile(ddlPath, []byte(`{"port":true}`), 0o644))

	var uploadedDirs []string
	var searchCalled bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/service/rest/v1/search/assets":
			// force=true 时 uploadFile 不应查询远端 MD5；若调用到这里说明幂等检查逻辑被误触发。
			searchCalled = true
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"items":[{"checksum":{"md5":"whatever"}}]}`))
		case r.URL.Path == "/service/rest/internal/ui/upload/raw":
			require.NoError(t, r.ParseMultipartForm(10<<20))
			dirs := r.MultipartForm.Value["directory"]
			require.Len(t, dirs, 1)
			uploadedDirs = append(uploadedDirs, dirs[0])
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
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/meta/datacluster-physical/DORIS/service_ddl.json"})

	assert.Equal(t, 0, fail)
	assert.Equal(t, 1, success)
	assert.False(t, searchCalled, "force=true 时不应查询远端 MD5")
	assert.Equal(t, []string{"/meta/datacluster-physical/DORIS"}, uploadedDirs)
}

// TestUploadSpecificFiles_UnsupportedAndMissingFilesCountAsFail 覆盖 docker 镜像、
// 无法识别前缀、文件不存在三种场景均计入 fail 且不影响其余文件正常上传。
func TestUploadSpecificFiles_UnsupportedAndMissingFilesCountAsFail(t *testing.T) {
	tmpDir := t.TempDir()
	rawDir := filepath.Join(tmpDir, "raw")
	require.NoError(t, os.MkdirAll(rawDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(rawDir, "ok.txt"), []byte("ok"), 0o644))
	require.NoError(t, os.MkdirAll(filepath.Join(tmpDir, "docker"), 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(tmpDir, "docker", "image.tar"), []byte("fake"), 0o644))
	require.NoError(t, os.MkdirAll(filepath.Join(tmpDir, "conf"), 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(tmpDir, "conf", "unknown.yml"), []byte("k: v"), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	task := &UploadRegistry{
		ProductPackagesPath: tmpDir,
		Username:            "admin",
		Password:            "admin",
	}
	success, fail := task.uploadSpecificFiles(server.URL, []string{
		"raw/ok.txt",
		"docker/image.tar",
		"conf/unknown.yml",
		"raw/does-not-exist.txt",
	})

	assert.Equal(t, 1, success)
	assert.Equal(t, 3, fail)
}

// TestUploadSpecificFiles_DryRunDoesNotHitNetwork 覆盖 --dry-run 与 --files 组合时
// 不应发起任何真实网络请求。
func TestUploadSpecificFiles_DryRunDoesNotHitNetwork(t *testing.T) {
	tmpDir := t.TempDir()
	rawDir := filepath.Join(tmpDir, "raw")
	require.NoError(t, os.MkdirAll(rawDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(rawDir, "service_ddl.json"), []byte("{}"), 0o644))

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
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/service_ddl.json"})

	assert.Equal(t, 0, fail)
	assert.Equal(t, 1, success)
}
