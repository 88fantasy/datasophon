package handler

import (
	"fmt"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"time"

	"golang.org/x/crypto/ssh"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// Chain 对应 Java InitNodeHandlerChain。
// 单个 *ssh.Client 跨所有 handler 复用，对齐 Java JSchSession 复用语义。
type Chain struct {
	host     *config.Host
	authType config.SSHAuthType
	handlers []Handler
	dryRun   bool
}

func NewChain(host *config.Host, authType config.SSHAuthType, dryRun bool) *Chain {
	return &Chain{host: host, authType: authType, dryRun: dryRun}
}

func (c *Chain) Add(h Handler) *Chain {
	c.handlers = append(c.handlers, h)
	return c
}

func (c *Chain) Handle() error {
	client, err := dialSSH(c.host, c.authType)
	if err != nil {
		return fmt.Errorf("SSH 连接失败 %s: %w", c.host.IP, err)
	}
	defer client.Close()

	for _, h := range c.handlers {
		slog.Info("执行处理器开始", "name", h.Name(), "host", c.host.Hostname)
		if err := h.Handle(client, c.dryRun); err != nil {
			slog.Error("执行处理器失败", "name", h.Name(), "host", c.host.Hostname, "err", err)
			return err
		}
		slog.Info("执行处理器结束", "name", h.Name(), "host", c.host.Hostname)
	}
	return nil
}

func dialSSH(host *config.Host, authType config.SSHAuthType) (*ssh.Client, error) {
	port := host.Port
	if port == 0 {
		port = 22
	}

	var auth []ssh.AuthMethod
	switch authType {
	case config.SSHAuthTypePublicKey:
		signer, err := loadPrivateKey()
		if err != nil {
			return nil, fmt.Errorf("加载 SSH 私钥失败: %w", err)
		}
		auth = []ssh.AuthMethod{ssh.PublicKeys(signer)}
	case config.SSHAuthTypePassword:
		auth = []ssh.AuthMethod{ssh.Password(host.Password)}
	default: // AUTO：先尝试密钥，失败则用密码
		if signer, err := loadPrivateKey(); err == nil {
			auth = append(auth, ssh.PublicKeys(signer))
		}
		auth = append(auth, ssh.Password(host.Password))
	}

	cfg := &ssh.ClientConfig{
		User:            host.User,
		Auth:            auth,
		HostKeyCallback: ssh.InsecureIgnoreHostKey(), //nolint:gosec
		Timeout:         30 * time.Second,
	}

	addr := net.JoinHostPort(host.IP, fmt.Sprintf("%d", port))
	return ssh.Dial("tcp", addr, cfg)
}

func loadPrivateKey() (ssh.Signer, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return nil, err
	}
	keyPath := filepath.Join(home, ".ssh", "id_rsa")
	key, err := os.ReadFile(keyPath)
	if err != nil {
		return nil, err
	}
	return ssh.ParsePrivateKey(key)
}
