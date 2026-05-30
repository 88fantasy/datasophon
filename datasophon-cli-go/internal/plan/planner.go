package plan

import (
	"fmt"
	"time"
)

// GeneratePlan 根据 ctx 和 registry 生成 PlanFile（不执行）。
// action 取值："initALL" | "initSingleNode"。
func GeneratePlan(action string, registry []Step, ctx *BuildContext) (*PlanFile, error) {
	pf := &PlanFile{
		Version:     "1",
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
		ConfigFile:  ctx.ConfigYaml,
		ClusterHash: ComputeHash(ctx.Cfg),
		Action:      action,
	}

	for _, s := range registry {
		ps := PlanStep{ID: s.ID, Name: s.Name, Status: StatusPending}

		// Scope 检查：步骤与集群类型不匹配时跳过
		if !s.Scope.Matches(ctx.Cfg.Type) {
			ps.Status = StatusSkipped
			pf.Steps = append(pf.Steps, ps)
			continue
		}

		// Condition 检查
		if s.Condition != nil && !s.Condition(ctx) {
			ps.Status = StatusSkipped
			pf.Steps = append(pf.Steps, ps)
			continue
		}

		// 调用 Build 获取 targets（仅用于展示，不执行）
		actions, err := s.Build(ctx)
		if err != nil {
			return nil, fmt.Errorf("plan step %s: %w", s.ID, err)
		}
		ps.Targets = uniqueHostKeys(actions)
		pf.Steps = append(pf.Steps, ps)
	}
	return pf, nil
}
