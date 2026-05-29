package plan

import (
	"fmt"
	"strings"
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

	onlyK8s := ctx.Cfg.Kubernetes.OnlyInstall
	for _, s := range registry {
		ps := PlanStep{ID: s.ID, Name: s.Name, Status: StatusPending}

		// OnlyInstall 模式：跳过非 k8s- 前缀的 step
		if onlyK8s && !strings.HasPrefix(s.ID, "k8s-") {
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
