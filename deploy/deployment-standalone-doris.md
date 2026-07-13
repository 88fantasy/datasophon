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
| 5 | CLI plan 生成与人工审批 | PASSED | 批准特定 plan hash 后才可 apply | plan hash / clusterHash（§7.2.4 最终批准，`25ad4ff34cff4283`；`os`/`config`/`soft` 经 §7.2.3 代码核查确认非真实阻塞项） |
| 6 | CLI apply 基础环境初始化 | BLOCKED | rustfs `.zip` 解压缺口修复决定 | apply 状态（§7.2.4：ping 误诊网络不通已纠正；§7.2.5：卡在 `init-tar`（离线环境无 tar）；§7.2.6：`init-tar` 代码修复（Codex 实现+Claude 审核）已现场验证通过，5 节点全部装上 tar，`apply` 越过该步；衍生新阻塞——`init-rustfs` 用 tar 解压 `.zip` 必然失败，是已知缺口首次真实暴露，待决定修复方式） |
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

### 7.2.2 重新 plan 现场执行记录（2026-07-13，clusterHash 已变更）

`nexus`/`mysql`/`rustfs` 三项离线 bundle 已确认来源并下载（版本从 Gate 5 记录时的 `nexus-3.85.0-03`/`mysql-8.0.28`/`rustfs-1.0.0.tar.gz` 升级为 `nexus-3.94.0-12`/`mysql-8.0.46`/`rustfs-v1.0.0-beta.8.zip`，详见 `package/manifest.json`）；`os`/`config`/`soft` 三项仍未就位——但见 §7.2.3：经代码核查，这三项在当前 `registry.enable=true` 的现场配置下并非真实阻塞项。

同时 `datasophon-cli-go` 完成一处适配：`packagesPath`（CLI 自身装 nexus/mysql/rustfs 等基础设施用的目录）改由 `productPackagesPath`（`-n`）推导为 `<productPackagesPath>/base`，不再依赖 `<datasophonPath>/datasophon-init/packages`；CLI 二进制已在 ddh-01 就地更新（旧版本备份为 `datasophon-cli.bak-20260712`）。

`cluster-sample.yml` 仅更新了 `packages:` 段的 6 行文件名以匹配上述新版本，节点拓扑、密码、`registry`/`mysql`/`ntpServer`/`rustfs` 的 node 归属均未改动（旧版本备份为 `cluster-sample.yml.bak-<时间戳>`）。由于 `packages:` 是 `ClusterConfig` 的一部分，此编辑使 `clusterHash` 必然变化。

```bash
DDH_HOME=/data/datasophon /data/datasophon/datasophon-cli create cluster plan \
  -t hadoop \
  -p /data/datasophon \
  --installPath /data \
  -n /data/install_datasophon/package
```

> `-n` 从 §7.2.1 记录的 `/data/datasophon/datasophon-init/product-packages`（当时为空目录）改为 `/data/install_datasophon/package`（真实落有 `raw/{meta,packages}` 与 `base/` 内容的目录），与新的 `packagesPath` 推导逻辑对齐。

plan 重新生成，仍是 34 个 Step（24 pending + 10 skipped，0 completed），targets 分布与 §7.2.1 记录完全一致（`init-rustfs`/`init-registry`/`init-registry-upload`/`init-nmap`/`init-ntp-server`/`init-mysql`/`init-mysql-app-db` 均只落在 `ddh-01`；同样 10 项 `skipped`；plan 中仍不包含 Doris/Worker/OTel Collector 或任何阶段 A 服务安装 Step）。审阅结论：

- `action=initALL`，`clusterHash=e91966fea9741bd2`（**已变更，取代 §7.2.1 的 `a85f980f11de2abc`，旧值即日起失效**）。
- plan 文件 SHA-256：`02f7564e26729024a14817e36167955ced06b8dfced19720c33620aa6074561f`。
- 配置文件 SHA-256：`0466e7cfedd678c1616034e4f8e635de67e716bbdbf6a0060ae49569c044cd9a`。
- 旧 plan 文件与旧配置文件均已在 ddh-01 就地备份（`.bak-<时间戳>`），未删除。

