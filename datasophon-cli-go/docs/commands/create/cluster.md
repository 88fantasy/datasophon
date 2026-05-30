# datasophon-cli create cluster

## 用途

完整集群初始化。读取 `cluster-sample.yml`，生成 initALL 33 步执行计划，然后顺序执行。支持 plan / apply 分离、`--plan-only`、跳过确认（`-y`）和断点续跑。

适用场景：首次搭建 Datasophon 集群的全量初始化。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] create cluster [flags]
datasophon-cli [--dry-run] create cluster plan [flags]
datasophon-cli [--dry-run] create cluster apply [flags]
```

## 子命令

| 子命令 | 说明 |
|---|---|
| `plan` | 仅生成执行计划（写到 `<datasophonPath>/datasophon-init/state/initALL.plan.json`），不执行 |
| `apply` | 读取已生成的计划并顺序执行（支持断点续跑） |
| （默认，无子命令） | plan → 打印摘要 → 交互确认 → apply |

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--type` | `-t` | string | — | **是** | 集群类型：`hadoop`（Hadoop 大数据集群）或 `kubernetes`（K8s 集群）。CLI 值优先于 `cluster-sample.yml` 中的 `type` 字段 |
| `--datasophonPath` | `-p` | string | — | 是 | datasophon 根目录绝对路径（须以 `/` 开头且目录存在）。配置文件从 `<datasophonPath>/datasophon-init/config/cluster-sample.yml` 读取 |
| `--installPath` | 无 | string | — | 是 | 组件安装根目录绝对路径，不存在时自动创建 |
| `--productPackagesPath` | `-n` | string | — | 是 | 组件安装包目录路径 |
| `--initPathOverwriteForce` | 无 | bool | `false` | 否 | 是否覆盖已存在的 `datasophon-init` 目录（重新初始化时使用） |
| `--yes` | `-y` | bool | `false` | 否 | 跳过交互确认，plan 完成后直接执行 apply |
| `--plan-only` | 无 | bool | `false` | 否 | 等价于 `create cluster plan`（只生成计划，不执行） |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 执行流程说明

### 默认模式（推荐生产）

```
create cluster（无参数）
  ├─ 1. 读取 cluster-sample.yml，生成 initALL 计划
  ├─ 2. 打印计划摘要（步骤总数、待执行数、已完成数）
  ├─ 3. 交互确认：输入 y 继续，其他输入取消
  └─ 4. 顺序执行 33 步 DAG
```

取消后计划文件已保存，可随时执行 `apply` 恢复：

```bash
datasophon-cli create cluster apply -p /data/datasophon --installPath /opt/install -n /data/datasophon/datasophon-init/packages
```

### 断点续跑

`apply` 执行时读取 `state/initALL.plan.json`，对每步判断状态：

- `completed` — 跳过，不重复执行
- `pending` / `failed` — 执行，执行成功后写回 `completed`

因此中途失败后直接重新 `apply` 即可从失败步骤继续，不会重复已完成的步骤。

### plan 计划文件路径

```
<datasophonPath>/datasophon-init/state/initALL.plan.json
```

## 配置文件依赖

| 字段 | 说明 |
|---|---|
| `type` | 集群类型：`hadoop` 或 `kubernetes`（CLI `--type` 优先覆盖） |
| `nodes` | 至少 1 个节点；当前机器 IP 必须出现在列表中 |
| `global.sshAuthType` | SSH 鉴权方式 |
| `global.registry.enable` | 控制 `init-registry` / `init-registry-upload` / `init-offline-nodes` 步骤是否激活 |
| `global.kubernetes.enable` | 控制所有 `k8s-*` 步骤是否激活（需同时 `--type kubernetes`） |
| `global.mysql.enable` | 控制 `init-mysql` / `init-mysql-app-db` 步骤是否激活 |
| `global.ntpServer.enable` | 控制 NTP 步骤是否激活 |
| `global.packages.*` | 各组件安装包文件名，与 `<datasophonPath>/datasophon-init/packages/` 下文件名对应 |

详见 [配置文件参考](../../config-reference.md)。

## 示例

### dry-run 预检（推荐先执行）

```bash
datasophon-cli --dry-run create cluster -t hadoop \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

### 生产环境：plan 后审阅再 apply

```bash
# Step 1: 生成计划（hadoop 集群）
datasophon-cli create cluster plan -t hadoop \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages

# Step 2: 审阅计划内容（可选）
cat /data/datasophon/datasophon-init/state/initALL.plan.json

# Step 3: 执行
datasophon-cli create cluster apply -t hadoop \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

### K8s 集群初始化

```bash
datasophon-cli create cluster -t kubernetes -y \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

### 跳过确认一键执行

```bash
datasophon-cli create cluster -t hadoop -y \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `required flag(s) "type" not set` | 未传 `--type` | 添加 `-t hadoop` 或 `-t kubernetes` |
| `--type 必须是 hadoop 或 kubernetes` | `--type` 取值不合法 | 检查拼写；仅接受 `hadoop` / `kubernetes` |
| `datasophonPath、installPath 必须是绝对路径` | 传入了相对路径 | 改用 `/` 开头的绝对路径 |
| `路径不存在: <path>` | `--datasophonPath` 指定的目录不存在 | 确认目录已创建 |
| `cluster-sample.yml 中 nodes 列表不能为空` | 配置文件 `nodes` 字段为空 | 补全 nodes 配置 |
| `生成计划失败` | 配置文件格式错误或字段缺失 | 检查 YAML 格式；对照 [配置文件参考](../../config-reference.md) |
| SSH 连接超时 | 网络不通或 SSH 端口不对 | 检查 `nodes[*].port` 和节点 SSH 服务状态 |

## 相关命令

- [`create node`](./node.md) — 集群初始化完成后扩容新节点
- [`create config`](./config.md) — 生成初始配置文件
- [DAG 步骤表](../../reference/init-all-dag.md) — initALL 33 步详解
- [退出码与断点续跑](../../reference/exit-codes.md)
