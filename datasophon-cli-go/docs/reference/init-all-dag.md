# DAG 步骤参考表

本页列出两套执行序列的完整步骤。数据来源：`internal/plan/registry.go`（initALL）和 `internal/cli/create/initializer.go`（standalone 10 步硬编码）。

> `initSingleNode` 17 步 DAG 已随 `addNodes` 批量模式一并移除，新增节点统一通过 `create node`（独立模式，10 步 standalone）完成。

## initALL — 33 步（全量集群初始化）

由 `create cluster`（默认执行）或 `create cluster apply` 触发，通过 plan 引擎持久化，支持断点续跑。

步骤的执行有两个维度的过滤：

- **Scope（集群类型）**：由 `--type` / `cluster-sample.yml type` 字段控制，优先过滤。`hadoop`：仅执行 `both`/`hadoop-only` 步骤；`kubernetes`：仅执行 `both`/`k8s-only` 步骤。
- **Condition（模块开关）**：通过 `global.*` 字段控制，Scope 通过后再判断。

| 序号 | Step ID | 步骤名 | Scope | 节点范围 | 触发条件（Condition） | 对应命令页 |
|---|---|---|---|---|---|---|
| 1 | `init-bin-package` | 分发资源包 | both | 全节点 | 无（必执行） | [bin_packages](../commands/init/packages/bin_packages.md) |
| 2 | `init-bash` | shell bash 设置 | both | 全节点 | 无 | [bash](../commands/init/system/bash.md) |
| 3 | `init-tar` | 安装 tar | both | 全节点 | 无 | [tar](../commands/init/packages/tar.md) |
| 4 | `init-rustfs` | 安装 RustFS | both | RustFS 节点 | `registry.enable && rustfs.enable` | [create rustfs](../commands/create/rustfs.md) |
| 5 | `init-registry` | 安装 Nexus Registry | both | Registry 节点 | `registry.enable` | [create registry](../commands/create/registry.md) |
| 6 | `init-docker-for-registry` | 安装 Docker（Registry 阶段） | **k8s-only** | K8s 节点 | `registry.enable && kubernetes.enable` | [docker](../commands/init/k8s/docker.md) |
| 7 | `init-registry-upload` | 上传安装包到 Nexus | both | 本机（执行节点） | `registry.enable` | [upload registry](../commands/upload/registry.md) |
| 8 | `init-jdk8` | 安装 JDK 8 | both | 全节点 | 无 | [jdk8](../commands/init/packages/jdk8.md) |
| 9 | `init-jdk17` | 安装 JDK 17 | both | 全节点 | 无 | [jdk17](../commands/init/packages/jdk17.md) |
| 10 | `init-osuser` | 创建 hadoop 用户和组 | **hadoop-only** | 全节点 | 无 | [osUser](../commands/init/system/osuser.md) |
| 11 | `init-firewall` | 关闭防火墙 | both | 全节点 | 无 | [firewall](../commands/init/system/firewall.md) |
| 12 | `init-selinux` | 关闭 SELinux | both | 全节点 | 无 | [selinux](../commands/init/system/selinux.md) |
| 13 | `init-swap` | 关闭 Swap | both | 全节点 | 无 | [swap](../commands/init/system/swap.md) |
| 14 | `init-offline-server` | yum/apt 离线源服务 | both | 离线源节点 | `yumServer.enable` | [create yum-server](../commands/create/yum-server.md) |
| 15 | `init-offline-nodes` | yum/apt 离线源节点配置 | both | 全节点 | `yumServer.enable \|\| registry.enable` | [offlineSlave](../commands/init/repo/offlineslave.md) |
| 16 | `init-library` | 初始化依赖库 | both | 全节点 | 无 | [library](../commands/init/system/library.md) |
| 17 | `init-os-safe-conf` | 安全配置 | both | 全节点 | 无 | [osSafeConf](../commands/init/system/ossafeconf.md) |
| 18 | `init-system-conf` | 优化系统配置 | both | 全节点 | 无 | [system-conf](../commands/init/system/system-conf.md) |
| 19 | `init-hostname` | 配置 hostname | both | 全节点 | 无 | [hostname](../commands/init/network/hostname.md) |
| 20 | `init-all-host` | 配置 /etc/hosts | both | 全节点 | 无 | [allHost](../commands/init/network/allhost.md) |
| 21 | `init-nmap` | 安装 nmap | both | nmap 节点 | `nmapServer.enable` | [create nmap-server](../commands/create/nmap-server.md) |
| 22 | `init-ntp-server` | 配置 NTP Server | both | NTP Server 节点 | `ntpServer.enable` | [create ntp-server](../commands/create/ntp-server.md) |
| 23 | `init-ntp-slave` | 配置 NTP Slave | both | 全节点 | `ntpServer.enable` | [ntpslave](../commands/init/network/ntpslave.md) |
| 24 | `init-mysql` | 安装 MySQL | both | MySQL 节点 | `mysql.enable` | [create mysql](../commands/create/mysql.md) |
| 25 | `init-mysql-app-db` | 初始化 MySQL 数据库和账号 | both | MySQL 节点 | `mysql.enable` | [mysql_app_db](../commands/init/db/mysql_app_db.md) |
| 26 | `k8s-base-services` | 安装 K8s 集群（sealos） | **k8s-only** | K8s 节点 | `kubernetes.enable` | [k8sBaseServices](../commands/init/k8s/k8sbaseservices.md) |
| 27 | `k8s-kuboard` | 安装 Kuboard | **k8s-only** | K8s Master | `kubernetes.enable && kuboard.enable` | [kuboard](../commands/init/k8s/kuboard.md) |
| 28 | `k8s-registry-conf` | 配置 K8s 私有仓库 | **k8s-only** | K8s 节点 | `kubernetes.enable` | [k8sRegistryConf](../commands/init/k8s/k8sregistryconf.md) |
| 29 | `k8s-docker` | 安装 Docker（K8s 阶段） | **k8s-only** | K8s 节点 | `kubernetes.enable` | [docker](../commands/init/k8s/docker.md) |
| 30 | `k8s-kubectl` | 安装 kubectl | **k8s-only** | K8s 节点 | `kubernetes.enable` | [kubectl](../commands/init/k8s/kubectl.md) |
| 31 | `k8s-helm` | 安装 Helm | **k8s-only** | K8s 节点 | `kubernetes.enable` | [helm](../commands/init/k8s/helm.md) |
| 32 | `k8s-helmify` | 安装 Helmify | **k8s-only** | K8s 节点 | `kubernetes.enable` | [helmify](../commands/init/k8s/helmify.md) |
| 33 | `init-hugepage` | 关闭透明大页 | both | 全节点 | 无 | [hugePage](../commands/init/system/hugepage.md) |