**Gate 5 结论：仍待人工批准，尚未执行 `apply`。** 上述新 `clusterHash + plan SHA-256` 需重新走一遍人工批准。`os`/`config`/`soft` 三项 CLI 基础设施 bundle 仍未就位，但见 §7.2.3 的代码核查结论——在当前配置下这不会导致 `apply` 失败，不再作为批准前必须解决的前置项。

### 7.2.3 os / config / soft 代码核查修正（2026-07-13）

重新审视 §3、§7.2.1、§7.2.2 中反复提到的"`os`/`config`/`soft` 三项 CLI 基础设施 bundle 未就位"这一结论——全仓库 grep `datasophon-cli-go` 源码后发现，这三个字段与 `nexus`/`mysql`/`rustfs` 的运行期依赖强度并不相同：

- **`os`**（`packages.os`，如 `openEuler-22.03-LTS-SP3.tar.gz`）：代码中没有任何地方直接读取该字段值本身。它概念上对应 `internal/cli/init/offline_server.go` 的 `InitOfflineServer`（`init-offline-server` Step）期望的 `<packagesPath>/os/<arch>/<osType>/` 目录——一份**已展开好的完整 yum/apt 离线仓库**（rpm/deb + repodata），代码里也没有任何逻辑把该 tarball 解压到这个目录，只能靠人工预先摆放。更关键的是 `InitOfflineServer.doRun()` 开头即判断：
  ```go
  if t.EnableRegistry {
      slog.Info("enableRegistry=true，offlineServer 不需要", ...)
      return nil
  }
  ```
  这条路径**只在不启用 Nexus（`registry.enable=false`）时才会真正执行**，是"不用 Nexus、改走本机 httpd/apache2 当离线源"的备用方案。现场配置固定 `registry.enable: true`，§7.2.2 重新 plan 的结果里 `init-offline-server` 已是 `⊘ skipped`——**该文件对本次部署路径没有任何实际影响**。
- **`config`（`config.tar.gz`）/ `soft`（`packages.tar.gz`）**：全仓库搜索不到任何代码读取这两个字段，也搜不到任何地方引用 `<packagesPath>/config/` 或 `<packagesPath>/soft/` 目录。这是 Go 版 CLI 里**完全未接线的死字段**——只存在于 YAML schema（`internal/config/global.go:130-131`），没有任何 Step/Handler 消费。仓库内已无旧 Java CLI 可对照原始意图，推断是重写时把 Java 版 config schema 照搬过来但未移植对应安装逻辑（或本应废弃未清理）。

**修正结论**：在 `registry.enable=true` 的现场配置下，`os`/`config`/`soft` 三个文件即使始终缺失，也不会导致任何已知 Step 在 `apply` 时报错——不同于 `nexus`/`mysql`/`rustfs`（缺失会在 `buildRegistry`/`buildMysql`/`buildRustfs` 对应 Step 里因文件不存在直接失败）。Gate 5 的实际前置条件收窄为：**只需人工批准 §7.2.2 记录的新 `clusterHash=e91966fea9741bd2`**，不必再等待 `os`/`config`/`soft` 到位。若后续切换为 `registry.enable=false`（弃用 Nexus 改走离线 httpd 源），需重新评估 `os` 字段的必要性。

### 7.2.4 Gate 5 批准与首次 apply 尝试（2026-07-13）

用户在 ddh-01 的 `cluster-sample.yml` 中手动为五个节点补充了 SSH 密码（`nodes[].password`，原为空字符串，`sshAuthType: AUTO` 场景下用于密码回退认证），导致 `clusterHash` 相应变化。就地重新 `plan` 后确认新状态：

- `action=initALL`，`clusterHash=25ad4ff34cff4283`（取代 §7.2.2 的 `e91966fea9741bd2`）。
- `initALL.plan.json` 34 Step，0 completed，与前两次记录的 targets 分布一致。
- 用户明确批准：**Gate 5 PASSED**。

**首次 `apply` 尝试（16:10:32 启动，经 `nohup` 后台执行避免 SSH 断连影响）**：在第一个 Step `init-bin-package`（分发 `datasophon-init` 资源包到 `ddh-02～05`）即失败：

