# 五节点无 Hadoop Doris 集群部署与验收手册

> 范围：使用 `datasophon-cli-go` 初始化五台虚拟机并创建 DataSophon 控制面基础设施；随后从 DataSophon 前端创建集群、完成 Worker 与 OTel Collector 集群初始化，最后通过前端导入 DAG 安装剩余阶段 A 组件。本文不执行任何生产变更，所有现场配置、plan 和证据均保存在 Git 外。
>
> 凭据规则：本文只使用 `<ROOT_PASSWORD>`、`<MYSQL_PASSWORD>`、`<RUSTFS_ACCESS_KEY>` 等占位符。不得把真实密码、私钥、token、JDBC 凭据或未经脱敏的截图写入仓库、终端录屏或验收报告。

## 1. 目标、边界与阶段

### 1.1 节点清单

| Hostname | IP | SSH 用户 | 阶段 A 初始用途 |
| --- | --- | --- | --- |
| `ddh-01` | `192.168.10.131` | `root` | 管理面、Nexus、MySQL、NTP Server、RustFS、单 Doris FE |
| `ddh-02` | `192.168.10.132` | `root` | Worker、OTel Collector，阶段 A 中间件主要承载节点，不部署 Doris |
| `ddh-03` | `192.168.10.133` | `root` | Worker、OTel Collector、Doris BE |
| `ddh-04` | `192.168.10.134` | `root` | Worker、OTel Collector、Doris BE |
| `ddh-05` | `192.168.10.135` | `root` | Worker、OTel Collector、Doris BE |

> 拓扑已于 Phase 2 冻结为 `1 FE + 3 BE`（详见 §5 Gate 2 冻结结论）。单 FE 不具备高可用，`ddh-01` 或 FE 故障会导致 Doris 服务不可用；这是本次功能验证已知且已批准的偏差。

### 1.1.1 已验证现场基线（2026-07-12）

五台节点均为 `openEuler 22.03 LTS-SP3 x86_64`、`16 vCPU / 30 GiB RAM`、`4 GiB swap`，仅监听 SSH，未发现既有 MySQL、Nexus、RustFS、Doris 或 DataSophon 进程。系统盘 `/dev/vda3` 可用约 `37 GiB`；独立数据盘已完成以下准备：

| 节点 | 数据设备 | 文件系统 | 挂载点 | 可用容量 | 持久化验证 |
| --- | --- | --- | --- | ---: | --- |
| `ddh-01`（`192.168.10.131`） | `/dev/vdb` | `ext4` | `/data` | `466.1 GiB` | UUID 已写入 `/etc/fstab`，`mount -a` 通过 |
| `ddh-02`（`192.168.10.132`） | `/dev/vdb` | `ext4` | `/data` | `466.1 GiB` | UUID 已写入 `/etc/fstab`，`mount -a` 通过 |
| `ddh-03`（`192.168.10.133`） | `/dev/vdb` | `ext4` | `/data` | `466.1 GiB` | UUID 已写入 `/etc/fstab`，`mount -a` 通过 |
| `ddh-04`（`192.168.10.134`） | `/dev/vdb` | `ext4` | `/data` | `466.1 GiB` | UUID 已写入 `/etc/fstab`，`mount -a` 通过 |
| `ddh-05`（`192.168.10.135`） | `/dev/vdb` | `ext4` | `/data` | `466.1 GiB` | UUID 已写入 `/etc/fstab`，`mount -a` 通过 |

当前 hostname 仍为云主机默认值且存在重复，NTP 尚未同步、JDK 尚未安装；这些均由后续 CLI 初始化和对应 Gate 验证，不得手工绕过。

### 1.2 三层部署边界

1. **CLI 节点与控制面基础设施层**：`datasophon-cli-go` 初始化 OS、hostname、`/etc/hosts`、SSH、JDK、Nexus、MySQL、RustFS、NTP、系统依赖与离线包环境，创建 MySQL 数据库、上传 package，并创建/启动 `datasophon-api`。
2. **DataSophon 前端集群初始化层**：API 启动后，从前端新建集群、选择框架、配置五节点清单；前端为每个节点安装 Worker 与 OTel Collector，并完成集群初始化健康检查。
3. **DataSophon 前端 DAG 层**：仅在集群初始化通过后，从前端导入 DAG 安装其余组件；Doris FE/BE 与其余服务角色均不得写入 CLI YAML。

