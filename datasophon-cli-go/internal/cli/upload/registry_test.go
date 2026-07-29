package upload

import (
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
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
	assert.Equal(t, 4, success)
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
		{"路径穿越", "raw/../../outside.txt", "", "", false},
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

func TestUploadSpecificFiles_RejectsPathTraversalAndAbsolutePath(t *testing.T) {
	tmpDir := t.TempDir()
	outsidePath := filepath.Join(filepath.Dir(tmpDir), "outside.txt")
	require.NoError(t, os.WriteFile(outsidePath, []byte("secret"), 0o644))
	t.Cleanup(func() { _ = os.Remove(outsidePath) })

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatalf("非法路径不应发起网络请求: %s %s", r.Method, r.URL.Path)
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, IsSuccessDelete: true}
	success, fail := task.uploadSpecificFiles(server.URL, []string{
		"raw/../../outside.txt",
		outsidePath,
	})

	assert.Equal(t, 0, success)
	assert.Equal(t, 2, fail)
	_, err := os.Stat(outsidePath)
	assert.NoError(t, err, "非法 --files 路径不能删除目录外文件")
}

func TestUploadSpecificFiles_RejectsSymlinkOutsideProductDirectory(t *testing.T) {
	tmpDir := t.TempDir()
	rawDir := filepath.Join(tmpDir, "raw")
	require.NoError(t, os.MkdirAll(rawDir, 0o755))
	outsidePath := filepath.Join(filepath.Dir(tmpDir), "outside-symlink-target.txt")
	require.NoError(t, os.WriteFile(outsidePath, []byte("secret"), 0o644))
	t.Cleanup(func() { _ = os.Remove(outsidePath) })
	linkPath := filepath.Join(rawDir, "linked.txt")
	if err := os.Symlink(outsidePath, linkPath); err != nil {
		t.Skipf("当前环境不支持符号链接: %v", err)
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatalf("越界符号链接不应发起网络请求: %s %s", r.Method, r.URL.Path)
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, IsSuccessDelete: true}
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/linked.txt"})

	assert.Equal(t, 0, success)
	assert.Equal(t, 1, fail)
	_, err := os.Stat(outsidePath)
	assert.NoError(t, err, "越界符号链接不能删除目标文件")
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

// TestUploadSpecificFiles_AutoGeneratesAndUploadsMissingMD5Sidecar 覆盖回归场景：
// --files 精确模式没有整目录扫描兜底，若安装包本地缺 .md5 sidecar（如本次 Gravitino
// 集成踩的坑：手动 --files 只列了 tar.gz 本体），上传后必须自动计算 MD5、写本地 sidecar
// 并补传到 Nexus，不能要求用户提前手算 md5sum。
func TestUploadSpecificFiles_AutoGeneratesAndUploadsMissingMD5Sidecar(t *testing.T) {
	tmpDir := t.TempDir()
	pkgDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(pkgDir, 0o755))
	pkgPath := filepath.Join(pkgDir, "gravitino-1.3.0-bin.tar.gz")
	require.NoError(t, os.WriteFile(pkgPath, []byte("fake-gravitino-package"), 0o644))

	var uploadedFilenames []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, "/service/rest/internal/ui/upload/raw", r.URL.Path)
		require.NoError(t, r.ParseMultipartForm(10<<20))
		uploadedFilenames = append(uploadedFilenames, r.MultipartForm.Value["asset0.filename"][0])
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, Username: "admin", Password: "admin"}
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/packages/gravitino-1.3.0-bin.tar.gz"})

	assert.Equal(t, 0, fail)
	assert.Equal(t, 1, success)
	assert.ElementsMatch(t, []string{
		"gravitino-1.3.0-bin.tar.gz",
		"gravitino-1.3.0-bin.tar.gz.md5",
	}, uploadedFilenames)

	md5Bytes, err := os.ReadFile(pkgPath + ".md5")
	require.NoError(t, err, "本地应自动生成 .md5 sidecar 文件")
	wantSum, err := localMD5(pkgPath)
	require.NoError(t, err)
	assert.Equal(t, wantSum+"\n", string(md5Bytes))
}

