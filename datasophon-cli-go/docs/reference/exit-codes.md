# 退出码与断点续跑机制

## 退出码

| 退出码 |               含义                |
|-----|---------------------------------|
| `0` | 成功                              |
| `1` | 失败（所有非零错误均为 1，具体原因在 stderr 日志中） |

所有错误通过 `slog.Error(...)` 打印到标准错误，再以 `exit 1` 退出。Cobra 的 `RunE` 返回 error 时框架自动打印并退出。

## Plan 文件

`create cluster`（initALL）通过 plan 引擎执行。plan 文件持久化在：

```
<datasophon-init-path>/state/<action>.plan.json
```

例如：

```
/data/datasophon/datasophon-init/state/initALL.plan.json
```

> `create node`（standalone 10 步）**不**走 plan 引擎，详见下文 [Standalone 模式的差异](#standalone-模式的差异)。

文件权限为 `0600`（仅 owner 可读），格式为 JSON。

## Plan 文件结构

```json
{
  "action": "initALL",
  "clusterHash": "a3f9b21c...",
  "createdAt": "2026-05-01T10:00:00Z",
  "updatedAt": "2026-05-01T10:30:00Z",
  "steps": [
    {
      "id": "init-bin-package",
      "name": "分发资源包",
      "status": "completed",
      "startedAt": "...",
      "finishedAt": "..."
    },
    {
      "id": "init-bash",
      "status": "failed",
      "error": "ssh 连接失败: dial tcp 192.168.1.11:22"
    }
  ]
}
```

## 断点续跑机制

1. 首次执行 `create cluster` 时，生成 plan 文件并依次执行各步骤。
2. 每步执行完成后立即将状态写回 plan 文件（`completed` / `failed`）。
3. 如果中途失败，下次再次执行 `create cluster` 或 `create cluster apply`：
   - 已完成（`completed`）的步骤直接跳过。
   - 从失败步骤重新开始。
4. `clusterHash` 字段是配置文件的 SHA256 前缀，用于检测配置变更——若 hash 不一致，会提示重新生成 plan。

## 强制重建 Plan

若需要从头重跑（忽略已完成步骤），删除 plan 文件后重新执行：

```bash
rm /data/datasophon/datasophon-init/state/initALL.plan.json
datasophon-cli create cluster \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  ...
```

## Plan / Apply 分步执行

```bash
# 第一步：仅生成计划，不执行
datasophon-cli create cluster \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  -p /data/datasophon \
  --installPath /opt \
  -n /data/datasophon/datasophon-init/packages \
  --plan-only

# 检查生成的 plan 文件
cat /data/datasophon/datasophon-init/state/initALL.plan.json

# 第二步：执行已有计划（不重新生成）
datasophon-cli create cluster apply \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  -p /data/datasophon \
  --installPath /opt \
  -n /data/datasophon/datasophon-init/packages
```

<a id="standalone-模式的差异"></a>

## Standalone 模式的差异

`create node`（独立模式，需同时提供 `--ip / --user / --password / --port / --hostname`）触发的 standalone（10 步序列）**不使用 plan 引擎**，不生成 plan 文件，不支持断点续跑。中途失败后重新执行会从第 1 步开始，但所有步骤均为幂等操作，重跑安全。

> 早期版本支持的 "initSingleNode 17 步 + addNodes 批量" 模式已移除：扩容多节点请对每个节点分别执行 `create node`。

## 常见错误排查

|               错误信息               |               根因               |                           处置                            |
|----------------------------------|--------------------------------|---------------------------------------------------------|
| `计划文件不存在，请先执行 plan`              | 执行 `apply` 前未生成 plan           | 先执行 `create cluster --plan-only` 或直接运行 `create cluster` |
| `配置已变更（hash 不一致），请重新生成 plan`     | cluster-sample.yml 修改后未更新 plan | 删除旧 plan 文件，重新运行                                        |
| `ssh 连接失败`                       | 节点不可达或密码错误                     | 检查 nodes[*].ip / port / password；确认 SSH 服务正常            |
| `步骤执行失败: <step-id>`              | 具体步骤报错                         | 查看 slog 错误日志；参考对应步骤的命令页排查                               |
| `required flag(s) "xxx" not set` | Cobra 必填 flag 未提供              | 补全缺失的 flag                                              |

## 相关页面

- [create cluster — plan / apply 子命令](../commands/create/cluster.md)
- [initALL / standalone DAG 步骤表](./init-all-dag.md)

