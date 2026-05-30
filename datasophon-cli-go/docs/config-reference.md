# 配置文件参考（cluster-sample.yml）

集群配置文件是 `datasophon-cli create cluster` 的唯一配置源。默认路径：

```
<datasophonPath>/datasophon-init/config/cluster-sample.yml
```

可用 [`create config`](./commands/create/config.md) 生成带随机密码的初始模板，然后按本文档修改各字段。

---

## 顶层结构

```yaml
type:          # 集群类型（hadoop | kubernetes），与 --type CLI flag 对应
global:        # 全局配置（registry / mysql / ntp / kubernetes / packages…）
nodes:         # 集群节点列表（至少 1 个，含主节点）
```

### type

| 字段 | 类型 | 默认 | 必填 | 说明 |
|---|---|---|---|---|
| `type` | string | — | 是 | 集群类型。`hadoop`：Hadoop 大数据集群（含 osuser 步骤，跳过 k8s-*）；`kubernetes`：K8s 集群（包含所有 k8s-* 步骤，跳过 osuser） |

`type` 也可通过 `--type` / `-t` CLI flag 覆盖（flag 优先于文件中的值）。两者均需为 `hadoop` 或 `kubernetes`，否则命令启动时报错。

> 早期版本支持的 `addNodes:` 顶层字段已随 `create node` 批量模式一并移除。扩容时通过 `create node --ip ...` 对单节点初始化，成功后由命令自动追加到 `nodes` 列表。

---

## global

### global.offline

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `offline` | bool | `false` | `true` 表示离线环境，所有包从本地或制品库获取，不访问公网 |

---

### global.osInfo

系统类型与架构信息，影响包管理器选择（yum/apt）和安装包文件名。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `auto` | bool | `true` | `true` 则自动探测（推荐）；`false` 则使用下方手动指定的值 |
| `osType` | string | `""` | 手动指定 OS 类型，如 `openEuler-22.03-LTS-SP3`、`centos7`、`ubuntu-22.04` |
| `archType` | string | `""` | 手动指定架构：`x86_64` 或 `aarch64` |

---

### global.sshAuthType

| 字段 | 类型 | 可选值 | 默认 |
|---|---|---|---|
| `sshAuthType` | string | `AUTO` / `PASSWORD` / `PUBLICKEY` | `AUTO` |