```
Error: step init-bin-package: SSH 连接失败 192.168.10.132: dial tcp 192.168.10.132:22: i/o timeout
```

现场排查（非 CLI/配置问题，已逐层定位到网络基础设施层）：

| 检查项 | 结果 |
| --- | --- |
| ddh-02 本身是否存活 | **存活**——从会话执行环境直连 `ssh root@192.168.10.132` 成功，`uptime` 137 天 |
| ddh-01 → ddh-02/03/04/05 | **全部 100% 丢包**（`ping` 逐个测试），不止 ddh-02 一台 |
| ddh-01 → 网关（192.168.10.1） | **正常**，0% 丢包，延迟 0.2ms |
| ddh-01 本机 firewalld | `inactive` |
| ddh-01 本机 iptables | 三条链策略均为 `ACCEPT`，规则为空 |
| ddh-01 本机 SELinux | `Disabled` |

**结论**：ddh-01 能到网关（南北向）但到同网段其余 4 台节点（东西向）全部不通，本机防火墙/SELinux 均已确认关闭且规则为空，可排除节点内部配置问题。症状符合**云平台安全组/网络 ACL 只放通了到网关方向、未放通同子网实例间互访**这一常见模式（本环境路由表显示 `169.254.169.254 via 192.168.10.3` 等 DHCP 元数据路由，符合 OpenStack/云平台网络特征）。这需要有云平台/网络管理权限的人核实并放通 `192.168.10.0/24` 网段内 ddh-01↔ddh-02/03/04/05 的东西向访问（至少 `22/TCP`，后续还会用到 §6 端口清单中的 MySQL `3306`、Nexus `8081`/`8083`、RustFS `9040`/`9041` 等）。

**apply 进程状态**：已自行退出（未挂起、无残留进程），`initALL.plan.json` 仍是 `0/34 completed` 的干净状态。网络打通后**可直接重跑 `apply`，无需重新 `plan`**（`clusterHash=25ad4ff34cff4283` 仍然有效）。

```bash
DDH_HOME=/data/datasophon /data/datasophon/datasophon-cli create cluster apply \
  -t hadoop -p /data/datasophon --installPath /data -n /data/install_datasophon/package
```

**误诊纠正（同日复查）**：上述"100% 丢包"结论仅来自 `ping`（ICMP）。用户确认现场服务器**禁用了 ICMP**，但 TCP 业务端口是放通的。改用 TCP 三次握手直接探测后，`ddh-01 → ddh-02/03/04/05` 的 `22/TCP` 全部 `OK`：

```bash
ssh root@192.168.10.131 "for ip in 192.168.10.132 192.168.10.133 192.168.10.134 192.168.10.135; do \
  timeout 3 bash -c \"echo > /dev/tcp/\$ip/22\" && echo \$ip OK || echo \$ip FAIL; done"
# 192.168.10.132: OK / .133: OK / .134: OK / .135: OK
```

即东西向网络实际是通的，§7.2.4 前段"云安全组未放通东西向流量"的结论为误判——正确结论应为"ICMP 被禁用，与 TCP 连通性无关"。**网络侧无需任何人介入，`apply` 可直接重跑**，之前的 Step 失败是首次 `ping` 排障方式选错导致的错误诊断，不代表 apply 本身还会在网络层再次失败（真实原因仍待重跑观察，若 22/TCP 通但 apply 仍失败，需回到 CLI 侧日志排查）。

### 7.2.5 网络误诊纠正后的第二次 apply 尝试（2026-07-13）

后台重跑 apply（`nohup`，PID 206907），验证结果：**网络确实已通**——`init-bash`（shell bash 设置）在 ddh-02/03/04/05 上全部执行成功，证实 §7.2.4 的误诊纠正是对的，东西向 SSH 通道没有问题。

紧接着在 `init-tar`（安装 tar）这一步失败：

```
Error: step init-tar: tar 命令不存在，请手动安装
```

