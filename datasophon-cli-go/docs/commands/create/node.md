# datasophon-cli create node

## 用途

为单个新增节点执行基础初始化。支持两种模式：

| 模式 | 触发方式 | 特点 |
|---|---|---|
| **配置模式** | 指定 `-c/--config <file>` | 从配置文件读取集群上下文，走 plan 引擎执行 12 步条件化 DAG，支持断点续跑 |
| **手动模式** | 不传 `-c`（默认） | 直接用 CLI 参数初始化，不依赖配置文件，不生成 plan 文件 |

> 增加多个节点时请对每个节点分别执行 `create node`。安装完成后，新节点会自动**追加**到配置文件的 `nodes` 列表（同 IP 或 hostname 已存在时跳过追加）。

## 用法 (Synopsis)

**配置模式**（推荐，支持 ntp_slave/offline_slave 等条件化步骤）：

```bash
datasophon-cli [--dry-run] create node \
  -c <cluster.yml> \
  -p <datasophonPath> --installPath <path> -n <packagesPath> \
  --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn>
```

**手动模式**（快速初始化，无需配置文件）：

```bash
datasophon-cli [--dry-run] create node \
  -p <datasophonPath> --installPath <path> -n <packagesPath> \
  --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn> \
  [-t hadoop|kubernetes]
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--datasophonPath` | `-p` | string | — | 是 | datasophon 根目录绝对路径（须以 `/` 开头且目录存在） |
| `--installPath` | 无 | string | — | 是 | 组件安装根目录绝对路径，不存在时自动创建 |
| `--productPackagesPath` | `-n` | string | — | 是 | 组件安装包目录路径 |
| `--initPathOverwriteForce` | 无 | bool | `false` | 否 | 是否覆盖已存在的 `datasophon-init` 目录 |
| `--ip` | 无 | string | — | 是 | 目标节点 IP |
| `--user` | 无 | string | — | 是 | SSH 用户 |
| `--password` | 无 | string | — | 是 | SSH 密码 |
| `--port` | 无 | int | — | 是 | SSH 端口 |
| `--hostname` | 无 | string | — | 是 | 目标节点 hostname |
| `--config` | `-c` | string | — | 否 | **配置模式**：配置文件路径，提供集群上下文与 ntp/offline 服务端 IP |
| `--cluster-type` | `-t` | string | — | 否 | **手动模式**：集群类型 `hadoop\|kubernetes`，仅 `hadoop` 时创建 hadoop 用户 |

> 5 个节点定位参数（`--ip / --user / --password / --port / --hostname`）在两种模式下均必填。
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

---

## 配置模式执行流程

1. 校验 `--datasophonPath` 与 `--installPath` 均为绝对路径且 `datasophonPath` 已存在
2. 加载 `-c` 指定的配置文件
3. **重复检测**：若 `--ip` 指定的 IP 已存在于配置文件 `nodes` 列表中 → **提示并停止**（非零退出）
4. 调用 plan 引擎生成 `state/initNode.plan.json`，打印摘要
5. 按下列 12 步条件化 DAG 顺序执行：

| 序号 | 步骤 ID | 步骤名 | Scope / Condition |
|---|---|---|---|
| 1 | `node-bash` | shell bash 设置 | 始终执行 |
| 2 | `node-hadoopuser` | 创建 hadoop 用户和组 | `cluster-type=hadoop` 时执行 |
| 3 | `node-firewall` | 关闭防火墙 | 始终执行 |
| 4 | `node-selinux` | 关闭 selinux | 始终执行 |
| 5 | `node-swap` | 关闭 swap | 始终执行 |
| 6 | `node-offline-slave` | yum/apt 离线源节点配置 | `global.offline=true` 时执行 |
| 7 | `node-ntp-slave` | 配置 NTP Slave | `ntpServer.enable=true` 时执行 |
| 8 | `node-library` | 初始化依赖库 | 始终执行 |
| 9 | `node-os-safe-conf` | 安全配置 | 始终执行 |
| 10 | `node-system-conf` | 优化系统配置 | 始终执行 |
| 11 | `node-hostname` | 配置 hostname | 始终执行 |
| 12 | `node-hugepage` | 关闭透明大页 | 始终执行 |

6. 成功后将新节点追加到配置文件 `nodes` 列表

> **依赖说明**：`offline_slave`（步骤 6）需要配置文件中有有效的 `registry.node`（registry 启用时）
> 或 `yumServer.node`（yumServer 启用时）。若 `global.offline=true` 但两者均未配置，步骤会构造空 ServerIP 并报错。
>
> `ntp_slave`（步骤 7）需要 `ntpServer.node` 指向一个存在于 `nodes` 列表中的节点。若目标新节点的 IP
> 恰好与 NTP server 相同（即新节点就是 NTP server 本身），则 ntp_slave 无目标执行（被 `slavesOf` 过滤）。

