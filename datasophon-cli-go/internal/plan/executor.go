package plan

import (
	"fmt"
	"log/slog"
	"time"
)

// Apply 读取 plan 文件，顺序执行非 completed/skipped 步骤，每步完成后回写状态。
func Apply(initPath string, registry []Step, ctx *BuildContext) error {
	pf, err := Load(initPath)
	if err != nil {
		return err
	}

	// 校验 cfg 未变
	if pf.ClusterHash != ComputeHash(ctx.Cfg) {
		return fmt.Errorf("cluster.plan.json 与当前 cfg 不一致（clusterHash 不匹配），请重新执行 `plan`")
	}

	byID := make(map[string]Step, len(registry))
	for _, s := range registry {
		byID[s.ID] = s
	}

	for i := range pf.Steps {
		ps := &pf.Steps[i]
		if ps.Status == StatusCompleted || ps.Status == StatusSkipped {
			slog.Info("跳过", "id", ps.ID, "status", ps.Status)
			continue
		}

		s, ok := byID[ps.ID]
		if !ok {
			return fmt.Errorf("registry 中找不到 step: %s", ps.ID)
		}

		// 重新 build 得到 fresh actions
		actions, err := s.Build(ctx)
		if err != nil {
			return markFail(initPath, pf, i, fmt.Errorf("rebuild step %s: %w", ps.ID, err))
		}

		// 标记 running 并提前回写（崩溃后可观测）
		ps.Status = StatusRunning
		ps.StartedAt = time.Now()
		_ = Save(initPath, pf)

		slog.Info("执行步骤", "id", ps.ID, "name", ps.Name, "targets", ps.Targets)
		if err := runActions(actions, ctx.SSHAuthType, ctx.DryRun); err != nil {
			return markFail(initPath, pf, i, fmt.Errorf("step %s: %w", ps.ID, err))
		}

		ps.Status = StatusCompleted
		ps.EndedAt = time.Now()
		if err := Save(initPath, pf); err != nil {
			return err
		}
		slog.Info("步骤完成", "id", ps.ID, "elapsed", ps.EndedAt.Sub(ps.StartedAt))
	}

	slog.Info("所有步骤执行完成")
	return nil
}

// markFail 把指定步骤标记为 failed，回写文件，并返回原始错误。
func markFail(initPath string, pf *PlanFile, idx int, err error) error {
	pf.Steps[idx].Status = StatusFailed
	pf.Steps[idx].EndedAt = time.Now()
	pf.Steps[idx].Error = err.Error()
	_ = Save(initPath, pf)
	return err
}