`-t hadoop` 仅表示 CLI 的非 Kubernetes 物理机 scope，不代表本阶段安装 HDFS、YARN 或任何 Hadoop 服务。

### 1.3 服务范围

| 阶段 | 服务 | 是否计入本次验收 |
| --- | --- | --- |
| 集群初始化 | `OTELCOLLECTOR`，由前端为每个节点安装；CLI 部署 RustFS 作为其 S3 兼容存储 | 是 |
| 阶段 A DAG | `DORIS`、`VALKEY`、`ZOOKEEPER`、`ELASTICSEARCH`、`APISIX`、`NACOS` | 是 |
| 阶段 B | `KYUUBI`、`DS`、`SPARK3`、`HIVE`、`HDFS`、`YARN` 等 | 否，另行立项 |

`DS` 的依赖闭包会引入 `SPARK3 → HIVE → HDFS → ZOOKEEPER`；Kyuubi/DS 的默认运行参数也依赖 YARN。因此它们不能被表述为“无 Hadoop”阶段 A 的组成部分。

## 2. Epic 进度跟踪

实施期间只更新状态与脱敏证据入口；不得把凭据、完整配置或 plan 正文提交到本仓库。

| Phase | 目标 | 状态 | 人工 Gate | 证据/产物 |
| --- | --- | --- | --- | --- |
| 0 | 五节点只读盘点与数据盘准备 | PASSED | 资源、磁盘、端口、数据保护与变更许可 | Git 外盘点包；五台 `/data` 持久挂载验证 |
| 1 | APISIX Standalone 产品适配 | PASSED（自动化） | 真实无 etcd 启动与路由转发仍待现场验证 | 模板单测、后续 DAG 证据 |
| 2 | 冻结拓扑、容量与服务角色 | PASSED | 管理面、FE/BE、中间件角色与资源预算 | 拓扑审批单（§5，1 FE + 3 BE） |
| 3 | 网络、时间、磁盘与离线包预检 | PASSED WITH DEVIATIONS | 数据盘、JDK17、架构与包校验 | manifest（§6 现场记录；阶段 A 服务包/JDK17/CLI 基础设施 bundle 延后） |
| 4 | CLI 配置生成与五节点审阅 | PASSED | 严格解析、引用、权限、敏感信息检查 | 脱敏配置 hash（§7.2.1） |
| 5 | CLI plan 生成与人工审批 | BLOCKED | 批准特定 plan hash 后才可 apply | plan hash / clusterHash（§7.2.1，待批准；用户已选择先确认离线包来源） |
| 6 | CLI apply 基础环境初始化 | BLOCKED | 失败续跑或重新 plan 的决定 | apply 状态 |
| 7 | 基础环境、RustFS 与 API 健康 | BLOCKED | 批准从前端创建集群 | 连接与健康检查 |
| 8 | 前端集群初始化：Worker 与 OTel Collector | BLOCKED | 五个节点检查均通过后才可导入服务 DAG | 初始化记录、注册与心跳证据 |
| 9 | 前端导入阶段 A 服务 DAG | BLOCKED | 每批角色和参数审批 | DAG 记录 |
| 10 | 阶段 A 业务与故障演练 | BLOCKED | 每次停止实例前单独审批 | SQL / 健康 / 演练报告 |
| 11 | 阶段 A 证据归档与结论 | BLOCKED | PASS / 偏差 / FAIL 审核 | 脱敏归档包 |
| 12 | 阶段 B Hadoop 扩展 | BLOCKED | 单独立项 | 后续计划 |

状态只能取 `NOT STARTED`、`IN PROGRESS`、`BLOCKED`、`PASSED`、`FAILED`、`ROLLED BACK`。

## 3. Phase 0：五节点只读盘点

每台节点以只读方式执行以下命令并归档到 Git 外目录。首次盘点期间禁止设置 hostname、安装软件、创建或清空目录、挂盘、修改防火墙或内核参数。

