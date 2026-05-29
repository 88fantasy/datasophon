# datasophon-cli create nmap-server

## 用途

在指定节点上安装 `nmap`（端口扫描与连通性诊断工具），供集群运维使用。命令底层复用 `init.InitNmap` Task 实现。

支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.nmapServer` 读取节点，SSH 到 `nmapServer.node` 远程执行；安装成功后将 `global.nmapServer.enable` 回写为 `true`。
- **手动模式**（不指定 `-c`）：在**本地节点**直接执行安装。

## 用法 (Synopsis)

```bash
# 配置文件模式
datasophon-cli [--dry-run] create nmap-server -c <config.yml>

# 手动模式（本机）
datasophon-cli [--dry-run] create nmap-server
```

## 参数 / Flags

| flag | 简写 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|---|
| `--config` | `-c` | string | `""` | 否 | 配置文件路径；指定后进入配置文件模式 |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

| 字段 | 说明 |
|---|---|
| `global.nmapServer.node` | 安装目标节点 hostname（须在 `nodes` 列表中） |
| `global.sshAuthType` | SSH 鉴权方式 |

## 安装行为

| OS 类型 | 安装命令 |
|---|---|
| CentOS / openEuler / RHEL | `yum -y install nmap` |
| Ubuntu / Debian | `apt install nmap -y` |

## 示例

### 配置文件模式

```bash
datasophon-cli create nmap-server \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
# 成功后：global.nmapServer.enable: true 回写
```

### 手动模式

```bash
datasophon-cli create nmap-server
```

## 退出码 / 常见错误

| 错误信息 | 根因 | 处置 |
|---|---|---|
| `配置中未找到 nmapServer 节点: <hostname>` | `global.nmapServer.node` 不在 `nodes` 列表中 | 检查 hostname 拼写 |
| `安装失败` | yum/apt 源不可用 | 检查节点网络或配置离线源 |
| `安装成功但写回配置文件失败` | 配置文件写权限不足 | 检查写权限 |

## 相关命令

- [`create cluster`](./cluster.md) — 集群初始化（DAG 步骤 21 `init-nmap` 复用同一 `InitNmap` Task）
