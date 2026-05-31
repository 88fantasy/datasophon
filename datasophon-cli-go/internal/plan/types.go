package plan

import (
	"time"

	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/config"
	"github.com/88fantasy/datasophon/datasophon-cli-go/internal/handler"
)

// ClusterScope 标记一个 Step 适用于哪种集群类型。
type ClusterScope int

const (
	ScopeBoth           ClusterScope = iota // 默认：hadoop 与 kubernetes 集群均执行
	ScopeHadoopOnly                         // 仅 hadoop 集群执行
	ScopeKubernetesOnly                     // 仅 kubernetes 集群执行
)

// Matches 判断该 Step 是否应在给定集群类型下执行。
// 空 ClusterType 视为 ScopeBoth（兼容未设置 type 的旧配置）。
func (s ClusterScope) Matches(t config.ClusterType) bool {
	switch s {
	case ScopeHadoopOnly:
		return t == config.ClusterTypeHadoop
	case ScopeKubernetesOnly:
		return t == config.ClusterTypeKubernetes
	default: // ScopeBoth 或未知值
		return true
	}
}

// Status 表示一个 Step 的当前执行状态。
type Status string

const (
	StatusPending   Status = "pending"
	StatusRunning   Status = "running"
	StatusCompleted Status = "completed"
	StatusFailed    Status = "failed"
	StatusSkipped   Status = "skipped" // Condition 为 false 时
)

// BuildFunc 是 Step.Build 字段的类型别名。
type BuildFunc func(ctx *BuildContext) ([]Action, error)

// CondFunc 是 Step.Condition 字段的类型别名。
type CondFunc func(ctx *BuildContext) bool

// Step 是一类初始化步骤的声明式蓝图。
type Step struct {
	ID        string       // 稳定 ID，如 "init-firewall"
	Name      string       // 人类可读名称
	Scope     ClusterScope // 适用集群类型；零值=ScopeBoth
	Condition CondFunc     // nil 视作 true
	Build     BuildFunc    // 在 plan/apply 阶段按需调用
}

// BuildContext 把运行时状态传给 Build 和 Condition。
// 所有 condition 类参数通过 cfg.* 获取，不通过 CLI flag。
type BuildContext struct {
	Cfg                    *config.ClusterConfig
	InitPath               string
	PackagesPath           string
	InstallPath            string
	ProductPkgsPath        string
	ConfigYaml             string
	LocalHost              *config.Host
	LocalIP                string
	GlobalNodes            map[string]*config.Host
	SSHAuthType            config.SSHAuthType
	InitPathOverwriteForce bool // 仅本地 datasophon-init 目录覆盖控制，保留为运行时参数
	DryRun                 bool
}

// Action = 一次 (host, handler) 调用对。
type Action struct {
	HostKey string          // hostname，用于 plan.json 可读性
	Host    *config.Host    // SSH 连接目标
	Handler handler.Handler // 已绑定参数的 task struct
}

// PlanFile 是序列化到磁盘的执行计划。
type PlanFile struct {
	Version     string     `json:"version"` // "1"
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
	ConfigFile  string     `json:"configFile"`  // cluster-sample.yml 绝对路径
	ClusterHash string     `json:"clusterHash"` // sha256(cfg)[:16]，apply 时校验
	Action      string     `json:"action"`      // "initALL" | "initSingleNode"
	Steps       []PlanStep `json:"steps"`
}

// PlanStep 是 PlanFile 中单个步骤的持久化状态。
type PlanStep struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Status    Status    `json:"status"`
	Targets   []string  `json:"targets"` // hostname 列表，仅供展示
	StartedAt time.Time `json:"startedAt,omitempty"`
	EndedAt   time.Time `json:"endedAt,omitempty"`
	Error     string    `json:"error,omitempty"`
}