func TestUploadSpecificFiles_RefreshesMD5AndFailsWhenSidecarUploadFails(t *testing.T) {
	tmpDir := t.TempDir()
	pkgDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(pkgDir, 0o755))
	pkgPath := filepath.Join(pkgDir, "gravitino-1.3.0-bin.tar.gz")
	require.NoError(t, os.WriteFile(pkgPath, []byte("new-package-content"), 0o644))
	require.NoError(t, os.WriteFile(pkgPath+".md5", []byte("stale-md5\n"), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.NoError(t, r.ParseMultipartForm(10<<20))
		filename := r.MultipartForm.Value["asset0.filename"][0]
		if strings.HasSuffix(filename, ".md5") {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, Username: "admin", Password: "admin", IsSuccessDelete: true}
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/packages/gravitino-1.3.0-bin.tar.gz"})

	assert.Equal(t, 0, success)
	assert.Equal(t, 1, fail)
	assert.FileExists(t, pkgPath, "sidecar 上传失败时不能删除主包")
	wantSum, err := localMD5(pkgPath)
	require.NoError(t, err)
	md5Bytes, err := os.ReadFile(pkgPath + ".md5")
	require.NoError(t, err)
	assert.Equal(t, wantSum+"\n", string(md5Bytes), "必须覆盖过期 sidecar")
}

func TestRepositoryUploadBatch_FailsWhenPackageSidecarUploadFails(t *testing.T) {
	tmpDir := t.TempDir()
	pkgDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(pkgDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(pkgDir, "gravitino-1.3.0-bin.tar.gz"), []byte("package-content"), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/service/rest/v1/search/assets":
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"items":[]}`))
		case "/service/rest/internal/ui/upload/raw":
			require.NoError(t, r.ParseMultipartForm(10<<20))
			filename := r.MultipartForm.Value["asset0.filename"][0]
			if strings.HasSuffix(filename, ".md5") {
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
			w.WriteHeader(http.StatusOK)
		default:
			t.Fatalf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, Username: "admin", Password: "admin"}
	success, fail := task.repositoryUploadBatch(server.URL)

	assert.Equal(t, 1, success)
	assert.Equal(t, 1, fail)
}

func TestUploadRegistry_DoRunReturnsErrorWhenSpecificPackageSidecarFails(t *testing.T) {
	tmpDir := t.TempDir()
	pkgDir := filepath.Join(tmpDir, "raw", "packages")
	require.NoError(t, os.MkdirAll(pkgDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(pkgDir, "gravitino-1.3.0-bin.tar.gz"), []byte("package-content"), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.NoError(t, r.ParseMultipartForm(10<<20))
		filename := r.MultipartForm.Value["asset0.filename"][0]
		if strings.HasSuffix(filename, ".md5") {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	serverURL, err := url.Parse(server.URL)
	require.NoError(t, err)
	host, port, err := net.SplitHostPort(serverURL.Host)
	require.NoError(t, err)

	task := &UploadRegistry{
		ProductPackagesPath: tmpDir,
		WebHost:             host,
		WebPort:             port,
		Username:            "admin",
		Password:            "admin",
		Files:               []string{"raw/packages/gravitino-1.3.0-bin.tar.gz"},
	}
	task.EnableRegistry = true

	require.Error(t, task.doRun(nil))
}

// TestUploadSpecificFiles_DoesNotGenerateMD5ForMetaFiles 覆盖边界：service_ddl.json /
// 模板 / SQL 等元数据文件不在 raw/packages/ 下，Worker 不会对它们做 md5 校验，不应被
// 误加 sidecar（否则每次上传 DDL 都会多一次无意义的 .md5 请求）。
func TestUploadSpecificFiles_DoesNotGenerateMD5ForMetaFiles(t *testing.T) {
	tmpDir := t.TempDir()
	metaDir := filepath.Join(tmpDir, "raw", "meta", "datacluster-physical", "GRAVITINO")
	require.NoError(t, os.MkdirAll(metaDir, 0o755))
	ddlPath := filepath.Join(metaDir, "service_ddl.json")
	require.NoError(t, os.WriteFile(ddlPath, []byte(`{}`), 0o644))

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	task := &UploadRegistry{ProductPackagesPath: tmpDir, Username: "admin", Password: "admin"}
	success, fail := task.uploadSpecificFiles(server.URL, []string{"raw/meta/datacluster-physical/GRAVITINO/service_ddl.json"})

	assert.Equal(t, 0, fail)
	assert.Equal(t, 1, success)
	_, err := os.Stat(ddlPath + ".md5")
	assert.True(t, os.IsNotExist(err), "元数据文件不应生成 .md5 sidecar")
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
