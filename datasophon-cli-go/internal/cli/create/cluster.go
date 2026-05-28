package create

import (
	"bufio"
	"fmt"
	"log/slog"
	"os"
	"strings"

	"github.com/spf13/cobra"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/plan"
)

type createClusterCmd struct {
	nodeInitializer

	yes      bool // --yes/-y：跳过确认
	planOnly bool // --plan-only：只生成计划，不执行
}

func NewClusterCommand(dryRun *bool) *cobra.Command {
	c := &createClusterCmd{}
	cmd := &cobra.Command{
		Use:   "cluster",
		Short: "创建或初始化集群（先 plan 生成计划，再 apply 执行）",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.runDefault()
		},
	}

	c.bindCommonFlags(cmd)
	cmd.Flags().BoolVarP(&c.yes, "yes", "y", false, "跳过确认，直接执行计划")
	cmd.Flags().BoolVar(&c.planOnly, "plan-only", false, "只生成计划，不执行（等价于 plan 子命令）")

	// plan 子命令
	planCmd := &cobra.Command{
		Use:   "plan",
		Short: "生成执行计划到 state/cluster.plan.json（不执行）",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.runPlan()
		},
	}
	c.bindCommonFlags(planCmd)

	// apply 子命令
	applyCmd := &cobra.Command{
		Use:   "apply",
		Short: "读取 state/cluster.plan.json 并顺序执行（支持断点续跑）",
		RunE: func(cmd *cobra.Command, args []string) error {
			c.dryRun = *dryRun
			return c.runApply()
		},
	}
	c.bindCommonFlags(applyCmd)

	cmd.AddCommand(planCmd, applyCmd)
	return cmd
}

// runDefault 默认行为：plan → 打印摘要 → 确认 → apply。
func (c *createClusterCmd) runDefault() error {
	if err := c.runPlan(); err != nil {
		return err
	}
	if c.planOnly {
		return nil
	}
	if !c.yes {
		fmt.Print("确认执行以上计划? [y/N] ")
		reader := bufio.NewReader(os.Stdin)
		line, _ := reader.ReadString('\n')
		if !strings.EqualFold(strings.TrimSpace(line), "y") {
			fmt.Println("已取消。可稍后执行 `create cluster apply` 运行计划。")
			return nil
		}
	}
	return c.runApply()
}

// runPlan 生成执行计划并打印摘要。
func (c *createClusterCmd) runPlan() error {
	if _, err := c.setup(); err != nil {
		return err
	}
	ctx := c.toBuildContext()
	pf, err := plan.GeneratePlan("initALL", plan.InitALLRegistry, ctx)
	if err != nil {
		return fmt.Errorf("生成计划失败: %w", err)
	}
	if err := plan.Save(c.initPath, pf); err != nil {
		return err
	}
	slog.Info("计划已写入", "path", plan.PlanPath(c.initPath))
	plan.PrintSummary(pf)
	fmt.Printf("下一步: datasophon-cli create cluster apply\n\n")
	return nil
}

// runApply 执行计划（断点续跑）。
func (c *createClusterCmd) runApply() error {
	if _, err := c.setup(); err != nil {
		return err
	}
	ctx := c.toBuildContext()
	return plan.Apply(c.initPath, plan.InitALLRegistry, ctx)
}