**现场完成记录（2026-07-12）**：五台节点均已完成只读盘点；随后经确认五块空白且未挂载的 `/dev/vdb` 可用于本 Epic，已格式化为 `ext4`、挂载至 `/data`，并使用 UUID 写入 `/etc/fstab`。五台节点均已执行 `mount -a` 成功验证；此磁盘准备是 Phase 0 之后获批准的受控变更，不属于初始只读采集命令。

```bash
hostnamectl
ip -brief address
ip route
uname -a
cat /etc/os-release
lscpu
free -h
swapon --show
lsblk -f
df -hT
df -ih
findmnt /data
chronyc tracking || timedatectl
ss -lntup
ps -ef | grep -E '[j]ava|[m]ysqld|[n]exus|[r]ustfs|[d]oris|[d]atasophon'
find /usr/lib/jvm -maxdepth 2 -type f -name java -print
getent hosts ddh-01 ddh-02 ddh-03 ddh-04 ddh-05
```

### Gate 0

确认并记录：

- 每台机器的预期网卡和 `192.168.10.0/24` 地址唯一；无多网卡路由歧义。
- 已确认五台节点规格为 `16 vCPU / 30 GiB RAM / 4 GiB swap`，运行 `openEuler 22.03 LTS-SP3 x86_64`；系统盘可用约 `37 GiB`。
- 已确认 `/dev/vdb` 在变更前无文件系统、无分区、无挂载；已格式化为 `ext4` 并挂载至 `/data`，每台可用约 `466.1 GiB`，UUID 持久化配置及 `mount -a` 已验证。
- 已监听端口及已有 MySQL、Nexus、RustFS、Doris、DataSophon 进程不会冲突。
- 时区、NTP 状态和跨节点时间偏差可接受。
- Doris FE 所需的 JDK17 与 `JAVA_HOME17` 可用性；平台自身使用 JDK21。
- 现场拥有对应架构的 CLI、API、Worker、服务安装包和元数据。

任何未确认项均使后续 Phase 保持 `BLOCKED`。

## 4. Phase 1：APISIX Standalone 适配

APISIX 使用 `3.17.0`（2026-07-12 从 3.16.0 升级，已用 `ApisixStandaloneTemplateTest` 与官方 changelog 复核不影响 Standalone 配置结构）的 Standalone 数据面模式，不依赖 etcd：

```yaml
deployment:
  role: data_plane
  role_data_plane:
    config_provider: yaml
```

DataSophon DDL 将生成：

| 文件 | 路径 | 用途 |
| --- | --- | --- |
| `config.yaml` | `/usr/local/apisix/conf/config.yaml` | 选择 YAML provider、监听 `9080`、关闭 Admin API、开启 Prometheus export server |
| `apisix.yaml` | `/usr/local/apisix/conf/apisix.yaml` | 一条可配置最小静态 route/upstream、Prometheus global rule、结尾 `#END` |

最小路由参数：`apisixRouteUri`、`apisixUpstreamHost`、`apisixUpstreamPort`。不包含路由 CRUD、热更新控制面或 etcd 兼容分支。

本地自动化验证：

```bash
JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home \
  ./mvnw -pl datasophon-worker -am test \
  -Dtest=ApisixStandaloneTemplateTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -s ~/.m2/setting.xml
```

现场 Gate 1 还必须完成：元数据上传 Nexus 后的 hash 核验、API 元数据刷新、隔离节点无 etcd 启动、最小路由转发与 `9091` metrics 抓取。自动化单测通过不替代这些真实验证。

## 5. Phase 2：冻结资源与服务角色

### Gate 2 冻结结论（2026-07-12，PASSED）

拓扑固定为 `1 FE + 3 BE`：

| 节点 | 基础设施/控制面 | Doris |
| --- | --- | --- |
| `ddh-01`（`192.168.10.131`） | API/Master、Nexus、MySQL、NTP Server、RustFS、Worker、OTel Collector | 单 FE，不部署 BE |
| `ddh-02`（`192.168.10.132`） | Worker、OTel Collector、NTP Client；阶段 A 中间件（`VALKEY`/`ZOOKEEPER`/`ELASTICSEARCH`/`NACOS`/`APISIX`）主要承载节点 | 不部署 Doris |
| `ddh-03`（`192.168.10.133`） | Worker、OTel Collector、NTP Client | BE |
| `ddh-04`（`192.168.10.134`） | Worker、OTel Collector、NTP Client | BE |
| `ddh-05`（`192.168.10.135`） | Worker、OTel Collector、NTP Client | BE |

