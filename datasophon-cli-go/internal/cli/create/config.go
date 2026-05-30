package create

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"log/slog"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"text/template"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
)

const (
	passwordCharset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%&*+-_"
	passwordLength  = 12
)

type templateData struct {
	ClusterType       string
	IsKubernetes      bool
	RegistryPassword  string
	MySQLRootPassword string
	RustfsPassword    string
	CurrentNodeIP     string
}

type createConfigCmd struct {
	OutputPath string
	Force      bool
	typeFlag   string
}

func NewConfigCommand() *cobra.Command {
	c := &createConfigCmd{}
	cmd := &cobra.Command{
		Use:   "config",
		Short: "生成集群配置文件",
		Long:  `从内置配置模板生成集群配置文件，自动填充随机密码和本机 IP。输出到当前目录的 cluster-config.yml`,
		RunE: func(cmd *cobra.Command, args []string) error {
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.OutputPath, "output", "o", "cluster-config.yml", "输出文件路径")
	cmd.Flags().BoolVarP(&c.Force, "force", "f", false, "强制覆盖已存在的文件")
	cmd.Flags().StringVarP(&c.typeFlag, "type", "t", "", "集群类型: hadoop | kubernetes (必填)")
	_ = cmd.MarkFlagRequired("type")

	return cmd
}

func (c *createConfigCmd) run() error {
	if c.typeFlag != "hadoop" && c.typeFlag != "kubernetes" {
		return fmt.Errorf("--type 必须是 hadoop 或 kubernetes，当前值: %q", c.typeFlag)
	}

	absOutputPath, err := filepath.Abs(c.OutputPath)
	if err != nil {
		return fmt.Errorf("获取输出文件绝对路径失败: %w", err)
	}

	if _, err := os.Stat(absOutputPath); err == nil && !c.Force {
		return fmt.Errorf("输出文件已存在: %s (使用 --force 强制覆盖)", absOutputPath)
	}

	tplBytes, err := config.GetClusterConfigTemplate()
	if err != nil {
		return fmt.Errorf("获取配置模板失败: %w", err)
	}

	regPwd, err := generatePassword(passwordLength)
	if err != nil {
		return err
	}
	mysqlPwd, err := generatePassword(passwordLength)
	if err != nil {
		return err
	}
	rustfsPwd, err := generatePassword(passwordLength)
	if err != nil {
		return err
	}
	localIP, err := detectLocalIP()
	if err != nil {
		return err
	}

	tpl, err := template.New("cluster-config").Option("missingkey=error").Parse(string(tplBytes))
	if err != nil {
		return fmt.Errorf("解析配置模板失败: %w", err)
	}

	var rendered bytes.Buffer
	if err := tpl.Execute(&rendered, templateData{
		ClusterType:       c.typeFlag,
		IsKubernetes:      c.typeFlag == "kubernetes",
		RegistryPassword:  regPwd,
		MySQLRootPassword: mysqlPwd,
		RustfsPassword:    rustfsPwd,
		CurrentNodeIP:     localIP,
	}); err != nil {
		return fmt.Errorf("渲染配置模板失败: %w", err)
	}

	if err := os.WriteFile(absOutputPath, rendered.Bytes(), 0o600); err != nil {
		return fmt.Errorf("写入配置文件失败: %w", err)
	}

	slog.Info("配置文件生成成功", "output", absOutputPath, "currentNodeIP", localIP)
	fmt.Printf("配置文件已生成: %s\n", absOutputPath)
	fmt.Printf("本机 IP: %s\n", localIP)
	fmt.Println("⚠️  密码已随机生成并写入配置文件，请妥善保管")
	return nil
}

func generatePassword(length int) (string, error) {
	out := make([]byte, length)
	max := big.NewInt(int64(len(passwordCharset)))
	for i := range out {
		n, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", fmt.Errorf("生成随机密码失败: %w", err)
		}
		out[i] = passwordCharset[n.Int64()]
	}
	return string(out), nil
}

func detectLocalIP() (string, error) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "", fmt.Errorf("枚举本机网卡失败: %w", err)
	}
	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok || ipNet.IP.IsLoopback() {
			continue
		}
		if v4 := ipNet.IP.To4(); v4 != nil {
			return v4.String(), nil
		}
	}
	return "", fmt.Errorf("未找到非 loopback 的 IPv4 地址")
}
