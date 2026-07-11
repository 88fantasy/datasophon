# 五节点无 Hadoop Doris 集群部署与验收手册

> 范围：使用 `datasophon-cli-go` 初始化五台虚拟机并创建 DataSophon 控制面基础设施；随后从 DataSophon 前端创建集群、完成 Worker 与 OTel Collector 集群初始化，最后通过前端导入 DAG 安装剩余阶段 A 组件。本文不执行任何生产变更，所有现场配置、plan 和证据均保存在 Git 外。
>
> 凭据规则：本文只使用 `<ROOT_PASSWORD>`、`<MYSQL_PASSWORD>`、`<RUSTFS_ACCESS_KEY>` 等占位符。不得把真实密码、私钥、token、JDBC 凭据或未经脱敏的截图写入仓库、终端录屏或验收报告。

## 1. 目标、边界与阶段

### 1.1 节点清单

| Hostname | IP | SSH 用户 | 阶段 A 初始用途 |
| --- | --- | --- | --- |
| `ddh-01` | `192.168.10.131` | `root` | 管理面候选、Worker、Doris FE/BE 候选 |
| `ddh-02` | `192.168.10.132` | `root` | Worker、Doris FE/BE 候选 |
| `ddh-03` | `192.168.10.133` | `root` | Worker、Doris FE/BE 候选 |
| `ddh-04` | `192.168.10.134` | `root` | Worker、Doris BE 候选 |
| `ddh-05` | `192.168.10.135` | `root` | Worker、Doris BE 候选 |

> 这是候选拓扑，不是资源承诺。CPU、内存、磁盘、架构、操作系统、端口占用和既有数据未经 Phase 0 盘点前，不得固定角色或数据目录。

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
| 0 | 五节点只读盘点 | NOT STARTED | 资源、磁盘、端口、数据保护与变更许可 | Git 外盘点包 |
| 1 | APISIX Standalone 产品适配 | PASSED（自动化） | 真实无 etcd 启动与路由转发仍待现场验证 | 模板单测、后续 DAG 证据 |
| 2 | 冻结拓扑、容量与服务角色 | BLOCKED | 管理面、FE/BE、中间件角色与资源预算 | 拓扑审批单 |
| 3 | 网络、时间、磁盘与离线包预检 | BLOCKED | 数据盘、JDK17、架构与包校验 | manifest |
| 4 | CLI 配置生成与五节点审阅 | BLOCKED | 严格解析、引用、权限、敏感信息检查 | 脱敏配置 hash |
| 5 | CLI plan 生成与人工审批 | BLOCKED | 批准特定 plan hash 后才可 apply | plan hash / clusterHash |
| 6 | CLI apply 基础环境初始化 | BLOCKED | 失败续跑或重新 plan 的决定 | apply 状态 |
| 7 | 基础环境、RustFS 与 API 健康 | BLOCKED | 批准从前端创建集群 | 连接与健康检查 |
| 8 | 前端集群初始化：Worker 与 OTel Collector | BLOCKED | 五个节点检查均通过后才可导入服务 DAG | 初始化记录、注册与心跳证据 |
| 9 | 前端导入阶段 A 服务 DAG | BLOCKED | 每批角色和参数审批 | DAG 记录 |
| 10 | 阶段 A 业务与故障演练 | BLOCKED | 每次停止实例前单独审批 | SQL / 健康 / 演练报告 |
| 11 | 阶段 A 证据归档与结论 | BLOCKED | PASS / 偏差 / FAIL 审核 | 脱敏归档包 |
| 12 | 阶段 B Hadoop 扩展 | BLOCKED | 单独立项 | 后续计划 |

状态只能取 `NOT STARTED`、`IN PROGRESS`、`BLOCKED`、`PASSED`、`FAILED`、`ROLLED BACK`。

## 3. Phase 0：五节点只读盘点

每台节点以只读方式执行以下命令并归档到 Git 外目录。此阶段禁止设置 hostname、安装软件、创建或清空目录、挂盘、修改防火墙或内核参数。

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
- OS、CPU 架构、CPU/内存、swap、数据盘、inode 与 `/data` 既有数据保护要求。
- 已监听端口及已有 MySQL、Nexus、RustFS、Doris、DataSophon 进程不会冲突。
- 时区、NTP 状态和跨节点时间偏差可接受。
- Doris FE 所需的 JDK17 与 `JAVA_HOME17` 可用性；平台自身使用 JDK21。
- 现场拥有对应架构的 CLI、API、Worker、服务安装包和元数据。

任何未确认项均使后续 Phase 保持 `BLOCKED`。

## 4. Phase 1：APISIX Standalone 适配

APISIX 使用 `3.16.0` 的 Standalone 数据面模式，不依赖 etcd：

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

资源足够时可采用以下基线：`ddh-01`～`ddh-03` 运行 Doris FE+BE，五台均运行 BE，形成 `3 FE + 5 BE`。FE 数量必须是奇数；第一个 FE 是初始 Master。

Gate 2 必须冻结：

- 管理面是否可以与 Doris 共置；不足时将 `ddh-01` 设为管理面专用，并重新决定 BE 数量。
- 每项阶段 A 服务的节点、依赖、故障域与端口矩阵。
- Doris `fe_priority_networks`、`be_priority_networks`、FE `meta_dir` 与堆、BE `storage_root_path` 与 `mem_limit`。
- RustFS 数据目录、容量、访问凭据与 Doris BE 数据目录的隔离预算。
- OTel Collector RustFS endpoint、bucket 和凭据映射。
- APISIX 最小路由实际 upstream 的地址与端口。

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

## 7. Phase 4～6：CLI 配置、plan 与 apply

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
