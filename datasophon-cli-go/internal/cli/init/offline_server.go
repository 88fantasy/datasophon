package initcmd

import (
	"fmt"
	"log/slog"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitOfflineServer 对应 Java InitOfflineServer — 配置本机 httpd/apache2 作为 yum/apt 离线源。
type InitOfflineServer struct {
	TaskBase
	PackagePath string
	ServerIP    string
	ServerPort  string
}

func (t *InitOfflineServer) Name() string { return "离线源Server配置" }

func (t *InitOfflineServer) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitOfflineServer) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "offlineServer",
		Short: "配置 httpd/apache2 离线源服务端",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().StringVarP(&t.PackagePath, "packagePath", "p", "", "安装包目录（必填）")
	cmd.Flags().StringVar(&t.ServerIP, "serverIp", "", "httpd 服务 IP（必填）")
	cmd.Flags().StringVar(&t.ServerPort, "serverPort", "", "httpd 服务端口（必填）")
	_ = cmd.MarkFlagRequired("packagePath")
	_ = cmd.MarkFlagRequired("serverIp")
	_ = cmd.MarkFlagRequired("serverPort")
	return cmd
}

func (t *InitOfflineServer) doRun(exec executor.Executor) bool {
	if t.EnableRegistry {
		slog.Info("enableRegistry=true，offlineServer 不需要", "enableRegistry", t.EnableRegistry)
		return true
	}
	osType := exec.GetOs()
	archType := exec.GetArch()
	httpRootPath := fmt.Sprintf("%s/os", t.PackagePath)
	repoOsPath := fmt.Sprintf("%s/os/%s/%s", t.PackagePath, string(archType), string(osType))

	if !exec.Exists(httpRootPath).Success {
		slog.Error("目录不存在", "path", httpRootPath)
		return false
	}
	if !exec.Exists(repoOsPath).Success {
		slog.Error("目录不存在", "path", repoOsPath)
		return false
	}

	if osType.IsUbuntu() {
		exec.ExecShell("dpkg --configure -a")
		fileRepoURL := fmt.Sprintf("file://%s", repoOsPath)
		AptRepoConfFile(exec, fileRepoURL)
		exec.ExecShell("apt clean")
		if r := exec.ExecShell("apt update"); !r.Success {
			panic("apt update 失败")
		}
		if r := exec.ExecShell("apt -y install apache2"); !r.Success {
			panic("apt -y install apache2 失败")
		}
		if r := exec.ExecShell("apache2 -v"); r.Success {
			exec.ExecShell(fmt.Sprintf("sed -i 's|DocumentRoot /var/www/html|DocumentRoot %s|g' /etc/apache2/sites-available/000-default.conf", httpRootPath))
			exec.ExecShell(fmt.Sprintf("sed -i 's|<VirtualHost \\*:80>|<VirtualHost *:%s>|g' /etc/apache2/sites-available/000-default.conf", t.ServerPort))
			exec.ExecShell(fmt.Sprintf("sed -i 's|<Directory /var/www/>|<Directory %s>|g' /etc/apache2/apache2.conf", httpRootPath))
			exec.ExecShell(fmt.Sprintf("sed -i 's|Listen 80|Listen %s|g' /etc/apache2/ports.conf", t.ServerPort))
		}
		if r := exec.ExecShell("systemctl restart apache2"); !r.Success {
			panic("apache2 启动失败")
		}
	} else if osType.IsCentos() {
		fileRepoURL := fmt.Sprintf("file://%s", repoOsPath)
		YumRepoConfFile(exec, fileRepoURL)
		exec.ExecShell("yum clean all")
		if r := exec.ExecShell("yum makecache"); !r.Success {
			panic("yum makecache 失败")
		}
		if r := exec.ExecShell("yum install -y httpd"); !r.Success {
			panic("yum install httpd 失败")
		}
		httpdConfPath := "/etc/httpd/conf/httpd.conf"
		exec.ExecShell(fmt.Sprintf("sed -i 's|^DocumentRoot \"/var/www/html\"|DocumentRoot \"%s\"|g' %s", httpRootPath, httpdConfPath))
		exec.ExecShell(fmt.Sprintf("sed -i 's|^<Directory \"/var/www/html\">|<Directory \"%s\">|g' %s", httpRootPath, httpdConfPath))
		exec.ExecShell(fmt.Sprintf("sed -i 's|^Listen 80|Listen %s|g' %s", t.ServerPort, httpdConfPath))
		if r := exec.ExecShell("systemctl restart httpd"); !r.Success {
			panic("httpd 启动失败")
		}
	} else {
		slog.Error("不支持的 OS", "os", string(osType))
		return false
	}
	return true
}

// YumRepoConfFile 配置 yum 离线源（共享给 offlineSlave 使用）。
func YumRepoConfFile(exec executor.Executor, baseURL string) {
	exec.ExecShell("mv /etc/yum.repos.d /etc/yum.repos.d.$(date +%Y%m%d.%H%M%S)")
	exec.ExecShell("mkdir /etc/yum.repos.d")
	exec.WriteLines([]string{
		"[LOCAL-REPO]",
		"name=LOCAL-REPO",
		fmt.Sprintf("baseurl=%s", baseURL),
		"enabled=1",
		"gpgcheck=0",
	}, "/etc/yum.repos.d/local_base.repo")
	slog.Info("yum 离线源已配置", "baseurl", baseURL)
}

// AptRepoConfFile 配置 apt 离线源（共享给 offlineSlave 使用）。
func AptRepoConfFile(exec executor.Executor, baseURL string) {
	exec.ExecShell("mv /etc/apt/sources.list /etc/apt/sources.list.bak")
	exec.ExecShell("mv /etc/apt/sources.list.d/rightscale_extra.sources.list /etc/apt/sources.list.d/rightscale_extra.sources.list.bak 2>/dev/null || true")
	exec.WriteLines([]string{
		fmt.Sprintf("deb [trusted=yes] %s ./", baseURL),
	}, "/etc/apt/sources.list")
	slog.Info("apt 离线源已配置", "baseurl", baseURL)
}
