# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## datasophon-cli-go

Datasophon 节点初始化与管理 CLI 的 Go 重写版（替代原 Java CLI）。`Go 1.21` + `Cobra` + `viper` + `pkg/sftp` + `golang.org/x/crypto`，零依赖静态单文件二进制，支持 Linux/macOS amd64/arm64 交叉编译。

架构上下文参见仓库根 `docs/ARCHITECTURE.md`「4.6 节点初始化:datasophon-cli-go」。

---

## 常用命令（基于 Makefile）

```bash
make build      # 当前平台二进制 → dist/datasophon-cli
make release    # 交叉编译 4 个目标到 dist/（linux/darwin × amd64/arm64）
make test       # go test ./...
make vet        # go vet ./...
make clean      # rm -rf dist
go fmt ./...    # 格式化
go test -cover ./...   # 测试 + 覆盖率
```

交叉编译产物命名（**绝对不要重命名**）：

| 文件 | 平台 |
|---|---|
| `datasophon-cli-linux-amd64` | Linux x86_64（生产主流） |
| `datasophon-cli-linux-arm64` | Linux aarch64 |
| `datasophon-cli-darwin-amd64` | macOS Intel |
| `datasophon-cli-darwin-arm64` | macOS Apple Silicon |

全局开关 `--dry-run`（由 `internal/cli/root.go` 注册为 `PersistentFlags`，通过指针下传）只打印命令不实际执行，可用于排查。

---

## 关键设计

### plan / apply 两阶段

`create cluster` 子命令采用「先 plan 生成计划，再 apply 顺序执行」的两阶段模型：

- `create cluster plan`：调用 `plan.GeneratePlan`，把 33 个 Step 展开为 `PlanFile`（含 `clusterHash`、`steps` 列表、每个 step 的 `targets`），序列化到 `$DDH_HOME/state/initALL.plan.json`（`0600` 权限）。
- `create cluster apply`：读取 `plan.json`，按顺序执行；apply 阶段会比对 `clusterHash` 与当前 `cluster-sample.yml` 哈希，不一致直接拒绝。
- `create cluster`（默认行为）= plan + 打印摘要 + 交互确认（`-y` 跳过）+ apply。
- 断点续跑：apply 会复用 `PlanFile` 中已 `completed` 的 Step，跳过重做；正在 `running` 的 Step 失败时记录到 `plan.steps[i].error`，下次 apply 可继续。

### 33 个 Step 声明式定义（initALL）

`internal/plan/registry.go` 中 `InitALLRegistry` 数组是 `create cluster` 的核心；`internal/plan/registry_node.go` 中 `InitNodeRegistry` 是 `create node` 配置模式的 12 步 DAG。两者共享相同的 `Step` 结构（含 `ID / Name / Scope / Condition / Build`），按数组顺序即 DAG 顺序执行。

- `Scope`：`ScopeBoth`（默认）/ `ScopeHadoopOnly` / `ScopeKubernetesOnly`，由 `ClusterType`（`hadoop` | `kubernetes`）匹配。
- `Condition`：必须从 `ctx.Cfg.*` 读布尔字段（如 `ctx.Cfg.Registry.Enable`、`ctx.Cfg.Kubernetes.Enable`），**禁止**走 CLI flag —— flag 只覆盖 type/auth 等全局项。
- `Build`：返回 `[]Action`（即 `host × handler` 调用对），仅用于 plan 阶段展示 `targets`；apply 阶段 `runner.runActions` 会再次调用并 SSH 链式执行。

涉及 33 个 Step 的 DAG 完整步骤表与依赖关系见 `docs/reference/init-all-dag.md`。

### clusterHash 校验配置一致性

`plan.ComputeHash(cfg)` 用 SHA256(JSON(cfg))[:16 hex]，写入 `PlanFile.ClusterHash`。apply 时若当前 `cluster-sample.yml` 算出的 hash 与 plan 中不一致，直接拒绝执行 —— 防止「计划用了旧配置、apply 时配置已被改」的脏执行。

### SSH / sftp 远程执行

`internal/executor/` 分三种执行器：

- `local.go`：本机直跑（`exec.Command`），`dryRun` 时只 echo。
- `ssh.go`：通过 `golang.org/x/crypto/ssh` 建连接，支持 `password` / `key` 两种鉴权（`SSHAuthType` 决定），用 `handler.Chain` 串行执行多个 handler。
- `batch.go`：批量并行执行（多节点并发）。

sftp 文件传输走 `github.com/pkg/sftp`；`internal/handler/chain.go` 把 SSH 连接、sftp 客户端、dryRun 状态打包，下游 handler 通过接口拿资源做上传/远端命令。

### 断点续跑

`plan.Store` 持久化到 `$DDH_HOME/state/{action}.plan.json`（`action` 取 `initALL` / `initSingleNode`，避免互相覆盖）。Runner 按 Step 顺序执行，失败即停；下次 `apply` 时从第一个未完成 Step 继续。

---

## 环境变量

- `DDH_HOME`：**必填**。`cmd/datasophon-cli/main.go` 入口处强制检查，为空直接 `Exit(1)`，打印 `DDH_HOME is empty, please set DDH_HOME using 'export DDH_HOME=xxx' command`。所有 state、plan、资源包路径都基于 `$DDH_HOME` 派生。

