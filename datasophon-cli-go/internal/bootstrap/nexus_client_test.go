package bootstrap

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
)

func TestNexusClientEnsureMetricsAccountCreatesThenUpdates(t *testing.T) {
	var mu sync.Mutex
	roleExists := false
	userExists := false
	passwords := []string{}
	updates := map[string]int{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/service/rest/v1/security/roles/datasophon-metrics":
			if !roleExists {
				w.WriteHeader(http.StatusNotFound)
				return
			}
			w.WriteHeader(http.StatusOK)
		case r.Method == http.MethodPost && r.URL.Path == "/service/rest/v1/security/roles":
			roleExists = true
			w.WriteHeader(http.StatusOK)
		case r.Method == http.MethodPut && r.URL.Path == "/service/rest/v1/security/roles/datasophon-metrics":
			updates["role"]++
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/service/rest/v1/security/users":
			if userExists {
				_ = json.NewEncoder(w).Encode([]map[string]string{{"userId": "metrics"}})
			} else {
				_ = json.NewEncoder(w).Encode([]interface{}{})
			}
		case r.Method == http.MethodPost && r.URL.Path == "/service/rest/v1/security/users":
			var payload map[string]interface{}
			_ = json.NewDecoder(r.Body).Decode(&payload)
			passwords = append(passwords, payload["password"].(string))
			userExists = true
			w.WriteHeader(http.StatusOK)
		case r.Method == http.MethodPut && r.URL.Path == "/service/rest/v1/security/users/metrics":
			updates["user"]++
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPut && r.URL.Path == "/service/rest/v1/security/users/metrics/change-password":
			value, _ := io.ReadAll(r.Body)
			passwords = append(passwords, string(value))
			w.WriteHeader(http.StatusNoContent)
		default:
			http.Error(w, r.Method+" "+r.URL.String(), http.StatusNotFound)
		}
	}))
	defer server.Close()

	client := NewNexusClient(server.URL, "admin", "secret")
	client.HTTPClient = server.Client()
	if err := client.EnsureMetricsAccount("metrics", "first-password"); err != nil {
		t.Fatal(err)
	}
	if err := client.EnsureMetricsAccount("metrics", "rotated-password"); err != nil {
		t.Fatal(err)
	}
	if len(passwords) != 2 || passwords[0] != "first-password" || passwords[1] != "rotated-password" {
		t.Fatalf("passwords were not converged: %#v", passwords)
	}
	if updates["role"] != 1 || updates["user"] != 1 {
		t.Fatalf("existing resources were not updated: %#v", updates)
	}
}
