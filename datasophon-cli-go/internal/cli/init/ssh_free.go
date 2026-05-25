package initcmd

import (
	"fmt"
	"log/slog"
	"os"
	"os/exec"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

// InitSshFree 对应 Java InitSsh（不继承 InitBase，不实现 handler.Handler）。
// 本地执行：生成密钥对 + 向所有节点分发公钥实现 SSH 免密登录。
type InitSshFree struct {
	ConfigFilePath string
	ConfigPassword string
}

func (t *InitSshFree) run(dryRun bool) error {
	cfg, err := config.Load(t.ConfigFilePath, t.ConfigPassword)
	if err != nil {
		return err
	}

	home, _ := os.UserHomeDir()
	keyPath := home + "/.ssh/id_rsa"
	if _, err := os.Stat(keyPath); os.IsNotExist(err) {
		slog.Info("生成 SSH 密钥对")
		if dryRun {
			slog.Info("[dry-run] ssh-keygen -t rsa -b 4096 -N '' -f " + keyPath)
		} else {
			c := exec.Command("ssh-keygen", "-t", "rsa", "-b", "4096", "-N", "", "-f", keyPath)
			c.Stdout = os.Stdout
			c.Stderr = os.Stderr
			if err := c.Run(); err != nil {
				return fmt.Errorf("ssh-keygen 失败: %w", err)
			}
		}
	}

	for _, node := range cfg.Nodes {
		port := node.Port
		if port == 0 {
			port = 22
		}
		copyCmd := fmt.Sprintf("ssh-copy-id -i %s.pub -p %d -o StrictHostKeyChecking=no %s@%s",
			keyPath, port, node.User, node.IP)
		slog.Info("分发公钥", "host", node.Hostname, "cmd", copyCmd)
		if !dryRun {
			c := exec.Command("bash", "-c", copyCmd)
			c.Stdin = os.Stdin
			c.Stdout = os.Stdout
			c.Stderr = os.Stderr
			if err := c.Run(); err != nil {
				slog.Warn("公钥分发失败，继续下一节点", "host", node.Hostname, "err", err)
			}
		}
	}
	slog.Info("SSH 免密配置完成")
	return nil
}

func NewInitSshFreeCommand(dryRun *bool) *cobra.Command {
	task := &InitSshFree{}
	cmd := &cobra.Command{
		Use:   "ssh",
		Short: "配置 SSH 免密登录（本地执行，向所有节点分发公钥）",
		RunE: func(cmd *cobra.Command, args []string) error {
			return task.run(*dryRun)
		},
	}
	cmd.Flags().StringVarP(&task.ConfigFilePath, "config", "c", "", "配置文件路径 (必填)")
	cmd.Flags().StringVar(&task.ConfigPassword, "cpassword", "", "配置文件加密密钥")
	_ = cmd.MarkFlagRequired("config")
	return cmd
}