**代码侧确认**（`datasophon-cli-go/internal/cli/init/tar.go`）：`InitTar.doRun` 只执行 `which tar` 做存在性检查，**没有任何安装逻辑**——保留着原 Java 版本的注释 `// TODO 默认已安装 tar，废弃，在线安装`，即这一步的设计假设是"节点镜像默认自带 tar，此处只是兜底确认"，从 Java 版本迁移以来就没变过。且它在 `registry.go` 的 `InitALLRegistry` 中排在 `init-offline-server`/`init-offline-nodes`（yum/apt 离线源配置，第 16/17 步）**之前**、无 `Condition`（对所有场景总是执行）。

现场原因：当前是纯离线环境，用户尚未上传离线源文件到 ddh-01，ddh-02～05 的镜像本身未预装 `tar`。**需要注意**：即使离线源上传并配置完成，由于 `init-tar` 排在离线源配置步骤之前且自身不具备安装能力，apply 重跑仍会先卡在这一步——必须在离线源就位后**手动**确认/安装好 ddh-02～05 上的 `tar`，再重跑 apply，不能指望 apply 顺序本身补上这个缺口。这是 Java→Go 迁移带来的既有假设边界首次在精简/定制镜像上暴露，不是本次改动引入的新问题，是否调整 Step 顺序或补充自动安装逻辑留待用户决定。

**apply 进程状态**：已自行退出，`initALL.plan.json` 状态在此次重跑前为 `clusterHash=25ad4ff34cff4283`、23 pending + 10 skipped + 1 failed（`init-tar`），重跑后 `init-bash` 转为 completed、`init-tar` 仍为 failed。等待用户上传离线源并在节点上补齐 `tar` 后，直接重跑 §7.2.4 的 apply 命令即可续跑，无需重新 `plan`。

### 7.2.6 init-tar 代码修复与验证（2026-07-13）

用户把离线源（openEuler-22.03-LTS-SP3 全量 yum 仓库，含 `repodata/` 与
`tar-1.34-5.oe2203sp3.x86_64.rpm`）手动上传到
`/data/install_datasophon/package/yum/x86_64/openEuler-22.03-LTS-SP3/`，据此走了一次完整的
「Claude 出计划 → Codex 实现 → Claude 审核 + 现场验证」流程（实施清单：
`docs/cli-init-tar-离线安装-实施清单-2026-07-13.md`）：

- `InitTar.doRun`（`datasophon-cli-go/internal/cli/init/tar.go`）由纯检查改为三分支：已存在直接
  跳过；缺失且未传 `--productPackagesPath` 保持旧报错（向后兼容独立 `init tar` 命令）；缺失且有
  路径则从 `<ProductPkgsPath>/yum/<arch>/<os>/` glob 找 rpm/deb，`SendFile` 分发到目标节点后
  `rpm -ivh`/`dpkg -i` 直接安装本地包（不走 yum/apt 源，因为 `init-tar` 排在离线源配置步骤之前）。
- `buildTar`（`internal/plan/builders_common.go`）目标节点从 `workerHostSlice`（排除本地节点）
  改为全部节点——因为 `init-registry` 也要在 ddh-01 上 `tar xzf` 解压 Nexus，ddh-01 同样需要 tar。
- 代码审查：`go build`/`go vet`/`go test -count=1 ./...` 全绿，新增 4 个单测覆盖三分支 + 目标节点
  含本地节点，且顺带修正了一处过时的旧文档描述（`docs/commands/init/packages/tar.md` 曾写"从
  `--packagePath` 下载安装"，但原代码从未实现这个能力）。
- **现场验证**：`make release` 交叉编译新二进制部署到 ddh-01（备份为
  `datasophon-cli.bak-20260713-tarfix`），重新 `plan` 确认 `clusterHash` 不变、`init-tar` targets
  从 4 变 5（新增 ddh-01）；后台重跑 `apply`，日志证实 5 个节点全部走通「探测缺失 → 分发 rpm →
  `rpm -ivh` → 二次校验成功」，耗时 7.3 秒，**两天的死锁彻底解开**，`apply` 顺利越过 `init-tar`
  进入 `init-rustfs`。

