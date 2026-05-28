package plan

import (
	"fmt"
	"strings"
)

// PrintSummary 打印 PlanFile 的人类可读摘要到 stdout。
func PrintSummary(pf *PlanFile) {
	pending, completed, skipped, failed, running := 0, 0, 0, 0, 0
	for _, s := range pf.Steps {
		switch s.Status {
		case StatusPending:
			pending++
		case StatusCompleted:
			completed++
		case StatusSkipped:
			skipped++
		case StatusFailed:
			failed++
		case StatusRunning:
			running++
		}
	}

	total := len(pf.Steps)
	fmt.Printf("\n执行计划 (%d steps)  action=%s\n", total, pf.Action)
	fmt.Printf("  ✓ completed: %3d\n", completed)
	fmt.Printf("  • pending  : %3d\n", pending)
	fmt.Printf("  ⊘ skipped  : %3d\n", skipped)
	if failed > 0 {
		fmt.Printf("  ✗ failed   : %3d\n", failed)
	}
	if running > 0 {
		fmt.Printf("  ⟳ running  : %3d  (上次执行被中断)\n", running)
	}
	fmt.Println()
	fmt.Printf("%-4s  %-10s  %-28s  %-24s  %s\n", "序号", "状态", "ID", "步骤", "目标节点")
	fmt.Println(strings.Repeat("-", 90))
	for i, s := range pf.Steps {
		icon := statusIcon(s.Status)
		targets := strings.Join(s.Targets, ", ")
		if len(targets) > 30 {
			targets = targets[:27] + "..."
		}
		fmt.Printf("%-4d  %-10s  %-28s  %-24s  %s\n",
			i+1, icon+"  "+string(s.Status), s.ID, s.Name, targets)
	}
	fmt.Println()
}

func statusIcon(s Status) string {
	switch s {
	case StatusCompleted:
		return "✓"
	case StatusSkipped:
		return "⊘"
	case StatusFailed:
		return "✗"
	case StatusRunning:
		return "⟳"
	default:
		return "•"
	}
}
