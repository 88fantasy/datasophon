package initcmd

import (
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/executor"
	"github.com/spf13/cobra"
)

// TaskBase 对应 Java InitBase 中继承到各子任务的公共字段和方法。
type TaskBase struct {
	ConfigFilePath   string
	ConfigPassword   string
	RegistryIP       string
	RegistryPort     string
	RegistryUsername string
	RegistryPassword string
	EnableRegistry   bool
}

func (b *TaskBase) GetConfig() (*config.ClusterConfig, error) {
	return config.Load(b.ConfigFilePath, b.ConfigPassword)
}

// AddBaseFlags 注册 InitBase 的公共 flags（对应 Java @CommandLine.Option 注解列表）。
func (b *TaskBase) AddBaseFlags(cmd *cobra.Command) {
	cmd.Flags().StringVarP(&b.ConfigFilePath, "config", "c", "", "配置文件路径")
	cmd.Flags().StringVar(&b.ConfigPassword, "cpassword", "", "配置文件加密密钥")
	cmd.Flags().StringVar(&b.RegistryIP, "registryIp", "", "制品库 IP")
	cmd.Flags().StringVar(&b.RegistryPort, "registryPort", "", "制品库端口")
	cmd.Flags().StringVar(&b.RegistryUsername, "registryUsername", "", "制品库用户名")
	cmd.Flags().StringVar(&b.RegistryPassword, "registryPassword", "", "制品库密码")
	cmd.Flags().BoolVar(&b.EnableRegistry, "enableRegistry", false, "是否使用制品库")
}

// runLocal 对应 Java InitBase.run()：本地 LocalExecutor 执行 doRun。
func runLocal(dryRun bool, doRun func(executor.Executor) bool) error {
	doRun(executor.NewLocalExecutor(dryRun))
	return nil
}
