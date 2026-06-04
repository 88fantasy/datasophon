# datasophon-cli create ntp-server

## 用途

在指定节点安装并配置 chrony NTP 服务端：安装 `chrony` 包、覆盖 `/etc/chrony.conf`（或 Ubuntu 下的 `/etc/chrony/chrony.conf`，覆盖前自动备份为 `.YYYYMMDD.HHMMSS`）、启动并 enable chronyd 服务。集群其余节点通过 [`init ntpslave`](../init/network/ntpslave.md) 指向此 NTP 服务端。

支持两种模式：

- **配置文件模式**（`-c` 指定配置文件）：从 `cluster-sample.yml` 的 `global.ntpServer` 读取节点，SSH 到 `ntpServer.node` 远程执行；安装成功后将 `global.ntpServer.enable` 回写为 `true`。
- **手动模式**（不指定 `-c`）：在**本地节点**直接执行安装与配置。

## 用法 (Synopsis)

```bash
# 配置文件模式
datasophon-cli [--dry-run] create ntp-server -c <config.yml>

# 手动模式（本机）
datasophon-cli [--dry-run] create ntp-server
```

## 参数 / Flags

|    flag    |  简写  |   类型   |  默认  | 必填 |         说明         |
|------------|------|--------|------|----|--------------------|
| `--config` | `-c` | string | `""` | 否  | 配置文件路径；指定后进入配置文件模式 |

> 继承全局 flag：`--dry-run` —— 详见 [global-flags.md](../../global-flags.md)

## 配置文件依赖（配置文件模式）

|           字段            |                 说明                 |
|-------------------------|------------------------------------|
| `global.ntpServer.node` | NTP 服务端节点 hostname（须在 `nodes` 列表中） |
| `global.sshAuthType`    | SSH 鉴权方式                           |

## 写入的 chrony.conf 内容

```
server 127.0.0.1 iburst
driftfile /var/lib/chrony/drift
makestep 1.0 3
rtcsync
allow all
local stratum 10
keyfile /etc/chrony.keys
leapsectz right/UTC
logdir /var/log/chrony
```

> `allow all` 表示开放所有客户端访问，配合内网防火墙限制使用；如需收紧，安装后再手动改 `allow <CIDR>`。

## OS 差异

|           OS 类型           |                         包安装命令                          |           配置路径            |         服务名          |
|---------------------------|--------------------------------------------------------|---------------------------|----------------------|
| CentOS / openEuler / RHEL | `yum -y install chrony`                                | `/etc/chrony.conf`        | `chronyd`            |
| Ubuntu / Debian           | `DEBIAN_FRONTEND=noninteractive apt install chrony -y` | `/etc/chrony/chrony.conf` | `chrony` / `chronyd` |

## 示例

### 配置文件模式

```bash
datasophon-cli create ntp-server \
  -c /data/datasophon/datasophon-init/config/cluster-sample.yml
# 成功后：global.ntpServer.enable: true 回写
```

### 手动模式（本机）

```bash
datasophon-cli create ntp-server
```

### dry-run 预检

```bash
datasophon-cli --dry-run create ntp-server -c /data/datasophon/datasophon-init/config/cluster-sample.yml
```

## 退出码 / 常见错误

|               错误信息                |                   根因                   |                 处置                 |
|-----------------------------------|----------------------------------------|------------------------------------|
| `配置中未找到 ntpServer 节点: <hostname>` | `global.ntpServer.node` 不在 `nodes` 列表中 | 检查 hostname 拼写                     |
| `chrony 安装失败`                     | yum/apt 源不可用                           | 检查节点网络或先配置 `create yum-server` 离线源 |
| `安装成功但写回配置文件失败`                   | 配置文件写权限不足                              | 检查写权限                              |

## 相关命令

- [`init ntpslave`](../init/network/ntpslave.md) — 其余节点配置为 NTP 客户端，指向本命令安装的 server
- [`create cluster`](./cluster.md) — 集群初始化（DAG 步骤 22 `init-ntp-server` 复用同一 `ntpServerTask`）