## standalone — 10 步（新增节点初始化）

由 `create node --ip <IP> --user <user> --password <pass> --port <port> --hostname <hn>` 触发，**不使用 plan 引擎**，由 `initializer.go` 硬编码顺序执行，不支持断点续跑（所有步骤幂等，可直接重跑）。

| 序号 | 步骤名 | 对应命令页 |
|---|---|---|
| 1 | shell bash 设置 | [bash](../commands/init/system/bash.md) |
| 2 | 创建 hadoop 用户和组 | [osUser](../commands/init/system/osuser.md) |
| 3 | 关闭防火墙 | [firewall](../commands/init/system/firewall.md) |
| 4 | 关闭 SELinux | [selinux](../commands/init/system/selinux.md) |
| 5 | 关闭 Swap | [swap](../commands/init/system/swap.md) |
| 6 | 初始化依赖库 | [library](../commands/init/system/library.md) |
| 7 | 安全配置 | [osSafeConf](../commands/init/system/ossafeconf.md) |
| 8 | 优化系统配置 | [system-conf](../commands/init/system/system-conf.md) |
| 9 | 配置 hostname | [hostname](../commands/init/network/hostname.md) |
| 10 | 关闭透明大页 | [hugePage](../commands/init/system/hugepage.md) |

> standalone 模式不生成 `state/*.plan.json`，中途失败需从第 1 步重跑（幂等设计，重跑安全）。

## 各步骤的过滤字段

### Scope（集群类型，优先过滤）

由 `--type` CLI flag 或 `cluster-sample.yml` 顶层 `type` 字段决定（CLI 优先）：

| 集群类型 | 执行的 Scope | 跳过的 Scope |
|---|---|---|
| `hadoop` | both + hadoop-only | k8s-only |
| `kubernetes` | both + k8s-only | hadoop-only |

### Condition（模块开关，Scope 通过后生效）

条件触发的步骤均从 `cluster-sample.yml` 读取，与 CLI flag 无关：

| 条件字段 | 控制的步骤 |
|---|---|
| `global.registry.enable` | init-rustfs（部分）、init-registry、init-docker-for-registry、init-registry-upload、init-offline-nodes（部分） |
| `global.rustfs.enable` | init-rustfs |
| `global.yumServer.enable` | init-offline-server、init-offline-nodes（部分） |
| `global.ntpServer.enable` | init-ntp-server、init-ntp-slave |
| `global.nmapServer.enable` | init-nmap |
| `global.mysql.enable` | init-mysql、init-mysql-app-db |
| `global.kubernetes.enable` | k8s-base-services、k8s-kuboard（部分）、k8s-registry-conf、k8s-docker（k8s 阶段）、k8s-kubectl、k8s-helm、k8s-helmify、init-docker-for-registry（部分） |
| `global.kubernetes.kuboard.enable` | k8s-kuboard |