默认日志走 `log/slog` → `stderr`，level `INFO`（main.go 初始化）。如需更详细输出临时调 slog level。

---

## 关键约定

1. **二进制名固定为 `datasophon-cli`**：由 `Makefile` 的 `BINARY = datasophon-cli` 决定，**不要重命名**。外部运维脚本（Java CLI 兼容路径、`/usr/local/bin/` 软链、systemd 单元、文档中的命令示例）都强依赖这个名字。
2. **`Condition` 一律从 `cfg.*` 读**：`Step.Condition` 的签名是 `func(ctx *BuildContext) bool`，从 `ctx.Cfg.<Section>.Enable` / `ctx.Cfg.Kubernetes.K8sTools.Containerd` 等结构化配置读布尔值，**不要**让 Condition 检查 CLI flag —— CLI flag 只在 `cluster` 子命令层 override `ClusterType` 和 SSHAuthType。
3. **`cluster-sample.yml` 是运行期文件，不在本模块**：模块内 `internal/config/configs/cluster-config.yml` 是**单测 fixture / 默认配置样例**；生产配置由 `create config` 交互生成，落到 `$DDH_HOME/conf/cluster.yml`（或老脚本兼容路径），与 Java CLI 复用同一份。改配置结构时同时改 `internal/config/*.go` 的结构体 tag 与 `docs/config-reference.md` 字段表。
4. **Cobra 子命令组织**：`internal/cli/{create,init,upload}/` 各自封装一个子命令树；新增子命令先在对应包内写 `func NewXxxCommand(dryRun *bool) *cobra.Command`，再到该包的 `init.go` / `cluster.go` 等入口 `cmd.AddCommand(...)` 注册。
5. **新增 Step 流程**：在 `internal/cli/init/` 加 handler struct（实现 `handler.Handler` 接口的 `Name / Check / Handle`），到 `internal/plan/builders_*.go` 写 `BuildFunc`，最后在 `internal/plan/registry.go` 的 `InitALLRegistry` 末尾追加一行 `Step{ID, Name, Scope, Condition, Build}`。**顺序即执行顺序**，插队要谨慎评估上下游依赖。

---

## 子命令树

```text
datasophon-cli [--dry-run]
├── create                                  # 集群创建/扩容/远程安装
│   ├── cluster   [plan|apply] [-y] [-t hadoop|kubernetes]
│   ├── config                              # 交互式生成 cluster-sample.yml
│   ├── node   [-c file] [-t hadoop|kubernetes]  # 节点管理（配置模式或手动模式）
│   ├── mysql / nmap-server / ntp-server    # 单节点远程安装命令
│   ├── registry / rustfs / yum-server
│   └── ...
├── init                                    # 27 条独立单步初始化（system/network/packages/repo/db/k8s）
│   ├── firewall / selinux / swap / hadoop_user / bash
│   ├── library / osSafeConf / system-conf / hugePage
│   ├── hostname / allHost / ntpslave / ssh
│   ├── bin_packages / tar / jdk8 / jdk17
│   ├── offlineSlave / registryDecode
│   ├── mysql_app_db
│   └── docker / helm / helmify / kubectl / k8sBaseServices / k8sRegistryConf / kuboard
└── upload                                  # 离线包上传
    └── registry
```

`init` 27 条子命令的完整索引与 flag 公共参数见 `docs/commands/init/README.md`；每条子命令单独文档在 `docs/commands/init/{system,network,packages,repo,db,k8s}/<name>.md`。`init` 子命令语义是「在已登录的当前节点上单步本地初始化」；涉及「指定某个特定节点远程安装」（MySQL/Nexus/Rustfs/NTP Server/nmap/yum 离线源）已统一迁到 `create` 命令组。

`create cluster` 是入口，默认交互式先 plan 后 apply；加 `-y` 跳过确认；加 `--plan-only` 只 plan 不 apply。

---

## 测试

```bash
go test ./...                  # 全部单测
go test ./internal/plan/...    # 只跑 plan 包
go test -cover ./...           # 带覆盖率
```

测试分层：

- `internal/plan/` 内联单测：`builders_common_test.go` / `helpers_test.go` / `plan_test.go` / `registry_node_test.go`，覆盖 plan 生成、scope 匹配、condition 分支、initNode 配置模式 DAG。
- `internal/executor/`：`batch_test.go` / `result_test.go`，覆盖批执行与结果聚合。
- `internal/cli/init/containerd_test.go` / `internal/cli/create/append_yaml_test.go` / `internal/cli/create/node_setup_test.go`：handler 与 YAML 解析单测、setupConfig 重复检测单测。
- `internal/osinfo/`：`arch_test.go` / `os_test.go`，覆盖架构与 OS 探测（用于跨平台分发逻辑）。
- `test/`：跨包集成测试。`test/fixtures/cluster-sample.yml` 是集成测试用的样例配置；`test/config/loader_test.go` 验证 loader；`test/executor/local_test.go` 验证本地执行器。

新功能要求：附带单元测试；`make test && make vet` 通过；遵循 Go 命名约定（导出符号大写驼峰、文件名小写下划线、包名单数名词）。
