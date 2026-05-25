package initcmd

import (
	"fmt"
	"log/slog"
	"net/url"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitOfflineSlave 对应 Java InitOfflineSlave — 配置 yum/apt 指向离线源服务器。
type InitOfflineSlave struct {
	TaskBase
	ServerIP   string
	ServerPort string
}

func (t *InitOfflineSlave) Name() string { return "离线源slave配置" }

func (t *InitOfflineSlave) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitOfflineSlave) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "offlineSlave",
		Short: "配置 yum/apt 指向离线源服务器",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVar(&t.ServerIP, "serverIp", "", "离线源服务器 IP（必填）")
	cmd.Flags().StringVar(&t.ServerPort, "serverPort", "", "离线源服务器端口（必填）")
	_ = cmd.MarkFlagRequired("serverIp")
	_ = cmd.MarkFlagRequired("serverPort")
	return cmd
}

func (t *InitOfflineSlave) doRun(exec executor.Executor) bool {
	archType := exec.GetArch()
	osType := exec.GetOs()
	repoOsSuffix := fmt.Sprintf("%s/%s/", string(archType), string(osType))

	if osType.IsUbuntu() {
		if t.EnableRegistry {
			repoOsSuffix = "repository/apt/"
		}
		exec.ExecShell("dpkg --configure -a")
		AptRepoConfFile(exec, t.buildURL(repoOsSuffix))
		exec.ExecShell("apt clean")
		if r := exec.ExecShell("apt update"); !r.Success {
			panic("apt update 失败")
		}
		slog.Info("apt 离线源配置完成")
	} else if osType.IsCentos() {
		if t.EnableRegistry {
			repoOsSuffix = fmt.Sprintf("repository/yum/%s/%s/", string(archType), string(osType))
		}
		YumRepoConfFile(exec, t.buildURL(repoOsSuffix))
		exec.ExecShell("yum clean all")
		if r := exec.ExecShell("yum makecache"); !r.Success {
			panic("yum makecache 失败")
		}
		slog.Info("yum 离线源配置完成")
	} else {
		slog.Error("不支持的 OS", "os", string(osType))
		return false
	}
	return true
}

func (t *InitOfflineSlave) buildURL(suffix string) string {
	if t.RegistryUsername != "" && t.RegistryPassword != "" {
		encodedPwd := url.QueryEscape(t.RegistryPassword)
		return fmt.Sprintf("http://%s:%s@%s:%s/%s",
			t.RegistryUsername, encodedPwd, t.ServerIP, t.ServerPort, suffix)
	}
	return fmt.Sprintf("http://%s:%s/%s", t.ServerIP, t.ServerPort, suffix)
}
