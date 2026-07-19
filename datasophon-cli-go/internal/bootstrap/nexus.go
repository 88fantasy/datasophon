package bootstrap

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

type AuthenticatedGet func(baseURL, path, username, password string) (*http.Response, error)
type AuthenticatedPost func(baseURL, path, username, password, contentType string, body io.Reader) (*http.Response, error)

// AcceptNexusEULA 从 Nexus 获取 disclaimer 原文并原样回传，避免两条初始化路径行为分叉。
func AcceptNexusEULA(baseURL, username, password string, get AuthenticatedGet, post AuthenticatedPost) error {
	type eulaState struct {
		Accepted   bool   `json:"accepted"`
		Disclaimer string `json:"disclaimer"`
	}

	getResponse, err := get(baseURL, "/service/rest/v1/system/eula", username, password)
	if err != nil {
		return fmt.Errorf("获取 EULA 状态失败: %w", err)
	}
	defer getResponse.Body.Close()
	if getResponse.StatusCode != http.StatusOK {
		return fmt.Errorf("获取 EULA 状态失败: HTTP %d", getResponse.StatusCode)
	}

	var state eulaState
	if err := json.NewDecoder(getResponse.Body).Decode(&state); err != nil {
		return fmt.Errorf("解析 EULA 状态失败: %w", err)
	}
	if state.Accepted {
		return nil
	}

	payload, err := json.Marshal(eulaState{Accepted: true, Disclaimer: state.Disclaimer})
	if err != nil {
		return fmt.Errorf("构造 EULA 请求失败: %w", err)
	}
	postResponse, err := post(baseURL, "/service/rest/v1/system/eula",
		username, password, "application/json", bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("提交 EULA 失败: %w", err)
	}
	defer postResponse.Body.Close()
	if postResponse.StatusCode != http.StatusNoContent {
		return fmt.Errorf("提交 EULA 失败: HTTP %d", postResponse.StatusCode)
	}
	return nil
}