**衍生新阻塞**（与本次修复无关，是全新暴露的问题）：`init-rustfs` 在 ddh-01 上失败，实际执行的
命令是 `tar xvz -f rustfs-linux-x86_64-musl-v1.0.0-beta.8.zip -C /data`——用 `tar` 解压 `.zip`
文件，格式不匹配必然失败。这正是 §2.1（交接文档 2026-07-13）记录的**已知缺口**：RustFS 官方无
GA 1.0.0，只有 beta `.zip`，`rustfs.go` 解压逻辑硬编码 `tar xvz` 不认 `.zip`，当时已确认"另开
任务改 rustfs.go"。之前两次 apply 都卡在更早的步骤，这个 bug 一直没被真实执行路径触达；今天
`init-tar` 死锁解开后，它才第一次在真实 apply 中暴露。`apply` 进程已自行退出，plan 状态
3 completed（`init-bin-package`/`init-bash`/`init-tar`）+ 1 failed（`init-rustfs`）+ 20 pending +
10 skipped。是否现在修 `rustfs.go`、还是先手动 unzip 让 apply 继续、待用户决定。

### 7.2.7 rustfs 解压/密码转义/Nexus IP/静默失败/EULA/bcprov/yum 索引 —— 连续 8 层修复（2026-07-13～14）

紧接 §7.2.6 的 `init-rustfs` 阻塞，同一会话内以「发现问题 → 手动验证根因 → 确认方案 → 改代码 →
`go build/vet/test` → `make release` → 部署 → 重跑 apply 验证」的节奏，连续解开 8 层问题，`apply`
从卡在 `init-rustfs` 一路推进到 `init-library`（第 16 步）真正开始装依赖库。逐层记录：

**① rustfs 用 `tar` 解压 `.zip`**：`internal/plan/builders_cluster.go` `rustfsTask.doRun` 原来
`tar xvz -f xxx.zip` 解压 zip 包，格式不匹配必然失败。改成 `unzip -o`；同时发现 zip 包内部结构
（`unzip -l` 核实）跟 tar.gz 发布物不同——**根目录直接是裸 `rustfs` 二进制，没有版本号顶层目录**，
原代码 `mv %s/rustfs-* %s` 这一行的前提对 zip 不成立，改成先 `mkdir home` 再 `unzip -o zip -d home`
直接产出 `home/rustfs`，去掉不再需要的 `mv`。

**② rustfs 密码含 `#` 被 shell 当注释截断**：`start()` 生成的 `start.sh` 里
`--secret-key #t46$q7_QT1L ... > logs/rustfs.log 2>&1 &` 一整行从 `#` 开始被 bash 当注释吃掉，
密码参数悬空、重定向和后台 `&` 全部丢失，进程同步阻塞且不产生日志。新增 `shellQuote` helper（单
引号转义），用户名/密码统一转义；同时按用户要求把 `mkdir -p data`/`mkdir -p logs` 从 Go 代码里的
`ExecShell` 调用移进 `start.sh` 脚本本体，脚本自检测自创建，更幂等。

**③ 所有访问 Nexus 的地方用 hostname，早于 `/etc/hosts`（第 20 步）生效**：`applyRegistry`
helper（`internal/plan/helpers.go`）新增 `resolveIP` 函数，`RegistryIP` 一律解析成配置里记录的
真实 IP、查不到才退回 hostname 兜底；13 处调用点补上 `ctx.GlobalNodes` 参数。另有 4 处**没有走
`applyRegistry`、直接手写 `xxx.Node` 赋值**的遗漏点单独修：`buildRegistry`/`buildRegistryUpload`
的 `WebHost`、`buildOfflineNodes` 的 `ServerIP`（YumServer 和 Registry 两条分支）、`buildMysql` 的
`RegistryIP`。`rustfsTask.WebHost` 同样从 `rs.Nodes[0]`（hostname）改成 `node.IP`。

**④ 上传/改密码/EULA 失败被静默吞掉，plan 状态被误标记 `completed`**：`upload.UploadRegistry`
上传 2735 个文件全部失败（`success=0 fail=2735`）但 `init-registry-upload` 仍记录"步骤完成"；
`registryTask.doRun` 里 `changePassword`/`systemEula` 的 `bool` 返回值被忽略，失败也照样往下走、
最终打"nexus 安装成功"返回 `nil`。两处都补上返回值检查，失败即 `return errors.New(...)`，让断点
续跑机制能正确识别失败并在下次 apply 时真正重试，而不是心照不宣地假装成功。

