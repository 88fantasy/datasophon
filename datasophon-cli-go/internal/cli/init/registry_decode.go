package initcmd

import (
	"fmt"
	"log/slog"
	"os"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
	"golang.org/x/crypto/ssh"
)

// InitRegistryDecode 对应 Java InitRegistryDecode — 制品包解压并复制配置文件（明文，无需解密）。
type InitRegistryDecode struct {
	TaskBase
	Enable              bool
	DatasophonHomePath  string
	ProductConfigPath   string
	ProductPackagesPath string
}

func (t *InitRegistryDecode) Name() string { return "制品包解压复制" }

func (t *InitRegistryDecode) Handle(client *ssh.Client, dryRun bool) bool {
	return t.doRun(executor.NewSSHExecutor(client, dryRun))
}

func (t *InitRegistryDecode) Command(dryRun *bool) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "registryDecode",
		Short: "制品包解压并复制配置文件",
		RunE: func(cmd *cobra.Command, args []string) error {
			return runLocal(*dryRun, t.doRun)
		},
	}
	t.AddBaseFlags(cmd)
	cmd.Flags().BoolVarP(&t.Enable, "enable", "e", false, "是否执行")
	cmd.Flags().StringVarP(&t.DatasophonHomePath, "datasophonHomePath", "d", "", "datasophon 主目录（必填）")
	cmd.Flags().StringVar(&t.ProductConfigPath, "productConfigPath", "", "元数据包目录（必填）")
	cmd.Flags().StringVar(&t.ProductPackagesPath, "productPackagesPath", "", "安装包目录（必填）")
	_ = cmd.MarkFlagRequired("datasophonHomePath")
	_ = cmd.MarkFlagRequired("productConfigPath")
	_ = cmd.MarkFlagRequired("productPackagesPath")
	return cmd
}

func (t *InitRegistryDecode) doRun(exec executor.Executor) bool {
	if !t.Enable {
		slog.Info("enable=false，跳过")
		return true
	}

	rawPackagesPath := fmt.Sprintf("%s/raw/packages", t.ProductPackagesPath)
	commonPropertiesPath := fmt.Sprintf("%s/common.properties", t.ProductConfigPath)
	clusterSamplePath := fmt.Sprintf("%s/datasophon-init/cluster-sample.yml", t.ProductConfigPath)
	datasophonInitPath := fmt.Sprintf("%s/datasophon-init", t.DatasophonHomePath)
	datasophonInitPackagesPath := fmt.Sprintf("%s/packages", datasophonInitPath)

	// 路径检查
	for _, path := range []string{
		t.DatasophonHomePath, t.ProductConfigPath, t.ProductPackagesPath,
		rawPackagesPath, commonPropertiesPath, clusterSamplePath, datasophonInitPath,
	} {
		if _, err := os.Stat(path); os.IsNotExist(err) {
			slog.Error("路径不存在", "path", path)
			return false
		}
	}

	// 复制文件
	exec.ExecShell(fmt.Sprintf("cp -f %s %s/conf", commonPropertiesPath, t.DatasophonHomePath))
	exec.ExecShell(fmt.Sprintf("cp -f %s %s/config", clusterSamplePath, datasophonInitPath))
	if _, err := os.Stat(datasophonInitPackagesPath); os.IsNotExist(err) {
		exec.ExecShell(fmt.Sprintf("cp -rf %s %s", rawPackagesPath, datasophonInitPath))
	} else {
		slog.Info("datasophon-init packages 已存在，跳过复制", "path", datasophonInitPackagesPath)
	}

	slog.Info("制品包初始化完成")
	return true
}