**已知偏差**：单 FE 不具备 Doris 元数据高可用；`ddh-01` 或 FE 故障将导致 Doris 服务不可用。Phase 10 故障演练只允许单 BE 停机与恢复，不执行 FE 切换，最终结论不得表述为 Doris 高可用验收通过。

### `/data` 目录与容量预算

CLI 参数固定为 `datasophonPath=/data/datasophon`、`installPath=/data`。

| 节点 | 用途 | 预算 |
| --- | --- | ---: |
| `ddh-01` | `/data/nexusDir`（Nexus，含 blob） | 80 GiB |
| `ddh-01` | `/data/rustfs/data`（由 `--installPath` 推导，不依赖 `rustfs.config.volumes`） | 120 GiB |
| `ddh-01` | `/data/doris/fe/meta` | 20 GiB |
| `ddh-01` | DataSophon/安装包/临时文件 | 50 GiB |
| `ddh-01` | 余量 | 约 176 GiB |
| `ddh-02` | 阶段 A 中间件数据+软件目录（各中间件独立子目录） | ≤300 GiB |
| `ddh-02` | 日志/升级暂存 | 50 GiB |
| `ddh-02` | 余量 | 约 116 GiB |
| `ddh-03/04/05` | `/data/doris/be/storage` | 各 350 GiB |
| `ddh-03/04/05` | 软件/日志/暂存 | 各 40 GiB |
| `ddh-03/04/05` | 余量 | 各约 76 GiB |

MySQL datadir 仍是 `/var/lib/mysql`（系统盘），`--installPath=/data` 不会迁移；验证期预算 8～10 GiB 并监控系统盘剩余（当前约 37 GiB）。当前 `cluster plan` 只处理 `rustfs.nodes[0]`，RustFS 固定单节点 `ddh-01`。

### 内存预算

单 FE `-Xms4g -Xmx4g`；`ddh-03～05` BE 统一 `mem_limit=14G`（禁止使用默认 `100%`）；`ddh-01` 的 API/Nexus/MySQL/RustFS 各预留约 2 GiB，并保留至少 8 GiB 给 OS/页缓存/native memory；`ddh-02` 中间件内存预算延后到对应服务 DAG 的 Gate 9 单独确认。

### 仍待 Phase 9 确认

- 每项阶段 A 服务在 `ddh-02` 的具体子目录、端口与依赖矩阵。
- Doris `fe_priority_networks=192.168.10.0/24`、`be_priority_networks=192.168.10.0/24`。
- OTel Collector 的 RustFS endpoint、bucket 与凭据映射。
- APISIX 最小路由实际 upstream 地址与端口。

禁止直接使用 BE 默认 `mem_limit=100%`，也不得让 RustFS 与 Doris 数据在同一磁盘无预算共置。

## 6. Phase 3：网络、时间、磁盘与离线包预检

至少放通并验证以下端口方向：

| 服务 | 端口 |
| --- | --- |
| SSH | `22/TCP` |
| DataSophon API / Master gRPC / Worker gRPC | `8080/TCP`、`18081/TCP`、`18082/TCP` |
| MySQL / Nexus / NTP / RustFS | `3306/TCP`、`8081/TCP`、`123/UDP`、`9040/TCP`、`9041/TCP` |
| Doris FE | `18030`、`9020`、`9030`、`9010/TCP` |
| Doris BE | `9060`、`18040`、`8060`、`9050/TCP` |
| APISIX / metrics | `9080/TCP`、`9091/TCP` |

离线包 manifest 需记录版本、架构、来源、大小、SHA-256 与目标节点：CLI、manager/API、Worker、Nexus、MySQL、RustFS、JDK8/JDK17/JDK21、系统依赖、Doris 与阶段 A 服务包及元数据。

数据盘已于 2026-07-12 在五台节点完成 `ext4` 格式化并持久挂载为 `/data`。Phase 2 仍须将 `/data` 下的 RustFS、Doris FE metadata、Doris BE storage 与其他服务目录划分为独立子目录和容量预算；禁止将其不加约束地共置在同一目录。

### Phase 3 现场记录（2026-07-12，IN PROGRESS）

