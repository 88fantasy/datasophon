# init ntpserver — 配置 NTP 服务端

> 源文件：`datasophon-cli/src/main/java/com/datasophon/cli/init/InitNtpServer.java`

## 用途

在目标节点上安装并配置 `chrony` 作为 NTP 时间服务端，其他节点通过 `init ntpslave`（内部命令）同步到此节点。

---

## 参数

无独有参数，仅继承 [InitBase 公共参数](./README.md#公共参数initbase)。

> **提示**：单独运行此命令时，节点信息从 cluster-sample.yml 读取（`-c` + `-cpwd`），但 `InitNtpServer.doRun()` 本身不读取 yml，只在本地安装 chrony。`-c` / `-cpwd` 在此命令中无实际作用，可忽略。

---

## 示例

```bash
java -jar datasophon-cli.jar init ntpserver
```

---

## 安装的 chrony 配置

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

> 关键参数说明：
> - `allow all`：允许所有客户端同步
> - `local stratum 10`：无上游时仍作为有效时间源（适合离线环境）

---

## 行为说明

| OS 类型 | 安装命令 | 配置文件路径 | 服务名 |
|---|---|---|---|
| CentOS/OpenEuler | `yum -y install chrony` | `/etc/chrony.conf` | `chronyd` |
| Ubuntu/Debian | `apt install chrony -y` | `/etc/chrony/chrony.conf` | `chrony` |

安装后备份原配置（`mv .conf .conf.$(date +%Y%m%d.%H%M%S)`），写入新配置，重启服务。

---

## 注意事项

- 此命令只在 `create cluster` 编排中对 `global.ntpServer.node` 指定的**单个节点**执行（NTP 服务端唯一）。
- 其余节点的时间同步由内部命令 `InitNtpSlave` 负责（见 [internal-commands.md](../internal-commands.md)）。
- 生产环境建议 NTP 服务端也配置外部上游 NTP 服务器（如 `pool.ntp.org`）以保证准确性，当前配置使用 `127.0.0.1` 作为来源适用于完全离线环境。