**⑤ Nexus 密码验证**：`init-registry` 用旧代码（hostname 未解析）跑过一次，`changePassword`
静默失败，Nexus 真实密码从未变成配置文件里那个值——用 `admin.password` 里的临时密码认证
`GET /service/rest/v1/status` 返回 200 实测坐实。清空 plan.json 里 `init-registry` 的 `completed`
状态重跑，这次日志显示 `修改密码成功`，问题解除。

**⑥ EULA disclaimer 硬编码文本 Unicode 引号不匹配**：`nexus.log` 报
`java.lang.IllegalArgumentException: Invalid EULA disclaimer`——代码里硬编码的 `nexusEula`
常量用普通 ASCII 单引号，Nexus 3.94.0-12 服务端存储的原文用的是 Unicode 弯引号（`'`/`'`），逐
字节比较不相等。修复思路不是把常量改成弯引号（换个位置的同一个坑），而是**`systemEula` 先
`GET /service/rest/v1/system/eula` 拿服务端自己存的原文，再原样回传**，删除硬编码常量，新增
`nexusHTTPGet` helper，彻底消除未来文本漂移的可能。

**⑦ `bcprov-jdk15on-1.68.jar` 未纳入产品包清单**：JDK8 安装流程里这个 BouncyCastle jar（用于放宽
TLS1.0/1.1 算法限制）从未出现在 `package/manifest.json`、本地、现场任何地方，下载 404。用户决定
暂不补充这个包，改代码让下载失败仅警告、跳过 `cp` 和 TLS `sed` 配置（两者绑定在一起，避免
`java.security` 禁用列表被改动但缺少对应算法实现），继续完成 JDK8 核心安装。JDK21 无此依赖，
不受影响。

**⑧ yum/apt 上传遍历遇子目录直接 `continue`，`repodata/` 从未上传**：`internal/cli/upload/
registry.go` `repositoryUploadBatch` 的 `yum`/`apt` 分支只读 `<arch>/<os>/` 一层，`f.IsDir()` 为
真就 `continue` 跳过——`repodata/` 恰好是这样一个子目录，`repomd.xml`/`primary.xml.gz` 等索引
文件从未被遍历、从未上传，导致"2735 个文件全部上传成功"但 Nexus 里的 yum 仓库其实没有索引层，
`yum makecache` 报 `Cannot download repomd.xml: All mirrors were tried`。`raw` 分支本来就用
`filepath.Walk` 递归遍历不受影响，这也是为何只有 yum 类型缺索引。改成同样用 `filepath.Walk`
递归遍历 `osDir`，`directory` 字段按相对路径追加 `baseDir + "/" + relDir`，保留 `repodata` 在
Nexus 里的子路径结构。

**现场验证结果**：`init-rustfs`→`init-registry`→`init-registry-upload`（2735 文件真实上传成功，
含 3.5GB 的 Doris 安装包，耗时约 7 分钟）→`init-jdk8`（bcprov 降级生效，4 节点全部只警告不阻塞）
→`init-jdk21`→`init-hadoopuser`→`init-firewall`→`init-selinux`→`init-swap`→`init-offline-nodes`
（`yum makecache` 真正成功）→`init-library`（开始真实执行 `yum install` 装依赖库）全部走通。
每次代码改动都遵循：`go build && go vet && go test -count=1 ./...` 全绿 → `make release` →
scp 部署（md5 核对）→ 现场 apply/手动验证 → 确认后再进下一层。

**遗留**（不阻塞当前 apply，需后续单独跟进）：
- `docker`/`helm` 类型仓库创建返回 400（`must not be null` 参数校验错误），`repoCreateByList`
  内部忽略了这个失败继续走，当前环境用不到这两种仓库类型，暂未处理。
- `bcprov-jdk15on-1.68.jar` 仍未补进 `package/manifest.json`，TLS1.0/1.1 算法放宽长期缺失。

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
