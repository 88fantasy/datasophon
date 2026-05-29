# datasophon-cli create node

## 用途

初始化新增节点。支持两种模式：

- **批量模式**（默认）：读取 `cluster-sample.yml` 中的 `addNodes` 列表，对每个节点执行 17 步 initSingleNode DAG，完成后自动将节点追加到 `nodes` 列表。
- **独立模式**（指定 `--ip`）：直接对单个节点执行 10 步基础初始化，不依赖集群上下文；完成后同样写回配置文件。

## 用法 (Synopsis)

```bash
# 批量模式（读取 addNodes）
datasophon-cli [--dry-run] create node \
  -p <datasophonPath> --installPath <path> -n <packagesPath>

# 独立模式（指定目标 IP）
datasophon-cli [--dry-run] create node \
  -p <datasophonPath> --installPath <path> -n <packagesPath> \
  --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn>
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--datasophonPath` | `-p` | string | — | 是 | datasophon 根目录绝对路径 |
| `--installPath` | 无 | string | — | 是 | 组件安装根目录绝对路径 |
| `--productPackagesPath` | `-n` | string | — | 是 | 组件安装包目录路径 |
| `--initPathOverwriteForce` | 无 | bool | `false` | 否 | 是否覆盖已存在的 datasophon-init 目录 |
| `--ip` | 无 | string | `""` | 否 | 独立模式：目标节点 IP；提供后自动进入独立模式 |
| `--user` | 无 | string | `""` | 独立模式必填 | SSH 用户 |
| `--password` | 无 | string | `""` | 独立模式必填 | SSH 密码 |
| `--port` | 无 | int | `0` | 独立模式必填 | SSH 端口（独立模式下 0 被视为未填） |
| `--hostname` | 无 | string | `""` | 独立模式必填 | 目标节点 hostname |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 两种模式说明

### 批量模式

1. 读取 `cluster-sample.yml` 中的 `addNodes` 列表
2. 若 `addNodes` 为空，打印警告并退出（不报错）
3. 生成 `initSingleNode` 计划（17 步，仅操作 `addNodes` 中的节点）
4. 顺序执行计划
5. **自动**将新节点追加到 `nodes` 列表并写回配置文件

操作前，需在 `cluster-sample.yml` 的 `addNodes` 填入要加入的节点：

```yaml
addNodes:
  - ip: 192.168.1.20
    port: 22
    user: root
    password: "YourPassword"
    hostname: app7
```

### 独立模式

直接通过 `--ip` 等参数指定节点，执行 10 步基础初始化：

1. shell bash 设置（`init bash`）
2. 创建 hadoop 用户和组（`init osUser`）
3. 关闭防火墙（`init firewall`）
4. 关闭 SELinux（`init selinux`）
5. 关闭 Swap（`init swap`）
6. 初始化依赖库（`init library`）
7. 安全配置（`init osSafeConf`）
8. 优化系统配置（`init system-conf`）
9. 配置 hostname（`init hostname`）
10. 关闭透明大页（`init hugePage`）

> 独立模式**不走 plan 引擎**（不生成 plan.json），不支持断点续跑。适合快速加入单个已知节点。

## 配置文件依赖

| 字段 | 模式 | 说明 |
|---|---|---|
| `addNodes` | 批量模式必填 | 待初始化节点列表 |
| `global.sshAuthType` | 批量模式 | SSH 鉴权方式 |
| `nodes` | 批量模式 | 执行后新节点追加到此列表 |

## 示例

### 批量模式 dry-run

```bash
datasophon-cli --dry-run create node \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

### 批量模式实际执行

```bash
datasophon-cli create node \
  -p /data/datasophon \
  --installPath /opt/install \
  -n /data/datasophon/datasophon-init/packages
```

### 独立模式 dry-run

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

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `--ip 模式下必须同时提供 --user --password --hostname --port` | 独立模式参数不完整 | 补全所有独立模式必填参数 |
| `addNodes 列表为空，无需执行` | 配置文件 `addNodes` 为空 | 填写 `addNodes` 或使用独立模式 |

## 相关命令

- [`create cluster`](./cluster.md) — 全量初始化
- [DAG 步骤表 → initSingleNode](../../reference/init-all-dag.md#initsinglenode-17-步) — 17 步说明
