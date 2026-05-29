# datasophon-cli create node

## 用途

为单个新增节点执行 10 步基础初始化。该命令**只支持独立模式**：通过 `--ip` 等 5 个 flag 直接指定目标节点，不依赖 `cluster-sample.yml` 的 `nodes` 列表。

> 自重构后 `addNodes` 批量模式已移除：增加多个节点时请对每个节点分别执行 `create node`。安装完成后，新节点会自动**追加**到 `cluster-sample.yml` 的 `nodes` 列表（同 IP 或 hostname 已存在时跳过追加）。

## 用法 (Synopsis)

```bash
datasophon-cli [--dry-run] create node \
  -p <datasophonPath> --installPath <path> -n <packagesPath> \
  --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn>
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

> 5 个独立模式参数（`--ip / --user / --password / --port / --hostname`）必须**同时**全部提供，缺一即报错。
> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 执行流程

1. 校验 5 个独立参数均已提供
2. 校验 `--datasophonPath` 与 `--installPath` 均为绝对路径且 `datasophonPath` 已存在
3. SSH 到目标节点，按下列顺序顺序执行 10 步基础初始化：

| 序号 | 步骤名 | 对应 init 子命令 |
|---|---|---|
| 1 | shell bash 设置 | `init bash` |
| 2 | 创建 hadoop 用户和组 | `init osUser` |
| 3 | 关闭防火墙 | `init firewall` |
| 4 | 关闭 SELinux | `init selinux` |
| 5 | 关闭 Swap | `init swap` |
| 6 | 初始化依赖库 | `init library` |
| 7 | 安全配置 | `init osSafeConf` |
| 8 | 优化系统配置 | `init system-conf` |
| 9 | 配置 hostname | `init hostname` |
| 10 | 关闭透明大页 | `init hugePage` |

4. 如果 `<datasophonPath>/datasophon-init/config/cluster-sample.yml` 存在，将新节点追加到 `nodes` 列表

> 独立模式**不走 plan 引擎**（不生成 `state/initSingleNode.plan.json`），不支持断点续跑。所有 10 步均为幂等操作，中途失败可直接重跑。

## 写回 cluster-sample.yml 的语义

- 写回字段：`ip / port / user / password / hostname`
- 重复检测：若 `nodes` 列表中已有相同 IP 或 hostname 的节点，**跳过写回**并仅打印 Warn 日志
- 如配置文件不存在（路径下没有 `cluster-sample.yml`），跳过写回并仅打印 Info 日志

## 示例

### dry-run 预检

```bash
datasophon-cli --dry-run create node \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 \
  --user root \
  --password 'YourPassword' \
  --port 22 \
  --hostname app7
```

### 实际执行

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

执行结束后，`cluster-sample.yml` 的 `nodes:` 列表会自动新增 `app7` 一项。

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `--ip 模式下必须同时提供 --user --password --hostname --port` | 5 个独立模式参数缺一 | 补齐所有 5 个 flag |
| `datasophonPath、installPath 必须是绝对路径` | 传入了相对路径 | 改用 `/` 开头的绝对路径 |
| `路径不存在: <path>` | `--datasophonPath` 指定的目录不存在 | 确认 `datasophonPath` 已创建 |
| `cluster-sample.yml 不存在，跳过写回` | 配置文件不存在 | 非错误（仅 Info 日志），节点初始化已完成 |
| `同 IP 或 hostname 节点已存在，跳过写回` | 节点已存在于 `nodes` 列表 | 非错误（仅 Warn 日志），节点初始化已完成 |

## 相关命令

- [`create cluster`](./cluster.md) — 全量初始化
- [DAG 步骤表 → standalone](../../reference/init-all-dag.md) — 10 步详解