### 断点续跑

配置模式走 plan 引擎，生成 `<datasophonPath>/datasophon-init/state/initNode.plan.json`。
中途失败后直接重跑相同命令，plan 引擎会自动跳过已完成的步骤。

---

## 手动模式执行流程

1. 校验 5 个定位参数均已提供
2. 校验 `--datasophonPath` 与 `--installPath` 均为绝对路径
3. 若传入 `-t/--cluster-type` 则校验合法性（`hadoop` 或 `kubernetes`）
4. SSH 到目标节点，按下列顺序顺序执行（9 步基础 + 可选 hadoop_user）：

| 序号 | 步骤名 | 说明 |
|---|---|---|
| 1 | shell bash 设置 | 始终执行 |
| 2 | 创建 hadoop 用户和组 | 仅 `-t hadoop` 时执行 |
| 3 | 关闭防火墙 | 始终执行 |
| 4 | 关闭 SELinux | 始终执行 |
| 5 | 关闭 Swap | 始终执行 |
| 6 | 初始化依赖库 | 始终执行 |
| 7 | 安全配置 | 始终执行 |
| 8 | 优化系统配置 | 始终执行 |
| 9 | 配置 hostname | 始终执行 |
| 10 | 关闭透明大页 | 始终执行 |

5. 如果 `<datasophonPath>/datasophon-init/config/cluster-sample.yml` 存在，将新节点追加到 `nodes` 列表

> 手动模式**不走 plan 引擎**（不生成 plan.json），不支持断点续跑。所有步骤均为幂等操作，中途失败可直接重跑。
> 手动模式不支持 `ntp_slave`/`offline_slave`，这两步需要配置文件提供服务端 IP，请使用配置模式。

---

## 写回配置文件的语义

- 写回字段：`ip / port / user / password / hostname`
- **配置模式**：写回目标为 `-c` 指定的配置文件（已通过重复检测，追加必成功）
- **手动模式**：写回目标为 `<datasophonPath>/datasophon-init/config/cluster-sample.yml`（不存在时跳过）
- 重复检测：若 `nodes` 列表中已有相同 IP 或 hostname 的节点，**跳过写回**并仅打印 Warn 日志

---

## 示例

### 配置模式 dry-run（hadoop 集群，开启 ntp 和 offline）

```bash
datasophon-cli --dry-run create node \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 \
  --user root \
  --password 'YourPassword' \
  --port 22 \
  --hostname app7
# 期望输出：plan 摘要，显示 node-hadoopuser/node-ntp-slave/node-offline-slave 的执行情况
```

### 配置模式实际执行

```bash
datasophon-cli create node \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 \
  --user root \
  --password 'YourPassword' \
  --port 22 \
  --hostname app7
```

### 手动模式（hadoop 集群，需要创建 hadoop 用户）

```bash
datasophon-cli create node \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 \
  --user root \
  --password 'YourPassword' \
  --port 22 \
  --hostname app7 \
  -t hadoop
```

### 手动模式（kubernetes 集群，不建 hadoop 用户）

```bash
datasophon-cli create node \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 \
  --user root \
  --password 'YourPassword' \
  --port 22 \
  --hostname app7
```

---

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `节点 <ip> 已存在于配置文件 nodes 列表中，已停止` | 配置模式：目标 IP 已在 nodes 列表 | 检查 `--ip` 是否是真正的新节点 |
| `--ip 模式下必须同时提供 --user --password --hostname --port` | 手动模式：5 个定位参数缺一 | 补齐所有 5 个 flag |
| `datasophonPath、installPath 必须是绝对路径` | 传入了相对路径 | 改用 `/` 开头的绝对路径 |
| `路径不存在: <path>` | `--datasophonPath` 指定的目录不存在 | 确认 `datasophonPath` 已创建 |
| `--type 必须是 hadoop 或 kubernetes` | 手动模式 `-t` 值非法 | 只传 `hadoop` 或 `kubernetes` |
| `cluster-sample.yml 不存在，跳过写回` | 手动模式配置文件不存在 | 非错误（仅 Info 日志），节点初始化已完成 |
| `同 IP 或 hostname 节点已存在，跳过写回` | 节点已存在于 `nodes` 列表 | 非错误（仅 Warn 日志），节点初始化已完成 |

## 相关命令

- [`create cluster`](./cluster.md) — 全量初始化
- [DAG 步骤表](../../reference/init-all-dag.md) — initNode 12 步详解