- **时钟漂移**：五节点 `date +%s` 相互偏差最大约 `119` 秒，确认 NTP 同步是配置生成前必须解决的真实问题（非只是防御性检查）。已决定 `ntpServer` 采用 `ddh-01` 本地时钟源（`stratum 10`，无外部上游依赖），`ddh-02～05` 作为 client，与集群隔离网络环境相符。
- **端口/SELinux/firewalld/swap**：复核与 Phase 0 结果一致，仅 `22/TCP` 监听，SELinux 已禁用，firewalld 未运行，无端口冲突。
- **基础环境安装包**：已下载 `OpenJDK8U-jdk_x64_linux_hotspot_8u492b09.tar.gz`、`OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz`（均 x86_64，SHA-256 已记录在本地 `package/raw/packages/*.sha256`，未入库）。
- **已知缺口（用户已确认延后）**：阶段 A 服务包（Doris/Elasticsearch/Nacos/ZooKeeper/Valkey/APISIX 源包）与 Doris FE 所需 JDK17，均未下载；用户明确选择先只完成基础环境部分，服务包留到 Phase 9 服务 DAG 前补齐。CLI 所需的 `nexus`/`mysql`/`rustfs`/`os`/`config`/`soft` 离线基础设施 bundle（`packages:` 段引用的文件名）在本仓库和本机均未找到来源，需现场另行确认制品来源，不属于公开 manifest 下载范围。
- **worker 打包**：`datasophon-worker` 已执行 `mvn package`（非仅 `test`），产出 `datasophon-worker.tar.gz`，供后续主机纳管使用。
- **CLI 交叉编译**：已执行 `make release`，产出 `datasophon-cli-linux-amd64`（供五节点使用）等四平台二进制。

## 7. Phase 4～6：CLI 配置、plan 与 apply

> **执行主机的架构约束（现场确认，2026-07-12）**：`create cluster plan/apply` 在 `setup()` 阶段会用 `os.Stat`/`os.MkdirAll` **在运行 CLI 的本机**校验 `datasophonPath` 存在、`installPath` 存在或可创建，随后同一个 `installPath` 又会被传给通过 SSH 在各节点执行的安装动作（Nexus/RustFS/MySQL/JDK 等）。这意味着 CLI **必须在集群某个节点自身（本次为 `ddh-01`）上运行**，因为只有 `ddh-01` 真正挂载了 `/data`；不能从外部跳板机（如运维人员的笔记本）以 `--installPath /data` 执行，否则会在本机报 `mkdir /data: read-only file system` 或在错误的本地目录创建 `/data`。`datasophonPath`（`-p`）建议直接使用 `ddh-01` 的 `/data/datasophon`。

### 7.1 生成配置

`create config` 与 `create cluster` 是同级子命令：

```bash
export DDH_HOME=<datasophonPath>
mkdir -p "$DDH_HOME/datasophon-init/config"
chmod 700 "$DDH_HOME/datasophon-init/config"

datasophon-cli create config \
  -t hadoop \
  -o "$DDH_HOME/datasophon-init/config/cluster-sample.yml"
chmod 600 "$DDH_HOME/datasophon-init/config/cluster-sample.yml"
```

配置中必须只引用上述五个 hostname，统一 SSH 用户 `root`；Registry、MySQL、NTP、nmap、RustFS 的 `node`/`nodes` 引用必须存在。移除样例中 `app*`、`ddh1` 等残留项。

不要给 CLI YAML 添加 `doris`、`feNodes` 或服务 DAG 专用字段：loader 启用严格字段解析，未知字段会失败。

### 7.2 Plan 与人工审批

```bash
datasophon-cli create cluster plan \
  -t hadoop \
  -p "$DDH_HOME" \
  --installPath <installPath> \
  -n <productPackagesPath>
```

plan 固定写入：

```text
$DDH_HOME/datasophon-init/state/initALL.plan.json
```

设置权限 `0600`，并在 Gate 5 审阅 `action=initALL`、`clusterHash`、targets、Step 顺序和 Kubernetes Step 跳过情况。plan 中不得出现 Doris 或任何阶段 A 服务安装。

`clusterHash` 是解析后的 `ClusterConfig` JSON 的 SHA-256 前 16 位。任意语义配置变更必须重新 plan 并重走 Gate 5；YAML 注释和键排序通常不影响 hash。

