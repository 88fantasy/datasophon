package initcmd

import (
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/osinfo"
)

// ─── mock Executor ────────────────────────────────────────────────────────────

type mockExec struct {
	existsResult  bool
	writeSuccess  bool
	writtenBody   []byte
}

func (m *mockExec) ExecShell(_ string) executor.ExecResult      { return executor.Succeed("") }
func (m *mockExec) Exists(_ string) executor.ExecResult {
	if m.existsResult {
		return executor.Succeed("exists")
	}
	return executor.ExecResult{}
}
func (m *mockExec) SendFile(_, _ string, _ bool) executor.ExecResult { return executor.Succeed("") }
func (m *mockExec) SendDir(_, _ string, _ bool) executor.ExecResult  { return executor.Succeed("") }
func (m *mockExec) GetFileString(_ string) executor.ExecResult       { return executor.Succeed("") }
func (m *mockExec) WriteFromStream(in io.Reader, _ string) executor.ExecResult {
	data, err := io.ReadAll(in)
	if err != nil || !m.writeSuccess {
		return executor.Fail("write failed")
	}
	m.writtenBody = data
	return executor.Succeed("")
}
func (m *mockExec) WriteLines(_ []string, _ string) executor.ExecResult { return executor.Succeed("") }
func (m *mockExec) GetArch() osinfo.ArchType                            { return osinfo.ArchX86_64 }
func (m *mockExec) GetOs() osinfo.OsType                                { return osinfo.OsTypeCentos7 }

// ─── helpers ─────────────────────────────────────────────────────────────────

func restoreHTTPClient(orig *http.Client) func() {
	return func() { httpClient = orig }
}

// ─── tests ───────────────────────────────────────────────────────────────────

func TestDownloadFromRegistry_FileExistsSkip(t *testing.T) {
	orig := httpClient
	t.Cleanup(restoreHTTPClient(orig))

	requestCount := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount++
	}))
	t.Cleanup(srv.Close)
	httpClient = srv.Client()

	exec := &mockExec{existsResult: true, writeSuccess: true}
	err := DownloadFromRegistry(exec, true, "127.0.0.1", "8081", "u", "p", "pkg.tar", "/dist/pkg.tar", true)
	assert.NoError(t, err)
	assert.Equal(t, 0, requestCount, "文件已存在时不应发起 HTTP 请求")
}

func TestDownloadFromRegistry_RegistryDisabledSkip(t *testing.T) {
	orig := httpClient
	t.Cleanup(restoreHTTPClient(orig))

	requestCount := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount++
	}))
	t.Cleanup(srv.Close)
	httpClient = srv.Client()

	exec := &mockExec{existsResult: false, writeSuccess: true}
	err := DownloadFromRegistry(exec, false, "127.0.0.1", "8081", "u", "p", "pkg.tar", "/dist/pkg.tar", false)
	assert.NoError(t, err)
	assert.Equal(t, 0, requestCount, "enableRegistry=false 时不应发起 HTTP 请求")
}

func TestDownloadFromRegistry_NormalFlow(t *testing.T) {
	orig := httpClient
	t.Cleanup(restoreHTTPClient(orig))

	body := []byte("binary content")
	var receivedAuth string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedAuth = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(body)
	}))
	t.Cleanup(srv.Close)
	httpClient = srv.Client()

	// 将 registryIP:Port 替换成 httptest server 地址
	exec := &mockExec{existsResult: false, writeSuccess: true}

	// 注意：url 格式是 http://<ip>:<port>/repository/raw/packages/<name>
	// 我们用 httptest server URL 的 host:port
	host := srv.Listener.Addr().String()
	ip := host[:len(host)-len(":"+portOf(host))]
	port := portOf(host)

	err := DownloadFromRegistry(exec, true, ip, port, "admin", "secret", "pkg.tar", "/dist/pkg.tar", false)
	require.NoError(t, err)
	assert.NotEmpty(t, receivedAuth, "应带 Basic Auth")
	assert.Equal(t, body, exec.writtenBody)
}

func TestDownloadFromRegistry_HTTP4xx(t *testing.T) {
	orig := httpClient
	t.Cleanup(restoreHTTPClient(orig))

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	t.Cleanup(srv.Close)
	httpClient = srv.Client()

	host := srv.Listener.Addr().String()
	ip := host[:len(host)-len(":"+portOf(host))]
	port := portOf(host)

	exec := &mockExec{existsResult: false, writeSuccess: true}
	err := DownloadFromRegistry(exec, true, ip, port, "", "", "pkg.tar", "/dist/pkg.tar", false)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "404")
}

func TestDownloadFromRegistry_WriteFailure(t *testing.T) {
	orig := httpClient
	t.Cleanup(restoreHTTPClient(orig))

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("data"))
	}))
	t.Cleanup(srv.Close)
	httpClient = srv.Client()

	host := srv.Listener.Addr().String()
	ip := host[:len(host)-len(":"+portOf(host))]
	port := portOf(host)

	exec := &mockExec{existsResult: false, writeSuccess: false}
	err := DownloadFromRegistry(exec, true, ip, port, "", "", "pkg.tar", "/dist/pkg.tar", false)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "写入制品失败")
}

// portOf 从 "host:port" 提取端口字符串
func portOf(addr string) string {
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			return addr[i+1:]
		}
	}
	return ""
}