详见 [全局选项 → SSH 鉴权](./global-flags.md#ssh-鉴权)。

---

### global.registry

控制 Nexus 制品库的安装与使用。`enable: true` 时，DAG 中会激活 `init-registry`、`init-registry-upload`、`init-offline-nodes` 等步骤。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否启用制品库 |
| `disableUpload` | bool | `false` | `true` 时跳过上传步骤（制品库已有包时使用） |
| `type` | string | `"nexus"` | 制品库类型，目前仅支持 `nexus` |
| `node` | string | 必填 | 运行 Nexus 的节点 hostname（须在 `nodes` 列表中） |
| `config.webPort` | int | `8091` | Nexus Web UI 端口 |
| `config.user` | string | `"admin"` | Nexus 管理员用户名 |
| `config.password` | string | 必填 | Nexus 管理员密码（`create config` 自动随机生成） |
| `config.dockerHttpPort` | int | `8083` | Docker 镜像仓库 HTTP 端口（K8s 场景需要） |
| `config.repositories` | []string | `["yum","raw","apt","docker","helm"]` | 要创建的仓库类型列表 |

**相关 DAG 步骤**：`init-registry`（步骤 5）、`init-registry-upload`（步骤 7）、`init-offline-nodes`（步骤 15）

---

### global.rustfs

对象存储服务（Rustfs，兼容 S3 协议）。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否安装 Rustfs |
| `nodes` | []string | `[]` | 运行 Rustfs 的节点 hostname 列表 |
| `config.webPort` | int | `9041` | Web 管理端口 |
| `config.apiPort` | int | `9040` | S3 API 端口 |
| `config.user` | string | `"admin"` | 管理员用户名 |
| `config.password` | string | 必填 | 管理员密码 |
| `config.installType` | string | `"SNSD"` | 部署模式：`SNSD`（单节点单盘）/ `SNMD`（单节点多磁盘）/ `MNMD`（多节点多磁盘） |
| `config.volumes` | string | `"/data/rustfs0"` | 存储路径。SNSD: `/data/rustfs0`；SNMD: `/data/rustfs{0...3}`；MNMD: `http://node{1...4}:9040/data/rustfs{0...3}` |

**相关 DAG 步骤**：`init-rustfs`（步骤 4，条件：`registry.enable && rustfs.enable`）

---

### global.yumServer

本地 Yum/Apt 离线源服务器（HTTP 文件服务器）。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否启用离线源服务器 |
| `node` | string | 必填 | 运行离线源的节点 hostname |
| `listenPort` | int | `4080` | HTTP 监听端口 |

**相关 DAG 步骤**：`init-offline-server`（步骤 14）、`init-offline-nodes`（步骤 15，条件：`yumServer.enable || registry.enable`）

---

### global.nmapServer

Nmap 网络扫描工具安装。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否安装 nmap/netstat |
| `node` | string | 必填 | 安装目标节点 hostname |

**相关 DAG 步骤**：`init-nmap`（步骤 21，条件：`nmapServer.enable`）

---

### global.mysql

MySQL 8.x 安装与数据库初始化。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否安装 MySQL |
| `force` | bool | `false` | 若 MySQL 已存在则强制重装 |
| `user` | string | `"root"` | MySQL root 用户名 |
| `password` | string | 必填 | MySQL root 密码（`create config` 自动随机生成） |
| `port` | int | `3306` | MySQL 端口 |
| `node` | string | 必填 | 安装 MySQL 的节点 hostname |
| `appDbs[*].account` | string | 必填 | 应用数据库用户名 |
| `appDbs[*].password` | string | 必填 | 应用数据库密码 |
| `appDbs[*].dbName` | string | 必填 | 数据库名 |

**典型 appDbs 配置**（datasophon、hive、dolphinscheduler、ustream、nacos、bigdata、juicefs 等）。

**相关 DAG 步骤**：`init-mysql`（步骤 24）、`init-mysql-app-db`（步骤 25，每个 appDb 一个 Action）

---

### global.ntpServer

NTP 时钟服务。

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否配置 NTP |
| `node` | string | 必填 | NTP 服务端节点 hostname（其余节点自动配为客户端） |

**相关 DAG 步骤**：`init-ntp-server`（步骤 22）、`init-ntp-slave`（步骤 23，排除 server 自身）

---

### global.kubernetes

Kubernetes 集群部署（使用 Sealos 方案）。

#### global.kubernetes（顶层）

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否部署 K8s。`false` 时所有 `k8s-*` 步骤均跳过 |
| `force` | bool | `false` | 强制重装 K8s |

> `onlyInstall` 字段已移除，由顶层 `type: kubernetes` 取代。

#### global.kubernetes.baseServices

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `namespaces` | []string | `[]` | 预创建的命名空间列表 |
| `masters` | []string | 必填 | K8s Master 节点 hostname 列表 |
| `nodes` | []string | 必填 | K8s Worker 节点 hostname 列表 |
| `sealos` | bool | `true` | 是否使用 Sealos 安装 K8s |
| `kubernetesI` | bool | `true` | 是否安装 Kubernetes 组件 |
| `helmI` | bool | `true` | 是否安装 Helm（集群内） |
| `calicoI` | bool | `true` | 是否安装 Calico CNI |
| `ingressI` | bool | `true` | 是否安装 Ingress NGINX |

**相关 DAG 步骤**：`k8s-base-services`（步骤 26）

#### global.kubernetes.kuboardI

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `enable` | bool | `false` | 是否安装 Kuboard |
| `node` | string | 必填 | 安装 Kuboard 的节点 hostname |
| `etcdNodes` | []string | 必填 | etcd 节点列表（至少 3 个） |

**相关 DAG 步骤**：`k8s-kuboard`（步骤 27，条件：`kubernetes.enable && kuboardI.enable`）

#### global.kubernetes.k8sTools

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `docker` | bool | `true` | 是否在 K8s 节点安装 Docker |
| `helm` | bool | `true` | 是否安装 Helm CLI 工具 |
| `helmify` | bool | `true` | 是否安装 Helmify（K8s 清单 → Helm Chart 转换工具） |
| `kubectl` | bool | `true` | 是否安装 kubectl |

---

### global.packages

各组件安装包的文件名，与 `<datasophonPath>/datasophon-init/packages/` 目录拼接定位实际文件。`x86_64` 和 `aarch64` 分别对应两种架构。

| 字段 | 类型 | 示例值 |
|---|---|---|
| `os` | string | `openEuler-22.03-LTS-SP3.tar.gz` |
| `config` | string | `config.tar.gz` |
| `soft` | string | `packages.tar.gz` |
| `nexus.x86_64` | string | `nexus-3.85.0-03-linux-x86_64.tar.gz` |
| `nexus.aarch64` | string | `nexus-3.85.0-03-linux-aarch_64.tar.gz` |
| `mysql.x86_64` | string | `mysql-8.0.28-1.el8.x86_64.rpm-bundle.tar` |
| `mysql.aarch64` | string | `mysql-8.0.28-1.el8.aarch64.rpm-bundle.tar` |
| `rustfs.x86_64` | string | `rustfs-linux-x86_64-musl-1.0.0.tar.gz` |
| `rustfs.aarch64` | string | `rustfs-linux-aarch64-musl-1.0.0.tar.gz` |
| `sealos.x86_64` | string | `sealos_5.1.0_linux_amd64.tar.gz` |
| `sealos.aarch64` | string | `sealos_5.1.0_linux_arm64.tar.gz` |
| `kubernetesI.x86_64` | string | `kubernetes-v1.31.8-x86.tar` |
| `kubernetesI.aarch64` | string | `kubernetes-v1.31.8-arm.tar` |
| `helmI.x86_64` | string | `helm-v4.0.1-x86.tar` |
| `helmI.aarch64` | string | `helm-v4.0.1-arm.tar` |
| `calicoI.x86_64` | string | `calico-v3.28.1-x86.tar` |
| `calicoI.aarch64` | string | `calico-v3.28.1-arm.tar` |
| `ingressI.x86_64` | string | `ingress-nginx-4.1.0-x86.tar` |
| `ingressI.aarch64` | string | `ingress-nginx-4.1.0-arm.tar` |
| `kuboardI.x86_64` | string | `kuboard-v3-x86.tar` |
| `kuboardI.aarch64` | string | `kuboard-v3-arm.tar` |
| `helmify.x86_64` | string | `helmify_Linux_x86_64.tar.gz` |
| `helmify.aarch64` | string | `helmify_Linux_arm64.tar.gz` |
| `docker.x86_64` | string | `docker-x86-29.3.1.tgz` |
| `docker.aarch64` | string | `docker-arm-29.3.1.tgz` |
| `helm.x86_64` | string | `helm-v4.0.1-linux-amd64.tar.gz` |
| `helm.aarch64` | string | `helm-v4.0.1-linux-arm64.tar.gz` |
| `kubectl.x86_64` | string | `kubectl-x86` |
| `kubectl.aarch64` | string | `kubectl-arm` |

---

## nodes

集群节点列表。`create cluster` 执行时，工具会用当前机器的本机 IP 匹配 `nodes[*].ip`，找到的节点作为"本地节点"直接用 LocalExecutor 操作，其余节点通过 SSH 远程操作。

**nodes 至少需要 1 个节点。**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `ip` | string | 是 | 节点 IP |
| `port` | int | 是 | SSH 端口（通常为 22） |
| `user` | string | 是 | SSH 登录用户名（通常为 root） |
| `password` | string | 否 | SSH 密码（`sshAuthType: PUBLICKEY` 时可留空） |
| `hostname` | string | 是 | 节点 hostname（须与 OS 配置一致，其他字段的 `node` 引用该值） |

```yaml
nodes:
  - ip: 192.168.1.10
    port: 22
    user: root
    password: "YourPassword"
    hostname: app1
  - ip: 192.168.1.11
    port: 22
    user: root
    password: "YourPassword"
    hostname: app2
```

---

## 节点扩容

新节点不再通过配置文件提前声明。请直接对单节点执行：

```bash
datasophon-cli create node \
  -p /data/datasophon --installPath /opt/install -n /data/datasophon/datasophon-init/packages \
  --ip 192.168.1.20 --user root --password "YourPassword" --port 22 --hostname app7
```

命令完成后，该节点会**自动追加**到 `cluster-sample.yml` 的 `nodes` 列表。详见 [`create node`](./commands/create/node.md)。

---

## 完整配置示例

完整带注释的参考示例见 `internal/config/configs/cluster-config.yml`（`create config` 的输出模板）。
