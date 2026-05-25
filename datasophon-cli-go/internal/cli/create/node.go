package create

import "github.com/spf13/cobra"

// createNodeCmd 新增节点初始化，等价于原 create cluster --action initSingleNode。
// 嵌入 createClusterCmd 以复用全部私有方法；自身定义 run() 遮蔽嵌入的同名方法，
// 直接调 setup() + initSingleNode()，不经过 cluster 的 action switch。
type createNodeCmd struct {
	createClusterCmd
}

func (c *createNodeCmd) run() error {
	cfg, err := c.setup()
	if err != nil {
		return err
	}
	return c.initSingleNode(cfg)
}

// NewNodeCommand 对应 datasophon-cli create node。
// 读取 cluster-sample.yml 中的 addNodes 列表并执行单节点初始化流程。
func NewNodeCommand(dryRun *bool) *cobra.Command {
	c := &createNodeCmd{}
	cmd := &cobra.Command{
		Use:   "node",
		Short: "初始化新增节点（读取 cluster-sample.yml 中的 addNodes）",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.run()
		},
	}

	cmd.Flags().StringVarP(&c.DatasophonPath, "datasophonPath", "p", "", "datasophon 绝对路径 (必填)")
	cmd.Flags().StringVar(&c.InstallPath, "installPath", "", "安装路径 (必填)")
	cmd.Flags().StringVarP(&c.ProductPkgsPath, "productPackagesPath", "n", "", "安装包路径 (必填)")
	cmd.Flags().BoolVar(&c.InitPathOverwriteForce, "initPathOverwriteForce", false, "datasophon-init 目录是否覆盖")

	_ = cmd.MarkFlagRequired("datasophonPath")
	_ = cmd.MarkFlagRequired("installPath")
	_ = cmd.MarkFlagRequired("productPackagesPath")

	return cmd
}