### 7.2.1 Gate 4/5 现场执行记录（2026-07-12）

配置在 `ddh-01` 的 `/data/datasophon/datasophon-init/config/cluster-sample.yml` 生成并按 §7.1 要求重写：五个 hostname 全部为 `ddh-01～05`、`registry`/`mysql`/`ntpServer`/`nmapServer`.node 均为 `ddh-01`、`rustfs.nodes: [ddh-01]`，未添加任何 Doris/服务 DAG 字段，文件权限 `0600`。

Gate 4 结论：**PASSED**。严格解析通过（`plan` 成功生成即证明解析无未知字段错误），全部 hostname 引用存在。

```bash
DDH_HOME=/data/datasophon /data/datasophon/datasophon-cli create cluster plan \
  -t hadoop \
  -p /data/datasophon \
  --installPath /data \
  -n /data/datasophon/datasophon-init/product-packages
```

plan 生成 34 个 Step（24 pending + 10 skipped，0 completed），写入 `/data/datasophon/datasophon-init/state/initALL.plan.json`（权限 `0600`）。审阅结论：

- `action=initALL`，`clusterHash=a85f980f11de2abc`。
- plan 文件 SHA-256：`e7e39b422b7b79affa82e2380756569b7ccbb7c9cb713a9c815f0396be1c23ca`。
- 脱敏配置文件 SHA-256：`4a887b90e1562f5cae4ab417c16af1425ff36c3a9916a836eab5a47ad7f56971`。
- CLI 构建自 commit `bff406b1`（分支 `verify/cli-go-five-node-bootstrap`）。
- targets 仅 `ddh-01～05`；`init-rustfs`/`init-registry`/`init-registry-upload`/`init-nmap`/`init-ntp-server`/`init-mysql`/`init-mysql-app-db` 均只落在 `ddh-01`，与 Gate 2 冻结拓扑一致。
- 全部 8 个 Kubernetes Step（`k8s-base-services` 等）与 `init-docker-for-registry`、`init-offline-server` 共 10 项为 `skipped`，符合物理机 scope 及 `yumServer.enable=false`。
- plan 中不包含 Doris、Worker、OTel Collector 或任何阶段 A 服务安装 Step。
- 明确包含以下需要审批的破坏性/安全相关变更：`init-firewall`（关闭防火墙）、`init-selinux`（关闭 SELinux）、`init-swap`（关闭 swap）、`init-hostname`/`init-all-host`（改 hostname 与 `/etc/hosts`）、`init-os-safe-conf`/`init-system-conf`（系统安全与内核参数）、`init-hugepage`（关闭透明大页）。

**Gate 5 结论：待人工批准，尚未执行 `apply`。** 上述 `clusterHash + plan SHA-256 + 拓扑版本（1 FE + 3 BE，2026-07-12 冻结）` 三项需用户明确批准后才能进入 §7.3；批准 plan 不等于批准 apply。因阶段 A 服务包、Doris FE JDK17 及 CLI 所需的 `nexus`/`mysql`/`rustfs`/`os`/`config`/`soft` 基础设施 bundle 均尚未就位，即便获批 `apply` 也会在对应 Step 因缺少安装包而失败，需在批准前一并确认这些制品的到位计划。

**当前状态（2026-07-12）**：用户已明确选择暂停，自行确认 `nexus`/`mysql`/`rustfs`/`os`/`config`/`soft` 离线 bundle 的获取渠道后再继续；本 Epic 在此停止自动化推进，等待离线包来源确认后再重新进入本节。

### 7.3 Apply 与续跑

```bash
datasophon-cli create cluster apply \
  -t hadoop \
  -p "$DDH_HOME" \
  --installPath <installPath> \
  -n <productPackagesPath>
```

独立 `apply` 不会再次请求人工确认。失败后记录 Step ID、targets、错误与已完成状态，由 Gate 6 决定修复现场后续跑还是改配置后重新 plan。

续跑粒度是 Step：`completed`/`skipped` 跳过，`pending`/`failed`/`running` 重跑；多节点 Step 内已经成功的 Action 也可能再次执行。不得手工改 plan 状态绕过失败。

## 8. Phase 7～8：控制面健康与前端集群初始化

