package plan

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
)

func TestRegistryTaskDryRunDoesNotCallNexusREST(t *testing.T) {
	var requestCount atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	task := &registryTask{
		EnableRegistry:  true,
		EnableMetrics:   true,
		WebHost:         server.URL,
		MetricsUser:     "metrics",
		MetricsPassword: "metrics-secret",
	}
	if err := task.doRun(executor.NewLocalExecutor(true)); err != nil {
		t.Fatal(err)
	}
	if got := requestCount.Load(); got != 0 {
		t.Fatalf("dry-run must not issue Nexus REST requests, got %d", got)
	}
}
