package bootstrap

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const nexusMetricsRoleID = "datasophon-metrics"

// NexusClient owns authenticated Nexus bootstrap operations shared by the
// standalone create path and the create-cluster plan path.
type NexusClient struct {
	BaseURL    string
	Username   string
	Password   string
	HTTPClient *http.Client
}

func NewNexusClient(baseURL, username, password string) *NexusClient {
	return &NexusClient{
		BaseURL: strings.TrimRight(baseURL, "/"), Username: username, Password: password,
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
	}
}

// Do executes an authenticated Nexus REST request relative to BaseURL.
func (c *NexusClient) Do(method, path, contentType string, body io.Reader) (*http.Response, error) {
	return c.do(method, path, contentType, body)
}

func (c *NexusClient) do(method, path, contentType string, body io.Reader) (*http.Response, error) {
	req, err := http.NewRequest(method, c.BaseURL+path, body)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(c.Username, c.Password)
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	return c.HTTPClient.Do(req)
}

func (c *NexusClient) AcceptEULA() error {
	get := func(_, path, _, _ string) (*http.Response, error) {
		return c.do(http.MethodGet, path, "", nil)
	}
	post := func(_, path, _, _, contentType string, body io.Reader) (*http.Response, error) {
		return c.do(http.MethodPost, path, contentType, body)
	}
	return AcceptNexusEULA(c.BaseURL, c.Username, c.Password, get, post)
}

// EnsureMetricsAccount converges the fixed role and configured user. Existing
// resources are updated, including password rotation, instead of being skipped.
func (c *NexusClient) EnsureMetricsAccount(metricsUser, metricsPassword string) error {
	if strings.TrimSpace(metricsUser) == "" || metricsPassword == "" {
		return fmt.Errorf("metrics 用户名或密码为空")
	}
	rolePayload := map[string]interface{}{
		"id": nexusMetricsRoleID, "name": "Datasophon Metrics",
		"description": "Read Nexus metrics", "privileges": []string{"nx-metrics-all"},
		"roles": []string{},
	}
	rolePath := "/service/rest/v1/security/roles/" + url.PathEscape(nexusMetricsRoleID)
	if err := c.upsertJSON(rolePath, "/service/rest/v1/security/roles", rolePayload, "metrics role", ""); err != nil {
		return err
	}

	userPayload := map[string]interface{}{
		"userId": metricsUser, "firstName": "Datasophon", "lastName": "Metrics",
		"emailAddress": "metrics@localhost", "status": "active", "roles": []string{nexusMetricsRoleID},
	}
	userQuery := "/service/rest/v1/security/users?userId=" + url.QueryEscape(metricsUser) + "&source=default"
	exists, err := c.resourceExists(userQuery, metricsUser)
	if err != nil {
		return fmt.Errorf("查询 metrics user 失败: %w", err)
	}
	if !exists {
		userPayload["password"] = metricsPassword
		if err := c.createJSON("/service/rest/v1/security/users", userPayload, "metrics user"); err != nil {
			return err
		}
		return nil
	}
	userPath := "/service/rest/v1/security/users/" + url.PathEscape(metricsUser)
	if err := c.updateJSON(userPath, userPayload, "metrics user"); err != nil {
		return err
	}
	resp, err := c.do(http.MethodPut, userPath+"/change-password", "text/plain", strings.NewReader(metricsPassword))
	if err != nil {
		return fmt.Errorf("更新 metrics user 密码失败: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		return responseError("更新 metrics user 密码", resp)
	}
	return nil
}

func (c *NexusClient) upsertJSON(getPath, createPath string, payload interface{}, name, expectedUser string) error {
	exists, err := c.resourceExists(getPath, expectedUser)
	if err != nil {
		return fmt.Errorf("查询 %s 失败: %w", name, err)
	}
	if exists {
		return c.updateJSON(getPath, payload, name)
	}
	return c.createJSON(createPath, payload, name)
}

func (c *NexusClient) createJSON(path string, payload interface{}, name string) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("构造 %s 请求失败: %w", name, err)
	}
	resp, err := c.do(http.MethodPost, path, "application/json", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("创建 %s 请求失败: %w", name, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return responseError("创建 "+name, resp)
	}
	return nil
}

func (c *NexusClient) updateJSON(path string, payload interface{}, name string) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("构造 %s 更新请求失败: %w", name, err)
	}
	resp, err := c.do(http.MethodPut, path, "application/json", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("更新 %s 请求失败: %w", name, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent && resp.StatusCode != http.StatusOK {
		return responseError("更新 "+name, resp)
	}
	return nil
}

func (c *NexusClient) resourceExists(path, expectedUser string) (bool, error) {
	resp, err := c.do(http.MethodGet, path, "", nil)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()
	switch resp.StatusCode {
	case http.StatusOK:
		if expectedUser == "" {
			return true, nil
		}
		var users []struct {
			UserID string `json:"userId"`
		}
		if err := json.NewDecoder(resp.Body).Decode(&users); err != nil {
			return false, fmt.Errorf("解析用户查询响应失败: %w", err)
		}
		for _, user := range users {
			if user.UserID == expectedUser {
				return true, nil
			}
		}
		return false, nil
	case http.StatusNotFound:
		return false, nil
	default:
		return false, responseError("查询资源", resp)
	}
}

func responseError(action string, resp *http.Response) error {
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	return fmt.Errorf("%s 失败: HTTP %d: %s", action, resp.StatusCode, strings.TrimSpace(string(body)))
}