基础环境验收：hostname 和 hosts、节点 SSH、NTP、Nexus、MySQL、RustFS S3、plan 状态、数据盘与 JDK17 可用。确认无 Kubernetes 业务组件及 Hadoop 业务进程。

CLI 创建并启动 `datasophon-api` 后，验证 MySQL 连接、迁移、HTTP `8080` 和 Master gRPC `18081`。只有控制面健康后，才能从前端执行下列集群初始化：

1. 新建集群并选择本次物理机框架；
2. 从前端配置 `ddh-01`～`ddh-05` 的节点清单；
3. 为每个节点安装并启动 Worker 与 OTel Collector；
4. 在前端检查五个节点的初始化结果全部通过，再标记集群初始化完成。

集群初始化验收还需确认：

- Worker 的 `masterHost=192.168.10.131`，hostname/IP 一一对应。
- 五个节点均监听 `18082`，完成首次注册、每 30 秒心跳及非破坏性命令回拨。
- API/Worker 版本一致；重启 API 后 Worker 能重新注册。
- OTel Collector 已在五个节点安装并可使用 RustFS S3 配置。
- 拟承载 Doris FE 的节点具备 `JAVA_HOME17`。

任何节点的 Worker 或 OTel Collector 初始化未通过，均不得导入阶段 A 的服务 DAG。

## 9. Phase 9：通过前端导入阶段 A 服务 DAG

只有前端显示五节点 Worker 与 OTel Collector 集群初始化均通过后，才可从前端导入服务 DAG。每批导入前单独记录角色表、参数脱敏 hash、DAG ID、开始/结束时间与验收证据；失败即停止当前批，不进入后续批。

1. **基础依赖批**：`ZOOKEEPER`、`VALKEY`、`ELASTICSEARCH`、`NACOS`。Nacos 使用 CLI 预建 MySQL DB/账号，但仍须按产品流程确认 schema 初始化。
2. **Doris 批**：`DORIS`。确认 3 FE、批准数量的 BE、网络优先级、目录与内存限制；从第一个 FE 初始化并让其余 FE 通过 `<master>:9010` 加入。
3. **网关批**：`APISIX` Standalone。仅在 Gate 1 所有现场验证通过、Nexus 元数据 hash 已核对后导入。

OTel Collector 不在本阶段重复导入或安装；它是 Phase 8 的每节点集群初始化交付物。

## 10. Phase 10：业务验收与故障演练

### 10.1 Doris

从 `9030` 连接，执行：

```sql
SHOW FRONTENDS;
SHOW BACKENDS;
```

创建带明确副本数的验收库和测试表，插入确定性数据，核对行数、聚合及校验结果；从不同 FE 重复查询。

### 10.2 其他服务

- RustFS / OTel：确认五个节点的 OTel Collector 能按集群初始化配置向预期 bucket 写入或读取实际流水线数据。
- APISIX：在无 etcd 条件下启动，最小路由返回 upstream 响应，`9091` 可抓取指标。
- Valkey：认证读写。
- ZooKeeper：quorum 健康。
- Elasticsearch：集群健康及索引读写。
- Nacos：注册、查询与持久化。

故障演练每次只能停止一个实例：非 Master FE、单个 BE 或已经批准的一个关键中间件实例。停止前必须单独 Gate；恢复健康后才可进行下一项。禁止删除磁盘、删除 FE 元数据、并发停止多个实例或强制重建。

## 11. Phase 11：证据归档与结论

Git 外归档应包括：分支/commit、盘点、拓扑批准、离线包 manifest、脱敏配置 hash、plan hash/clusterHash、apply 状态、APISIX 模板测试与 Nexus 元数据 hash、API/Worker 注册、服务 DAG、Doris SQL、APISIX 路由与故障演练。

归档前扫描真实密码、私钥、token、JDBC 凭据和未脱敏截图。最终结论只能是：`PASS`、`PASS WITH DEVIATIONS` 或 `FAIL`。

## 12. Phase 12：后续 Hadoop 扩展

阶段 B 单独规划并审批 HDFS、YARN、Hive、Spark3、Kyuubi、DS 的角色、磁盘、端口和容量。完成前不得将 Kyuubi 或 DS 计入阶段 A 的“无 Hadoop”验收。
