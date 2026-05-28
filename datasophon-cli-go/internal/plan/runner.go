package plan

import (
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// runActions 顺序执行 []Action，每个 action 建立一个 SSH chain。
func runActions(actions []Action, sshAuthType config.SSHAuthType, dryRun bool) error {
	for _, a := range actions {
		if err := runSingleAction(a, sshAuthType, dryRun); err != nil {
			return err
		}
	}
	return nil
}

// runSingleAction 对单个节点建 SSH chain 并执行 handler。
func runSingleAction(a Action, sshAuthType config.SSHAuthType, dryRun bool) error {
	if a.Host == nil {
		slog.Warn("节点为 nil，跳过", "handler", a.Handler.Name())
		return nil
	}
	slog.Info("在节点执行", "host", a.HostKey, "handler", a.Handler.Name())
	chain := handler.NewChain(a.Host, sshAuthType, dryRun)
	chain.Add(a.Handler)
	return chain.Handle()
}

// singleHostAction 把 (host, handler) 包装为单元素 []Action。
func singleHostAction(host *config.Host, h handler.Handler) []Action {
	if host == nil {
		return nil
	}
	return []Action{{HostKey: host.Hostname, Host: host, Handler: h}}
}

// requireNode 从 globalNodes 查找 hostname 对应节点，找不到返回错误。
func requireNode(globalNodes map[string]*config.Host, hostname string) (*config.Host, error) {
	node, ok := globalNodes[hostname]
	if !ok {
		return nil, fmt.Errorf("节点 %q 不在 nodes 列表中", hostname)
	}
	return node, nil
}
