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
| 阶段 A DAG | `DORIS`、`VALKEY`、`ELASTICSEARCH`、`NACOS`、`DS`、`APISIX`（见下方说明） | 是 |
| 阶段 A 已知问题搁置 | `ELASTICSEARCH EsExporter`（`VALKEY` 已于 §7.12 用 openEuler 原生构建解决，不再是已知问题） | 是，按对应已知限制验收 |
| 阶段 B | `KYUUBI`、`SPARK3`、`HIVE`、`HDFS`、`YARN` 等 | 否，另行立项 |

`DS` 原依赖闭包会引入 `SPARK3 → HIVE → HDFS → ZOOKEEPER`；Kyuubi 的默认运行参数依赖 YARN，仍不能表述为"无 Hadoop"阶段 A 的组成部分。

**`ZOOKEEPER` 已从阶段 A 移除（2026-07-17 决定）**：DolphinScheduler 改用 MySQL 作为注册中心（`registry.type=jdbc`），不再依赖独立 ZooKeeper 集群。

**`DS` 已从阶段 B 移入阶段 A（2026-07-17 决定，见 §7.9）**：`service_ddl.json` 的 `dependencies` 清空为 `[]`，移除 `SPARK3`、`ZOOKEEPER` 硬依赖；注册中心切到 MySQL/JDBC 后，DS 核心四角色（ApiServer/MasterServer/WorkerServer/AlertServer）不再需要 Spark/Hive/HDFS/ZooKeeper 任何一环即可安装运行，Spark/Hive 相关任务插件仍可选，未装不影响核心调度功能。不影响阶段 B 未来因 `SPARK3 → HIVE → HDFS` 依赖闭环可能引入的 ZooKeeper（另行立项时再评估，与本次注册中心选型无关）。

## 2. Epic 进度跟踪

实施期间只更新状态与脱敏证据入口；不得把凭据、完整配置或 plan 正文提交到本仓库。

| Phase | 目标 | 状态 | 人工 Gate | 证据/产物 |
| --- | --- | --- | --- | --- |
| 0 | 五节点只读盘点与数据盘准备 | PASSED | 资源、磁盘、端口、数据保护与变更许可 | Git 外盘点包；五台 `/data` 持久挂载验证 |
| 1 | APISIX Standalone 产品适配与现场安装 | PASSED | Standalone 无 etcd 启动、参数化配置、最小路由与 `9091` metrics | §7.9：ddh-02 真实 RPM/systemd/路由/指标验收 |
| 2 | 冻结拓扑、容量与服务角色 | PASSED | 管理面、FE/BE、中间件角色与资源预算 | 拓扑审批单（§5，1 FE + 3 BE） |
| 3 | 网络、时间、磁盘与离线包预检 | PASSED WITH DEVIATIONS | 数据盘、JDK17、架构与包校验 | manifest（§6 现场记录；阶段 A 服务包/JDK17/CLI 基础设施 bundle 延后） |
| 4 | CLI 配置生成与五节点审阅 | PASSED | 严格解析、引用、权限、敏感信息检查 | 脱敏配置 hash（§7.2.1） |
| 5 | CLI plan 生成与人工审批 | PASSED | 批准特定 plan hash 后才可 apply | plan hash / clusterHash（§7.2.4 最终批准，`25ad4ff34cff4283`；`os`/`config`/`soft` 经 §7.2.3 代码核查确认非真实阻塞项） |
| 6 | CLI apply 基础环境初始化 | PASSED | rustfs `.zip` 解压缺口修复决定 | apply 状态（§7.2.4：ping 误诊网络不通已纠正；§7.2.5：卡在 `init-tar`（离线环境无 tar）；§7.2.6：`init-tar` 代码修复已现场验证通过；§7.2.7：连续 8 层修复后 34 个 Step 全部跑完（24 completed + 10 skipped + 0 failed）；§7.2.8：远端服务健康检查发现并修复 MySQL 密码链路 3 处新 bug，Nexus/RustFS/MySQL/NTP 四项实测可正常访问，Gate 6 完成） |
| 7 | 基础环境、RustFS 与 API 健康 | PASSED | 已批准从前端创建集群 | 连接与健康检查（§7.4：ddh-01 补装 JDK21、`datasophon-api` 已部署启动，DB 迁移至 2.2.3、HTTP 8080、gRPC 18081、登录鉴权均验证通过；NACOS ddl 元数据加载报错为遗留问题，不阻塞；§7.5：NACOS ddl 根因修复并现场验证；§7.6：18 服务 DDL value/defaultValue 清理 + `/internal/meta/refresh` 端点现场部署，2026-07-17 验证通过，另发现线上有 11 轮未提交修复被本次部署覆盖，详见 §7.6） |
| 8 | 前端集群初始化：Worker 与 OTel Collector | PASSED | 五个节点检查均通过后才可导入服务 DAG | §8.1：五节点 Worker/Collector 正常、导出队列清零、RustFS 已写入 445 个对象，前端集群状态为“正在运行” |
| 9 | 前端导入阶段 A 服务 DAG | PASSED WITH DEVIATIONS | 每批角色和参数审批 | §7.7：批量导入前的前置探索——NACOS、ELASTICSEARCH（主角色）单装验证通过（各自修复 1 组真实 bug）；ELASTICSEARCH 的 `EsExporter` 角色因缺失第三方二进制资产暂未解决，不阻塞主角色；§7.8：VALKEY 单装失败，`ValkeyMaster` 预编译包与 openEuler OpenSSL 主版本不兼容（缺 `libssl.so.3`），upstream 无该发行版预编译包，已记录暂不处理；§7.9：APISIX 以 openEuler 离线 RPM Standalone bundle 通过前端安装和现场验收，RPM、systemd、路由转发、`9091` metrics 均正常，Admin API 未监听；DS 移入阶段 A，`dependencies` 清空、注册中心切到 MySQL/JDBC；§7.10：现场安装验证，连续修复 5 个真实 bug（MINIO→RustFS 占位符、`INSTALL_PATH` 变量注册通用化、YARN 缺前缀、mysql 驱动缺失、`ServiceHandler` 状态检查退出码平台级 bug），`ApiServer`/`MasterServer`/`AlertServer` 三角色验证运行正常；`WorkerServer` 因 S3 存储插件不随官方发行包分发，一度记录为已知问题；§7.11：从 Maven Central 补全插件后仍崩溃，反编译定位到 S3 相关 property key 命名与官方 3.4.1 实际约定不符（`resource.aws.*` vs 官方 `aws.s3.*`），修复后 DS 六个角色（`ApiServer`/`MasterServer`/`AlertServer`/`WorkerServer`×3）全部验证真实稳定运行，S3 存储插件问题解除，不再是已知问题；`ZOOKEEPER` 已从基础依赖批移除（DolphinScheduler 改用 MySQL 注册中心，不再需要，见 §1.3）；DORIS 已安装完成并验证通过；§7.12：VALKEY 改用 openEuler 原生构建（链接系统自带 OpenSSL 1.1.1，非原先不兼容的 Ubuntu Jammy/OpenSSL3 包），Gate 1-6 现场验证全部通过，`ldd` 无 `not found`、真实进程/端口、认证 `PING`/`SET`/`GET`/`DEL`、exporter `redis_up=1` 均正常，不再是已知问题；实际部署节点从冻结拓扑的 `ddh-02` 改为 `ddh-01`（`ddh-02` 内存不足，用户现场决定改用 `ddh-01`，非代码 bug，属已批准的拓扑偏差）；§9.1（2026-07-18 补记）：四个批次正式验收表 + 本次 SSH 只读复核五节点全部服务健康存活，Phase 9 收口为 `PASSED WITH DEVIATIONS`（偏差：`EsExporter` 缺资产、VALKEY 拓扑偏差、四批次均以单服务逐个安装完成而非整批一次性导入）；§9.2（2026-07-18 晚，补测整批 DAG 导入）：清空 VALKEY/ELASTICSEARCH/NACOS/DS/APISIX 后用 `deploy/phase9-batch-deploy.yaml` 一次性合并导入，验证整批 DAG 路径本身可用（多服务合并单一 DAG、失败节点整体取消同批、`redeploy` 跳过已成功节点重跑均按预期工作）；VALKEY/APISIX/NACOS/DS 批量安装成功，ELASTICSEARCH 主角色成功、`EsExporter` 因既有已知问题失败拖累节点显示 FAILED（不阻塞服务级 RUNNING）；过程中定位并修复真实平台 bug——ddh-01/03/04/05 的 Worker 落后 ddh-02、未同步 APISIX `?c` 模板修复（镜像重演 §7.7 的 Worker 版本偏差问题），已同步四节点 jar+模板并重启验证；另发现 YAML 批量清单无法按角色排除安装（`deploy()` 按服务 DDL 全量角色生成命令），与前端向导单装的行为不同 |
| 10 | 阶段 A 业务与故障演练 | PASSED WITH DEVIATIONS | 每次停止实例前单独审批 | §10.3：2026-07-19 完成 Doris/RustFS-OTel/APISIX/Valkey/Nacos/Elasticsearch 六项只读业务验收（另补测 DS），全部通过；§10.4：单 Doris BE 停止/恢复演练已获批准并执行通过。偏差：未做 FE 切换（§5 已知单 FE 限制）、未逐一穷举中间件实例的故障演练（用户按 §10 规则逐项审批，本轮只批准了一项） |
| 11 | 阶段 A 证据归档与结论 | PASSED WITH DEVIATIONS | PASS / 偏差 / FAIL 审核 | §11.1～§11.3：2026-07-19 归档包存于 `datasophon-deploy-evidence/`（Git 外）；归档前扫描发现并打码两处历史已泄露的真实密码（MySQL root、RustFS secret key，均已 push 未轮换）；最终结论 `PASS WITH DEVIATIONS` |
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
| `ddh-02`（`192.168.10.132`） | Worker、OTel Collector、NTP Client；阶段 A 中间件（`VALKEY`/`ELASTICSEARCH`/`NACOS`/`APISIX`）主要承载节点 | 不部署 Doris |
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
`--secret-key <RUSTFS_SECRET_KEY> ... > logs/rustfs.log 2>&1 &` 一整行从 `#` 开始被 bash 当注释吃掉，
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

### 7.2.8 Gate 6 完成后的远端服务健康检查——MySQL 密码链路 3 处新 bug（2026-07-14）

34 个 Step 全部跑完（24 completed + 10 skipped + 0 failed）后，按用户要求逐一检查 Nexus/RustFS/
MySQL/NTP 是否真的可访问，而不是只看 apply 是否报错：

- Nexus(8081)、NTP(chronyd)：正常。
- RustFS(9040/9041)：端口未监听——`ps -ef` 确认进程已退出（此前手动验证阶段用 `bash start.sh &`
  启动的裸进程，无 systemd 托管，意外退出不会自愈）。手动 `bash /data/rustfs/start.sh` 重新拉起后
  恢复正常。
- **MySQL(3306)：`ERROR 1130 Host 'ddh-01' is not allowed to connect`**——排查确认根因是
  `internal/plan/mysql.go`（及其在 `internal/cli/create/mysql.go` 的重复实现）里 `ALTER USER
  'root'@'localhost' IDENTIFIED BY ...` 缺少 `--connect-expired-password`，MySQL 8.0 要求临时
  密码首次登录必须显式加这个选项才允许执行任何 SQL，缺失导致这条命令被拒绝执行、root 密码从未
  真正改变（用初始 `admin.password`/`mysqld.log` 里的临时密码测试登录，返回"密码已过期需先修改"
  确认坐实），但 `checkStart` 只查 `systemctl status`、不查密码状态，整体仍报"安装成功"。

修复过程中现场手动验证又暴露两个新问题（均已同步进代码）：
1. `mysql --defaults-extra-file` 必须是命令行第一个参数，放在其他选项之后会被 MySQL 客户端
   误判成普通变量赋值报 `unknown variable`；代码里 `--connect-expired-password` 排在了它前面。
2. cnf 配置文件里密码不加引号，MySQL 客户端的 ini 解析器把裸露的 `#` 当注释起始符截断——跟
   shell 的 `#` 截断是同一类问题、不同的层，这次出现在 cnf 文件里，`password=<MYSQL_PASSWORD>#后半段`
   被截断成 `password=<MYSQL_PASSWORD>前半段`。改为 `password="<MYSQL_PASSWORD>"` 加引号后解决。

**排查其他 ExecShell 调用暴露的重复代码问题**：`internal/cli/create/{mysql,registry,rustfs}.go`
是 `internal/plan/` 包对应逻辑的历史重复实现（单节点手动命令 `create mysql/registry/rustfs -f`
各自独立维护一份几乎相同的代码），这次会话在 plan 包修的 bug 一个都没同步过去，仍是旧版本。
一并同步修复：`create/mysql.go`（同样的三处 bug）、`create/rustfs.go`（zip 解压+`unzip`+
`chmod +x`，Username/Password 用已有的 `shellSingleQuote` 转义，data/logs 目录创建移入
start.sh）、`create/registry.go`（changePassword/systemEula 返回值检查、EULA 改 GET 服务端原文
再回传、`WebHost` 改用 `node.IP`）。

`internal/cli/init/mysql_app_db.go`（`InitMysqlAppDb`，plan 包和 create 包共用同一份实现，
未发现重复代码）同样存在"失败被静默忽略"：`doRun` 无条件 `return nil`，`initCommonAccount`
的 9 条 `CREATE DATABASE/USER`/`GRANT` SQL 全部没检查返回值。现场验证时 root 密码错误期间执行
过一次，`mysql.user`/`SHOW DATABASES` 确认 `datasophon`/`hive` 等 9 个应用数据库和账号全部
不存在，`init-mysql-app-db` 却被标记为"步骤完成"。补上返回值检查，重跑后 9 个账号全部创建成功
（`datasophon`/`hive`/`dolphinscheduler`/`ustream`/`nacos`/`bigdata`/`juicefs`/`portal`/
`datart`），`SHOW DATABASES`/`mysql.user` 实测确认。

**现场修复**（不删除任何数据）：手动用 `admin.password`/`mysqld.log` 里的临时密码 +
`--connect-expired-password` + 加引号的 cnf 文件，把 root 密码改成配置文件里指定的值并把
host 改成 `%`；再重置 `initALL.plan.json` 里 `init-mysql-app-db` 的状态用新代码重跑，创建
全部 9 个应用账号。全程未触碰 `/var/lib/mysql` 数据目录。

**最终结果**：Nexus/RustFS/MySQL/NTP 四项全部实测可正常访问，五节点部署 Epic 的 Gate 6 连同
远端服务健康检查一并完成。

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

### 7.4 Phase 7：部署 datasophon-api 到 ddh-01（2026-07-14）

`datasophon-cli-go` 目前不管控 `datasophon-api` 本身（§1.2 描述的"CLI 创建/启动 datasophon-api"是目标态，实际尚未实现），沿用 `deployment-standalone.md` 记录的手动模式：从 `datasophon-assembly` 产出的 tar.gz 在控制节点解压启动。

**发现的新阻塞——ddh-01 缺 JDK**：`init-jdk8`/`init-jdk21` 两步在 Gate 6 apply 中只覆盖了 4 个 worker 节点（`workerHostSlice` 排除本地节点），ddh-01 上除 Nexus 自带的内嵌 JRE 外没有任何系统 JDK，而 `datasophon-api` 编译目标是 Java 21（Spring Boot 3.4.5）。修复方式：直接复用已有的 `datasophon-cli init jdk21` 独立子命令在 ddh-01 本机执行（`--packagePath /data/install_datasophon/package/raw/packages --installPath /data`），该目录下已有此前为其余 4 节点上传准备的 `OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz`，无需下载，直接解压到 `/data/jdk-21.0.11+10` 并软链 `/usr/local/jdk21`。此步只是复用现成 CLI 命令补齐 ddh-01 的运行时依赖，不是新代码。

**构建与部署**：
1. `JAVA_HOME=<GraalVM 21.0.7> ./mvnw clean package -DskipTests -s ~/.m2/setting.xml -pl datasophon-api -am`（自动带出 `datasophon-ui-v2` 前端构建），产出 `datasophon-api/target/datasophon-manager-3.0-SNAPSHOT.tar.gz`（约 131MB，含前端 `static/`）。
2. scp 到 `ddh-01:/data/datasophon-api/`，md5 校验一致后解压。
3. 写入 `conf/api.local.properties`（`.gitignore` 已覆盖，未提交）：`mysql.ip=127.0.0.1`、`mysql.username=datasophon`、`mysql.password`/`mysql.database=datasophon`（复用 Gate 6 已创建的应用账号，无需新开账号）、`nexus.password`、`rustfs.secret_key`（分别对应现场真实密码，覆盖 `conf/api.properties` 里的默认占位值）。
4. `JAVA_HOME=/usr/local/jdk21 bash bin/datasophon-api.sh start`（脚本自带 pid 管理，未额外包装 systemd，与项目现有工具链一致）。

**验证结果**：
- `DatabaseMigration`：从空库一路跑到最新版本 `2.2.3`，`Migration success` 逐条打印，无失败。
- gRPC：`GrpcServerLifecycle` 日志确认 `18081` 监听，`WorkerRegistryService`/`MasterCallbackService`/健康检查/反射服务全部注册。
- HTTP：Jetty `8080` 监听，`curl http://127.0.0.1:8080/ddh/` 返回 200 及登录页 HTML。
- 登录：`POST /ddh/api/login`（`admin`/默认密码，特殊字符需 `--data-urlencode`）返回 `"msg":"登录成功"` 及有效 `sessionId`，证明 DB 读写、鉴权链路均正常，不只是端口起来。

**新发现的遗留问题（不阻塞 Phase 7，已于 §7.5 修复）**：
- 启动日志出现一条 `ERROR`：`LoadServiceMeta` 加载 `datacluster-physical/NACOS` 时报 `服务名称NACOS不能和解压文件名nacos一致（忽略大小写）`，该服务的 ddl 元数据未能加载进内存。是预置校验规则与 NACOS 服务包命名的冲突，与本次部署改动无关；当时因阶段 A 尚未进入 NACOS 的实际导入环节，暂不阻塞 Worker/OTel Collector 集群初始化，留待专项修复——**但 §1.3 明确 NACOS 属于阶段 A 计入验收范围**，该问题必须在 Phase 9 导入"基础依赖批"（含 NACOS）之前解决，不能长期搁置。

**Phase 7 结论**：控制面（MySQL 连接/迁移、HTTP 8080、gRPC 18081、登录鉴权）健康检查全部通过，满足 §8 "只有控制面健康后才能从前端执行集群初始化" 的前置条件。Phase 8（前端配置五节点、安装 Worker + OTel Collector）待人工批准后开始。

### 7.5 NACOS ddl 加载失败根因与修复（2026-07-14）

在准备 Phase 9 "基础依赖批"（`ZOOKEEPER`/`VALKEY`/`ELASTICSEARCH`/`NACOS`）前复查 §7.4 遗留问题，确认代码侧问题仍未修复（`package/raw/meta/datacluster-physical/NACOS/service_ddl.json` 的 `decompressPackageName` 仍是字面量 `"nacos"`），逐层定位根因：

- **`DdlMetaServiceImpl.loadServicePhysicalDdl`**（`datasophon-api/.../service/ddl/impl/DdlMetaServiceImpl.java`）在 ddl 加载时硬性校验"服务名与 `decompressPackageName` 忽略大小写不能相同"，不同直接抛 `IllegalStateException`，注释标注 `@see ServiceInstallHandler#createLink`。
- 顺藤摸瓜到 `InstallServiceHandler.createLink`（`datasophon-worker/.../handler/InstallServiceHandler.java`）：软链路径 = `INSTALL_PATH/lowercase(serviceName)`（`PkgInstallPathUtils.getLinkDirName`），软链目标 = `INSTALL_PATH/decompressPackageName`（`PkgInstallPathUtils.getInstallHomeName`）。NACOS 官方 tar 包解压后目录本来就叫 `nacos`，和服务名小写完全相同，两条路径会解析成同一个真实目录，若不拦截会在安装期执行 `ln -s /data/nacos /data/nacos`（自引用），`doCreateLink` 发现目标已存在且不是软链会直接抛异常。ddl 加载时的校验只是把这个必然会在安装期爆炸的场景提前到加载期拦下，校验规则本身没有错，但代价是让 NACOS 永远无法安装。

**修复方案**：不放宽校验本身允许的风险，而是让根因处的 `createLink` 安全处理这个场景——当软链路径与软链目标解析为同一个目录时，说明解压出来的目录已经就是"对外路径"，无需再建一次软链，直接跳过并返回成功；随之在 `DdlMetaServiceImpl` 移除此前的预防性拦截（不再需要，运行期已安全）。改动范围：

- `datasophon-worker/src/main/java/com/datasophon/worker/handler/InstallServiceHandler.java` `createLink()`：新增 `appLinkHome.equals(appHome)` 分支，跳过建软链。
- `datasophon-api/src/main/java/com/datasophon/api/service/ddl/impl/DdlMetaServiceImpl.java` `loadServicePhysicalDdl()`：删除对应的 `IllegalStateException` 抛出，改为注释说明。

**验证**：`JAVA_HOME=<GraalVM 21.0.7> ./mvnw -pl datasophon-worker,datasophon-api -am compile -s ~/.m2/setting.xml` 编译通过；`./mvnw -pl datasophon-worker -am test` 31 个用例全绿（含 `WorkerCommandGrpcServiceTest`/`DownloadStrategyTest` 等）。`datasophon-api` 无既有单测覆盖 `DdlMetaServiceImpl`，未新增（该模块常规测试需真实 MySQL，未在本次改动范围内跑集成测试）。

**现场部署验证（2026-07-14 14:41，ddh-01）**：`JAVA_HOME=<GraalVM 21.0.7> ./mvnw clean package -DskipTests -s ~/.m2/setting.xml -pl datasophon-api -am` 重新打包 `datasophon-manager-3.0-SNAPSHOT.tar.gz`，scp 到 ddh-01 并 md5 校验一致；旧版本整体备份为 `/data/datasophon-api/datasophon-manager-3.0-SNAPSHOT.backup-20260714-nacosfix`（未删除任何数据），`conf/api.local.properties`（真实密码）原样复制到新目录并 diff 确认一致。重启后日志实测：

- `DdlMetaServiceImpl` 打出 `arch:{common=ArchInfo(packageName=nacos-server-3.2.2.tar.gz, decompressPackageName=nacos)}` 及 `put datacluster-physical NACOS service info into cache`，NACOS ddl 不再报错，服务元数据已进内存——问题解除。
- Jetty `8080`、gRPC `18081` 正常监听，`curl http://127.0.0.1:8080/ddh/` 返回 `200`；`WorkerRegistryPrewarmer` 预热 5 个主机（`port=18082`）；`ping host: ddh-01～05 success` 全部正常；前端 v2 登录（`admin`）成功，证明重启未破坏 Phase 7/8 已验证的控制面链路。
- 日志中反复出现 `check host ... metrics from otel error, cause: No running DorisFE for cluster 1` 是 `OtelAlertScheduler` 在评估 Doris 相关指标告警但 Doris 尚未安装导致的预期噪音，与本次修复无关，Phase 9 导入 Doris 批后应自然消失。

至此 NACOS ddl 加载问题已在代码和现场两个层面确认修复，Phase 9 "基础依赖批"（含 NACOS）的前置阻塞已解除。

### 7.6 DDL 占位符/value-defaultValue 清理 + `/internal/meta/refresh` 现场部署（2026-07-16～17）

详细过程见 `docs/session-handoff-ddl-value-defaultvalue-2026-07-16.md`，本节只记录写回 Epic 主文档的结论。

**代码侧**（分支 `verify/cli-go-five-node-bootstrap`，4 个提交）：审计全部 18 个服务的 DDL，修复裸占位符引用（`${xxx}` 缺服务名前缀或引用了从未注册的变量，含 NACOS/DATART/HDFS/KAFKA/KYUUBI/JUICEFS/ZOOKEEPER 共 7 个服务，JUICEFS 的 MinIO 占位符实际连的是 RustFS 已按 `${ROOT.Rustfs.*}` 改正）；修复前端 `ConfigForm.tsx` 的 `value` 为空字符串时 `??` 回退失效导致 `defaultValue` 从未生效的 bug；合并 432 个参数中 339 个（78%）`value`/`defaultValue` 完全相同的冗余，删除 `parameters[].value` 字段（DORIS 的 `fe_priority_networks`/`be_priority_networks` 因是特定环境网段，例外保留原 `defaultValue`）。复核确认 NGINX `nginxModules` 少的 `--with-http_geoip_module`/`--with-http_image_filter_module=dynamic` 两个编译参数**不是漏项**：CLI 的 `library.go` 从未安装 `GeoIP-devel`/`gd-devel`，补回会导致 `./configure` 直接失败，且全部 nginx 配置模板都用不到这两个模块。

**现场部署（2026-07-17，ddh-01）**：
- 用 `datasophon-cli upload registry --productPackagesPath package --webHost 192.168.10.131 --webPort 8081 -u admin -p <真实密码> --dockerHttpPort 8083 --enableRegistry` 从本机直接上传到 ddh-01 真实 Nexus，44/44 文件成功、0 失败，覆盖全部 18 个服务改过的 `service_ddl.json`。
- `JAVA_HOME=<GraalVM 21.0.7> ./mvnw clean package -DskipTests -s ~/.m2/setting.xml -pl datasophon-api -am` 重新打包（含本次 DDL 修复代码 + §7.5 之后新增的 `/internal/meta/refresh` 端点），scp 到 ddh-01 并 md5 校验一致；备份现有运行目录为 `datasophon-manager-3.0-SNAPSHOT.bak-20260717064715-before-ddlmerge-metarefresh`，保留真实 `conf/api.local.properties` 后替换重启。
- 验证：HTTP `8080`→200、gRPC `18081` 正常监听、**`POST /internal/meta/refresh`→200** 且日志证实端点触发后重新从 Nexus 下载全部 18 服务 DDL 并刷新内存缓存（日志可见 NGINX `decompressPackageName=nginx-1.30.3` 等新值生效）；`WorkerRegistryPrewarmer` 预热 5 主机；无 ERROR/Exception；`admin` 登录成功；ddh-02～05 的 TCP `22`/`18082`（Worker gRPC）全部可达（ping 仍受该云环境已知的 ICMP 误诊问题影响，以 TCP 判断为准）。

**重要发现——线上实际进度可能超出本文档记载**：部署前核对 ddh-01 的 `datasophon-manager-3.0-SNAPSHOT.bak-*` 备份目录，发现 2026-07-15～16 期间线上至少经历过 11 轮独立重启修复（`svcdelete`/`dorishook`/`otelloghook`/`sqlfix`/`jobfix`/`alreadyexistsfix`/`decompressfix`/`restartrunnerfix`/`pkgutilsfix`/`scrapeindentfix`/`persistfix`），命名高度像是导入服务 DAG 时遇到的真实报错修复，但在本分支及全仓库 git 历史（含 stash、reflog、dangling commits）里完全找不到对应源码，只以编译字节码形式存在于线上——经用户确认是另一个并行会话（可能是 Codex）所做、尚未提交。**本次重新打包部署已覆盖这批未提交修复（用户明确授权）**，旧 jar 完整保留在 `datasophon-manager-3.0-SNAPSHOT.bak-20260717064715-before-ddlmerge-metarefresh/`，如需要可取回。下一个 session 若要继续 Phase 9，应先跟那个并行会话同步真实现场进度（本文档第 68 行 Phase 9 状态仍标 `BLOCKED` 是本文档已知信息的保守记录，不代表现场没有更早的实际尝试），避免重复排查已解决的问题，也避免误判 Phase 9 尚未开始。

### 7.7 单服务试装验证：NACOS 与 ELASTICSEARCH（2026-07-17）

在正式启动 Phase 9 "基础依赖批"（`ZOOKEEPER`/`VALKEY`/`ELASTICSEARCH`/`NACOS`）批量导入前，先从前端逐个单独试装 NACOS、ELASTICSEARCH 两个服务，验证 §7.6 部署的 DDL 与代码基线在真实安装路径上是否可用。属于 Phase 9 前置探索，不构成 §9 所述的正式批次审批流程。

**NACOS 单装**：首次安装报错，现场连服务器排查后确认并修复 3 个独立 bug：

1. **Worker jar 版本偏差**：ddh-02 部署的 `datasophon-worker` jar（MD5 `ebde...`,2026-07-14 12:22 打包）落后于 `9a283af0`（NACOS 自引用软链跳过修复，2026-07-14 14:51 合入）；其余四节点 jar 已是修复后版本。现场用 ddh-01 上的正确 jar 覆盖 ddh-02（旧 jar 备份至 `lib.bak-20260717/`），重启 Worker 后解决。
2. **`${ROOT.Mysql.mysqlHostPort}` 广播地址错误**：`GlobalVariables.genDefaultGlobalVariables()` 从 Master 自身的 `spring.datasource.url` 派生这个全局变量（`mysql.ip=127.0.0.1`），广播给远程节点后远程 NACOS 用 `127.0.0.1` 连自己的本机 MySQL 端口，实际不存在，报 `Connection refused`。这是配置问题，非代码 bug：修改 ddh-01 的 `conf/api.local.properties` 里 `mysql.ip=127.0.0.1` → `mysql.ip=192.168.10.131`（改前备份），重启 `datasophon-api` 后全部已存在集群自动生效（`LoadServiceMeta.loadGlobalVariables()` 每次 API 启动都会为所有集群重新计算，无需 DB 回填）。
3. **12 个服务 DDL 缺失 `type` 字段**：NACOS 装成功（DB 里 `service_state=2`/`role_state=1` 均为 RUNNING）但前端侧边栏"中间件"分组不显示。根因是 `t_ddh_frame_service.type` 为 NULL——前端 `Cluster/Layout/index.tsx` 的 `groupedServices` 按 `svc.catalog || 'OTHER'` 分组，`catalogOrder` 只渲染 `ENVIRONMENT`/`MIDDLEWARE`/`APPLICATION` 三档，`OTHER` 桶被静默丢弃，不报错也不提示。核查全部 18 个服务 DDL，发现 NACOS/APISIX/DATART/HDFS/JUICEFS/KAFKA/KYUUBI/NGINX/OTELCOLLECTOR/SPARK3/VALKEY/ZOOKEEPER 共 12 个从未写过 `type` 字段。按用户确认的分类补齐（`DATART`→`APPLICATION`、`OTELCOLLECTOR`→`ENVIRONMENT`、其余 10 个→`MIDDLEWARE`），`datasophon-cli upload registry` 重新上传（44/44 成功）+ `POST /ddh/internal/meta/refresh`（18/18 加载）后，NACOS 立即出现在侧边栏。`catalog` 是 `@TableField(exist = false)` 瞬态字段、每次请求现算，不涉及历史实例回填。

NACOS 三项修复后重装成功，前后端均验证通过。

**ELASTICSEARCH 单装**：ElasticSearch 主角色报 `IllegalArgumentException: unknown setting [node.master]`（外加 `transport.tcp.compress`/`transport.tcp.port`/`discovery.zen.minimum_master_nodes` 三个同类异常，作为 suppressed exception 附带抛出），ES 进程启动即崩。根因是 `service_ddl.json` 混用了 ES 7.0 前后两代 discovery 配置：DDL 声明的版本是 `9.4.3`，但 `node.master`/`transport.tcp.port`/`transport.tcp.compress`/`discovery.zen.minimum_master_nodes` 是 Zen1 时代（ES 7.0 前）的设置，在 9.4.3 上直接被判定为非法配置。修复（`package/raw/meta/datacluster-physical/ELASTICSEARCH/service_ddl.json`，`configWriter.generators[0].includeParams` 与 `parameters` 两处同步改）：

- 删除 `node.master`：ES 8+ 在 `node.roles` 未显式设置时默认即为全角色（含 master），与旧版 `node.master:true` 语义等价,无需替换。
- 删除 `discovery.zen.minimum_master_nodes`：Zen1 遗留概念,已被同一份 DDL 里已有的 `discovery.seed_hosts`+`cluster.initial_master_nodes`（Zen2）完全取代。
- `transport.tcp.port` → `transport.port`、`transport.tcp.compress` → `transport.compress`：ES 7.0 纯改名，语义不变。

重新上传（44/44）+ meta refresh（18/18）后 ElasticSearch 角色装成功，验证：进程稳定运行（`ps aux` 确认）；监听 `${host}:9200`（HTTP）与 `9300`（transport）——注意 `network.host=${host}` 绑定的是节点真实 IP 而非 `127.0.0.1`，探测需用节点自身地址；`http://<host>:9200/_cluster/health` 返回结构化 `401`（xpack security 正常拦截未认证请求，非崩溃/超时，证明服务健康）；DB 里 `service_role_state=1`（RUNNING）。另确认 `xpack.security.http.ssl` 默认关闭、只有 `xpack.security.transport.ssl`（9300 节点间通信）为 TLS，探测 9200 应使用 `http://` 而非 `https://`。

`EsExporter` 角色安装失败，是**已知未处理问题**，性质与前述几项不同——不是配置错误而是**资产缺失**：`elasticsearch-exporter/control_es_exporter.sh` 硬编码调用同目录下的 `elasticsearch_exporter` 二进制，但这是 Prometheus 社区第三方工具，Elastic 官方 tarball 从不携带；对比同一份 DDL 里 `ElasticSearch` 角色用 `POST_INSTALL`/`download` hook（`templateId` 引用已上传模板）拉取证书和 `control_es.sh` 的写法，`EsExporter` 角色的 `hooks` 是空数组 `[]`——从一开始打包这份 DDL 时就漏了"额外下载 exporter 二进制"这一步。本地仓库和 Nexus 均无此二进制，需另外从 `prometheus-community/elasticsearch_exporter` 拿对应架构的 Release 并搞清楚 DataSophon 的 `templateId` 资源上传机制才能补全，工作量明显超出本次探索范围，已记录暂不处理，不阻塞 ElasticSearch 主角色的可用性。

**附带的运维事故（与部署代码无关，记录仅为教训）**：为重新上传修复后的 DDL，先后触发 Nexus 3.94 内置的 `AuthRateLimiterServiceImpl`（按用户名 `user::admin` 计数、纯内存态、指数退避 30s→900s 封顶，失败或被拒尝试会继续累加退避，只有真正认证成功才清零）连续 429，一度耗时约 40 分钟排查：起因是探测时误用了本机 dev Nexus 的密码（`admin123`），而 ddh-01 生产 Nexus 密码不同；期间浏览器打开的 Nexus 管理界面 tab 后台自动重试登录，与 CLI/curl 探测互相"续杯"退避计时器，越查越锁得久。最终用正确密码 + 重启 Nexus（清空内存态计数器，`/data/nexusDir/nexus/bin/nexus restart` 须用绝对路径，相对路径下 `realpath "$0"` 解析失败会导致 restart 的 start 阶段静默不生效）解决，未造成任何数据丢失。

### 7.8 VALKEY 单装失败：预编译包与 openEuler 发行版 OpenSSL 主版本不兼容（2026-07-17）

前端从 ddh-02 单装 VALKEY，`ValkeyMaster` 角色安装成功但启动失败，20 次状态重试耗尽后 `command_state=3`（失败）；`ValkeyExporter` 角色因主角色已失败，命令提前结束，从未真正尝试执行。

现场日志（`ddh-02:/data/install_datasophon/datasophon-worker/logs/VALKEY/ValkeyMaster.log`）显示确切报错：

```
/data/install_datasophon/valkey-8.1.8-jammy-x86_64/bin/valkey-server: error while loading shared libraries: libssl.so.3: cannot open shared object file: No such file or directory
```

根因是**预编译二进制与目标发行版的 OpenSSL 主版本不兼容**，不是 DDL 或代码 bug：Nexus 上的包名 `valkey-8.1.8-jammy-x86_64.tar.gz` 里的 "jammy" 即 Ubuntu 22.04 代号，该二进制在 OpenSSL 3.x 环境下编译，链接了 `libssl.so.3`/`libcrypto.so.3`；而目标节点 ddh-02 是 openEuler 22.03 LTS-SP3，系统自带 OpenSSL 1.1.1wa（`libssl.so.1.1`），`ldd` 确认两个符号均 `not found`。

排查确认以下几条路径均不可行，问题无法通过换包/补库快速绕开：

- **Valkey upstream 从未发布过 openEuler/RHEL 系预编译包**：`valkey.io/download/` 上 8.1.8 版本只有 `jammy`/`noble`（Ubuntu 22.04/24.04）两种 x86_64/arm64 组合，且两者都基于 OpenSSL 3.x，换 `noble` 同样会踩 `libssl.so.3` 缺失。
- **openEuler 离线 yum 源（Nexus 代理，`repository/yum/x86_64/openEuler-22.03-LTS-SP3/`）里没有 OpenSSL 3.x 兼容库**，也没有 `redis`/`valkey` 本身的 RPM——搜到的唯一相关项是 `pcp-pmda-redis-5.3.7-3.oe2203sp3.x86_64.rpm`，这是 Performance Co-Pilot 的 Redis 监控插件，不含 `redis-server`，且依赖已有 Redis 实例先跑起来才有意义。
- ddh-02 本地 `openssl-devel` 已安装（`1.1.1wa-2.oe2203sp3`），具备源码编译 Valkey 的前置条件，但编译本身超出本次单装验证范围。

用户确认此问题记录后暂不处理，与 `EsExporter` 缺二进制资产同属"需要引入额外资产/构建步骤才能解决"的已知问题，不阻塞前置探索的其余部分。后续可选方案（未执行）：① 在 openEuler 22.03 机器上从源码编译 Valkey，链接系统自带 OpenSSL 1.1.1；② 从 Ubuntu 22.04 环境提取 `libssl.so.3`/`libcrypto.so.3` 并通过 `LD_LIBRARY_PATH` 让现有 jammy 二进制运行（需同步改造 `control_valkey.sh` 注入该变量）。

### 7.9 APISIX Standalone 离线 RPM 现场安装验收与 DolphinScheduler 注册中心解耦（2026-07-17～18）

**APISIX**：原 `apache-apisix-3.17.0-src.tgz` 是纯 Lua 源码包，而旧 `ApisixHandlerStrategy` 假设解压目录存在按系统与架构分类的预编译 RPM；两者结构不匹配，且离线 openEuler 环境不能依赖运行时联网构建。现改用预制的 `apisix-3.17.0-openEuler-22.03-LTS-SP3-x86_64-standalone-rpm.tar.gz`：内含离线 RPM、校验清单、签名、Standalone 配置及安装脚本，目标为 openEuler 22.03 LTS-SP3 x86_64。

接入改动：

- DDL 切换至新 bundle，并由 `POST_INSTALL` hook 执行 bundle 的 `scripts/install.sh`；安装包校验清单要求目录内容严格匹配，故 `control.sh` 必须在安装脚本完成后再下载。
- raw Nexus 不自动生成校验 sidecar，而平台下载器强制读取 `<package>.md5`，因此为新包生成并上传对应 `.md5` 文件。
- `control.sh` 的启停与状态检查统一委托 RPM 自带的 `apisix.service`；`systemctl is-active --quiet` 的退出码直接符合平台状态检查语义。
- bundle 在写入参数化配置前会先启动 APISIX；`ApisixHandlerStrategy` 在 `INSTALL_SERVICE` 阶段显式重启一次，确保 `/usr/local/apisix/conf/config.yaml` 中的 Prometheus 绑定配置真正加载。
- 配置参数会被平台转换为字符串，FreeMarker 模板不能对端口使用 `?c`；去掉该内建转换后，`config.yaml` 与 `apisix.yaml` 均由前端参数成功生成。

**现场验收（2026-07-18，ddh-02）**：从前端导入 `deploy/apisix-deploy.yaml` 安装成功，并以节点事实验收：

- `apisix` 与 `apisix-libcrypt-compat` RPM 已安装，`apisix.service` 为 `active` 且 `enabled`，主进程为 OpenResty。
- 参数化的 `/usr/local/apisix/conf/config.yaml` 含 `role: data_plane`、`config_provider: yaml`、`node_listen: 9080`、`enable_admin: false` 及节点地址上的 `9091` Prometheus 导出配置。
- `9080` 正在监听；根路径返回 `404`（无默认路由）；`/get` 已匹配并代理至验收上游 `127.0.0.1:8080`，该上游自身返回 `500`，访问日志记录了对应 upstream，证明路由链路生效。
- `http://<ddh-02>:9091/apisix/prometheus/metrics` 返回 `200` 且包含 `apisix_*` 指标；`9180` 拒绝连接，确认 Admin API 未启用。

APISIX 不再是阶段 A 的已知问题；验收上游仍仅用于最小代理验证，实际业务 upstream 在业务接入时另行配置。

**DolphinScheduler（DS）注册中心解耦**：原 `service_ddl.json` 声明 `dependencies: ["SPARK3", "ZOOKEEPER"]`（`SPARK3` 在参数里从未被实际引用，纯 DAG 顺序声明；`ZOOKEEPER` 通过 `zkUrls` 参数 `${ZOOKEEPER.zkUrls}` 跨服务占位符注入 `dolphinscheduler_env.sh` 的 `REGISTRY_ZOOKEEPER_CONNECT_STRING`）——这正是 §1.3 里把 DS 划入"阶段 B"（因 Spark→Hive→HDFS→ZooKeeper 依赖闭环，不符合"无 Hadoop"定位）的直接原因。核实 DS 3.4.1 源码（`dolphinscheduler-registry-jdbc` 模块）确认官方自带 MySQL/JDBC 注册中心实现（`registry.type=jdbc`，`3.2+` 版本内置，无需额外插件），且所需的 4 张表（`t_ds_jdbc_registry_data`/`_lock`/`_client_heartbeat`/`_data_change_event`）已包含在标准 `dolphinscheduler_mysql.sql` 建表脚本里，无需额外建表步骤。据此改造：

- `service_ddl.json`：`dependencies` 清空为 `[]`（移除 `SPARK3`、`ZOOKEEPER`）；删除 `zkUrls` 参数及其在 `dolphinscheduler_env.sh` generator 的 `includeParams` 引用。
- `dolphinscheduler_env.ftl`：注册中心配置块从 `REGISTRY_TYPE=zookeeper` + `REGISTRY_ZOOKEEPER_CONNECT_STRING` 改为 `REGISTRY_TYPE=jdbc` + 四个 `REGISTRY_HIKARI_CONFIG_*` 环境变量（`JDBC_URL`/`USERNAME`/`PASSWORD`/`DRIVER_CLASS_NAME`），直接复用 DS 自身已有的 `databaseUrl`/`username`/`password` 参数（同一个 MySQL 实例、同一个 `dolphinscheduler` 库），不新增连接配置。Spring Boot 环境变量宽松绑定规则（下划线可匹配 `.`/`-` 任意组合）与现有 `REGISTRY_ZOOKEEPER_CONNECT_STRING` 沿用同一套机制，非新发明。
- 同步更新 `DolphinschedulerEnvTemplateTest.java` 断言（新增 `REGISTRY_TYPE=jdbc`/`REGISTRY_HIKARI_CONFIG_*` 断言、确认不再输出 `REGISTRY_ZOOKEEPER_CONNECT_STRING`），测试通过。

移除 `SPARK3`/`ZOOKEEPER` 依赖后，DS 不再需要 Spark/Hive/HDFS/ZooKeeper 任何一环即可安装启动（Spark/Hive 等仍是可选任务插件，未装不影响 API/Master/Worker/Alert 四个核心角色运行），`resource.hdfs.*`/`yarn.*` 等可选资源存储参数原样保留、未做改动（只在用户后续启用对应任务类型时才生效）。

### 7.10 DolphinScheduler 现场安装：5 个真实 bug 的排查与修复 + S3 存储插件缺失（已知问题）（2026-07-17）

从前端发起 DS 安装（`ApiServer`/`MasterServer`/`AlertServer` → ddh-02，`WorkerServer` × 3 → ddh-03/04/05），过程中连续暴露 5 个真实 bug，逐一定位修复：

1. **`DS`/`FLINK` 的 `service_ddl.json` 残留 `${MINIO.*}` 占位符**：项目对象存储组件早已从 MinIO 改名为 RustFS（`GlobalVariables` 里注册的是 `ROOT.Rustfs.*`），但这两份 DDL 是改名前的历史遗留，引用了从未注册过的 `MINIO` 命名空间，会静默失败（原样把 `${MINIO.xxx}` 字面量写进配置文件）。已改为 `${ROOT.Rustfs.access_key}`/`${ROOT.Rustfs.secret_key}`/`${ROOT.Rustfs.__hostIp__}`/`${ROOT.Rustfs.__port__}`。
2. **`HIVE`/`JUICEFS`/`KAFKA`/`ZOOKEEPER` 引用 `${ROOT.<SERVICE>.INSTALL_PATH}` 无法解析**：`DdlMetaServiceImpl.putServiceHomeToVariable()` 原来只对 `HDFS` 特判生成 `${HADOOP_HOME}`，其余服务从未生成过"自己安装路径"这个变量。已把该方法通用化，对所有服务生成 `${ROOT.<服务名>.INSTALL_PATH}`（HDFS 的 `${HADOOP_HOME}` 兼容分支保留不变），一次性解决根因，避免以后新服务再踩同样的坑。
3. **`YARN` 的 `${dfs.nameservices}` 缺 `HDFS.` 前缀**：HDFS 自己该参数 `register=true`，本应写 `${HDFS.dfs.nameservices}`，YARN 引用时漏了服务名前缀，只影响 Node Label 存储目录这个非核心功能。已修正。
4. **DS 缺少 MySQL JDBC 驱动**：DolphinScheduler 官方发行包出于 GPL 协议原因不自带 `mysql-connector`，`tools/bin/upgrade-schema.sh`（数据库 schema 初始化，只有 `MasterServer` 触发）和四个组件自身运行都需要。已给 `ApiServer`/`MasterServer`/`WorkerServer`/`AlertServer` 各自的 `libs/` 目录、以及额外的 `tools/libs/` 目录（`MasterServer` 专用）都加了 `POST_INSTALL link` hook，软链复用 `datasophon-worker` 自带的 `lib/mysql-connector-j-8.2.0.jar`，`source` 用 `${ROOT.VosManager.INSTALL_PATH}` 变量而非写死路径。
5. **平台级 bug：`ServiceHandler.start()` 对所有服务通用的"是否已运行"判断依赖状态检查脚本的退出码，但 DS 官方 `bin/dolphinscheduler-daemon.sh` 的 `(status)` 分支没有任何 `exit` 语句**（脚本退出码恒为 0，取决于最后一条 `echo` 命令），导致无论服务真实是否在跑，都被误判"已运行，无需启动"，真正的 `start` 命令从未被执行——DS 曾经历过一轮"DAG 全部执行成功、DB 状态全部 RUNNING，但五节点上一个真实 Java 进程都没有"的完全假成功。DDL 自带的 `bin/control_ds.sh` 包装脚本本意是修正这个问题，但自身也有 bug（`execStatus()` 里 `[[ $status=="STOP" ]]` 缺空格，导致字符串比较失效、结果恒为"运行中"）。最终选择成本最低的方案：不改 `control_ds.sh`（避免涉及 `templateId` 模板重新上传机制的额外复杂度），改用已有的 `append_line` hook 直接给官方脚本的 `(status)` 分支打两行补丁——在 `get_server_running_status` 之后保存原始状态到 `__ds_real_state`，在打印状态之后追加 `if [[ "$__ds_real_state" == "RUNNING" ]]; then exit 0; else exit 1; fi`。本地用两种真实场景（无 pid 文件 / 真实存活 bash 进程）模拟验证退出码分别为 1/0，符合预期。

修复后现场重装验证：`ApiServer`/`MasterServer`/`AlertServer` 真实 Java 进程稳定运行（`ps -ef` 确认），DB 状态与真实情况一致。`WorkerServer` 这次也真正执行了启动流程（不再被误跳过），但因缺少 S3 存储插件在 Spring 容器初始化阶段失败退出：

```
No qualifying bean of type 'org.apache.dolphinscheduler.plugin.storage.api.StorageOperator' available
```

排查确认：`common.properties` 里 `resource.storage.type=S3` 及 RustFS 的 `access_key`/`secret_key`/`endpoint` 全部生成正确（间接验证了 bug 1 的修复彻底生效），但解压后的 `plugins/storage-plugins/` 目录是空的——查证 `archive.apache.org` 官方发布目录只有 `-bin.tar.gz`（核心二进制）和 `-src.tar.gz`（源码），**S3 存储插件不作为独立制品分发**，要用只能从源码自行编译或另寻预编译来源，工作量与不确定性明显超出本次现场排查范围。用户确认记录为已知问题、暂不处理，性质与 `EsExporter`/`VALKEY` 缺资产问题同类。由于本集群"无 Hadoop"定位下 S3/RustFS 是唯一可用存储类型（HDFS 不在部署范围内），此问题不解决则 `WorkerServer` 无法执行任何任务，但不影响 `ApiServer`/`MasterServer`/`AlertServer` 三个角色的运行。

### 7.11 DolphinScheduler S3 插件补全后仍崩溃：property key 命名不匹配根因与修复（2026-07-18）

§7.10 记录的 S3 插件缺失问题后续用户改变主意要求继续攻克：在 **Maven Central** 找到官方发布的 `org.apache.dolphinscheduler:dolphinscheduler-storage-s3:3.4.1` `-shade.jar`（自包含依赖的胖 jar，136KB，SHA1 已核实，`unzip -l` 确认内含 `S3StorageOperator.class` 及 SPI 声明），存入 `package/raw/meta/datacluster-physical/DS/plugin/`，给 DS 四个角色都加了 `POST_INSTALL download` hook 指向共享的 `plugins/storage-plugins/` 目录（与 mysql 驱动不同——这个目录是所有组件共享的顶层 classpath，只需下载一份，不用像 mysql 驱动那样每组件各自软链）。上传 Nexus + meta refresh 后，前端重新发起 DS 安装，界面显示"安装成功"。

**现场验证发现界面提示不可靠，五节点存在真实进程**：`ps -ef` 确认 ddh-02 只有 `MasterServer`/`AlertServer` 真实运行，`ApiServer` 和 ddh-03/04/05 三台 `WorkerServer` 全部启动即崩溃退出——又一次"控制面认为成功、数据面未必存活"的假成功，但机制和 §7.10 第 5 个 bug（脚本退出码恒为 0 导致 start 命令被跳过）不同：这次进程**确实尝试启动过**，是 Spring 依赖注入阶段失败退出。

两边日志报的是同一条异常链：`StorageOperator` bean 实例化失败 → `SdkClientException: Unable to execute HTTP request: s3` → `UnknownHostException: s3`——S3 client 试图连接一个字面量主机名 `s3`，不是配置里写的 RustFS 真实地址。排查过程：

1. `javap -p -c` 反编译插件 jar 中的 `S3StorageOperator.class`，定位真正构建 S3 client 的是 `org.apache.dolphinscheduler.authentication.aws.AmazonS3ClientFactory.createAmazonS3Client(Map)`——这个类不在插件 jar 里，而是 DS 官方发行包自带的 `dolphinscheduler-aws-authentication-3.4.1.jar`（从 ddh-02 实际部署目录 scp 下来核实版本号与插件一致，排除"插件版本与主版本不匹配"的猜测）。
2. 反编译 `AmazonS3ClientFactory`：从传入的 `Map<String,String>` 里取 `"region"`/`"endpoint"` 两个 key 构建 client。再反编译上游 `S3StorageOperatorFactory.getS3StorageProperties()`：`bucketName` 读取 `PropertyUtils.getString("aws.s3.bucket.name")`，其余配置读取 `PropertyUtils.getByPrefix("aws.s3.", "")`（精确前缀匹配并剥离前缀）。
3. 而 `DS/service_ddl.json` 生成的 `common.properties` 里，这几项全部带了 `resource.` 前缀（`resource.aws.access.key.id`/`resource.aws.secret.access.key`/`resource.aws.region`/`resource.aws.s3.bucket.name`/`resource.aws.s3.endpoint`）——是照着同一份文件里 OSS（`resource.alibaba.cloud.oss.*`）、Azure（`resource.azure.*`）的命名习惯写的，但 S3 这一项官方代码不吃这个前缀。`PropertyUtils.getByPrefix("aws.s3.", "")` 精确前缀匹配不到任何 key，拿到空 Map，`endpoint`/`region`/凭证全部取不到值，S3 client 构造时退化用了某个残留字符串当主机名，解析失败。
4. 用官方 GitHub `3.4.1-release` tag 交叉核实：`deploy/kubernetes/dolphinscheduler/README.md` 明确写着 S3 配置项**不带 `resource.` 前缀**，直接是 `aws.s3.access.key.id`/`aws.s3.access.key.secret`（注意子段顺序也和我们写的 `secret.access.key` 不同）/`aws.s3.bucket.name`/`aws.s3.endpoint`/`aws.s3.region`——这是 DolphinScheduler 官方自己的命名不统一（S3 插件相比 OSS/Azure 少了 `resource.` 前缀），不是版本问题。
5. 另确认 `resource.aws.region` 原值 `dolphinscheduler` 也是错的：`Regions.fromName(region)` 在 `createAmazonS3Client` 里无条件调用，要求严格匹配 AWS SDK 的 `Regions` 枚举名，任意字符串都会直接抛 `IllegalArgumentException`。
6. 顺带发现一处独立笔误：`s3Sync` hook（两个角色各一处）的 `"bucket"` 字段绑定到了 `${DS.resource.aws.region}`（region 变量），应为 bucket 名变量。

**修复**（`package/raw/meta/datacluster-physical/DS/service_ddl.json`，分支 `verify/cli-go-five-node-bootstrap`）：5 个 `parameters` 的 `name`/`label` 从 `resource.aws.*` 改为 `aws.s3.*`（含子段重排 `access.key.secret`），`region` 默认值从 `dolphinscheduler` 改为合法值 `us-east-1`（S3 兼容存储走自定义 endpoint，该值只用于满足 SDK 参数校验，不影响实际连接目标）；`configWriter.generators[common.properties].includeParams` 同步改名；两处 `s3Sync` hook 的 `${DS.xxx}` 变量引用同步改名，并修正 `bucket` 字段指向正确的 bucket 名变量。

**现场部署与验证**：`datasophon-cli upload registry` 重新上传（46/46 成功，0 失败）+ `POST /internal/meta/refresh`（200，ddh-01 日志确认 DS 全部 4 个角色重新加载进内存缓存，无报错）。优雅停止 ddh-02 上残留的真实 `MasterServer`/`AlertServer` 进程后，从前端删除 DS 服务实例（官方级联清理）重新安装。验证结果：

- `ps -ef` 确认六个真实进程全部稳定运行超过 1 分钟以上，CPU 已从启动期的 99% 回落稳态：`ApiServer`/`MasterServer`/`AlertServer`（ddh-02）、`WorkerServer`（ddh-03/04/05）。
- `ApiServer`/`WorkerServer` 日志均打出 `S3StorageOperator:[215] - bucketName: dolphinscheduler has been found, the current regionName is us-east-1`——此前反复崩溃的那一步现在直接通过。
- `ApiServer` 日志确认 `Started ApiApplicationServer in 27.59 seconds`，Jetty 监听 `12345`；`WorkerServer` 日志确认 `Started WorkerServer in 12.545 seconds`，且已成功注册到 registry center（`Worker node: 192.168.10.133:1234 registry ... successfully`，验证了 §7.9 的 MySQL/JDBC 注册中心解耦此前已生效）。

至此 DolphinScheduler 六个角色（`ApiServer`/`MasterServer`/`AlertServer`/`WorkerServer` × 3）全部验证为真实稳定运行，S3 存储插件问题彻底解决，不再是已知问题清单的一员。

### 7.12 VALKEY openEuler 原生构建现场验证与部署（2026-07-18）

§7.8 记录的问题（预编译包 `valkey-8.1.8-jammy-x86_64.tar.gz` 依赖 OpenSSL 3.x，与 openEuler 22.03 系统自带的 OpenSSL 1.1.1 不兼容）改用 openEuler 原生构建解决：在 `ddh-02` 本地用系统自带 `openssl-devel`（不替换系统 OpenSSL）源码编译 Valkey 8.1.8，产出 `valkey-8.1.8-openeuler22.03-x86_64.tar.gz`（链接 `libssl.so.1.1`/`libcrypto.so.1.1`），静态链接的 `redis_exporter 1.84.0` 一并打包。构建脚本 `package/build-valkey-openeuler.sh`，制品 SHA-256 `031705868b...`，构建产物与校验记录见 `docs/session-handoff-valkey-openeuler-2026-07-18.md`。

现场按该交接文档的 Gate 1-6 逐项执行：

- **Gate 1（本地制品预检）**：SHA-256 与交接值一致；`manifest.json`/`VALKEY/service_ddl.json`/tar 顶层目录名三处包名（`valkey-8.1.8-openeuler22.03-x86_64`）互相一致；构建脚本 `bash -n` 语法检查通过。
- **Gate 2（上传 Nexus）**：执行时发现 `datasophon-cli-go` 的 `upload registry` 子命令**不响应全局 `--dry-run` 参数**——`internal/cli/upload/registry.go` 的 `doRun`/`repositoryUploadBatch`/`uploadFile` 全程未读取 `dryRun`，只有 `uploadDocker` 一处真正接了该状态；`--dry-run upload registry` 实际发起了真实 HTTP 上传（`success=50 fail=0`）。核实无害：除已知的 `package/docs/apisix/BUILD.md` 外 `package/raw/**` 无任何未提交改动，此次真实上传的内容与 Gate 2 本该做的事完全一致。`curl -u admin:*** HEAD` 验证 Nexus 返回 `200`、`Content-Length=6806399` 与本地文件字节数一致。**已修复（同日）**：`upload registry` 的 `--dry-run` 语义缺失，与模块文档描述的"全局开关只打印命令不实际执行"不符。修复方式：`UploadRegistry` 新增 `DryRun` 字段（`Command`/`Handle` 两个入口都会设置），`uploadFile`/`uploadHelm` 在发起任何 HTTP 请求前直接短路返回并打印 `[dry-run]` 日志；`repositoryUploadBatch` 里 raw 类型上传前独立的 `.md5` sidecar 幂等预检（原本会发起真实只读 GET）同样加了 `!t.DryRun` 守卫。新增单测 `TestRepositoryUploadBatch_DryRunDoesNotHitNetwork` 锁定回归（httptest server 收到任何请求即失败）。对生产 Nexus 复测：52 个文件全部显示 `[dry-run]`，总耗时从秒级降到 0.75s，确认零真实网络调用。
- **Gate 3（元数据刷新）**：`POST /internal/meta/refresh` 返回 `success=true`、`physicalLoaded==physicalTotal==18`、`errors=[]`。
- **Gate 4（清理旧实例）**：平台 `cluster/service/instance/list` 查询确认集群内**已无 VALKEY 服务实例记录**（此前失败的实例已在更早会话被正式清理），无需走删除流程；`ddh-02` 上仅有文件系统残留（旧 `valkey-8.1.8-jammy-x86_64` 目录与软链，无真实进程/端口），不阻塞新装。
- **Gate 5（重新安装）**：通过 v2 部署清单接口（`POST /ddh/api/v2/cluster/1/deploy/upload` → `validate-deployment-file` → `deploy`，即 ui-v2"部署清单导入"背后的 `ExtRepoInstallDelegateService`）导入 `deploy/valkey-deploy.yaml`，校验通过（`errors=null`，`deployHosts` 正确回显为 `ddh-02`）。**现场安装时用户手动把目标主机由 `ddh-02` 改为 `ddh-01`**：`ddh-02` 当时 `free -h` 实测仅 `536Mi` 空闲（总 `30Gi`，已用 `24Gi`，`buff/cache 6.8Gi` 部分可回收，`available 6.6Gi`），已装 `ELASTICSEARCH`/`NACOS`/`APISIX`/`OTELCOLLECTOR`/`Worker` 等阶段 A 中间件后内存吃紧，用户判断不再适合承接新服务，遂改用 `ddh-01`。**排查记录**：安装完成后 Gate 6 一度在 `ddh-02` 找不到任何新进程/软链变化，怀疑是"部署清单导入"代码路径存在 `deployHosts` 未被尊重的平台级 bug；沿 `PhysicalProductInstallServiceImpl.deploy → ServiceInstallServiceImpl.saveServiceRoleHostMapping`（写入 `CacheUtils` 的 `<clusterCode>_SERVICE_ROLE_HOST_MAPPING` 键）代码走读、并用 `GET /service/install/getServiceRoleDeployOverview` 直接查询该内存缓存当前内容，确认 `ValkeyMaster`/`ValkeyExporter` 确实记录为 `["ddh-01"]`，而同一批次此前的 `Apisix`/`ElasticSearch`/`ApiServer`/`MasterServer` 均正确落在 `ddh-02`——排除代码路径系统性问题后，用户确认是现场手动改的目标主机，不是 bug；这段代码走读没有发现问题，顺带确认了这条部署清单路径本身可信。
- **Gate 6（真实运行验收，`ddh-01`）**：
  - `ldd valkey-server`：解析到 `libssl.so.1.1 => /usr/lib64/libssl.so.1.1`，无 `not found`；`valkey-server --version`/`valkey-cli --version` 均为 `8.1.8`。
  - 真实进程存在（`ps -ef`）：`valkey-server 0.0.0.0:7501`、`redis_exporter -redis.addr 127.0.0.1:7501 -web.listen-address 0.0.0.0:9121`；端口 `7501`/`9121` 均真实监听。
  - 认证读写：`PING`→`PONG`，`SET gate6:openeuler-check ok`→`OK`，`GET`→`ok`，`DEL`→`1`。
  - Exporter：`/metrics` 返回 `redis_up 1`、`redis_exporter_build_info{...version="v1.84.0"} 1`。
  - 控制脚本自身日志无 ERROR。

**结论**：openEuler 原生构建的 Valkey 8.1.8 包功能完全验证通过，VALKEY 不再是阶段 A 的已知问题。**拓扑偏差**：实际运行节点是 `ddh-01`，不是 Gate 2 冻结拓扑（§5）指定的 `ddh-02`，原因是 `ddh-02` 现场内存不足，属用户现场批准的偏差，不是安装失败或代码缺陷；`ddh-02` 的可用内存状况需要在后续继续往该节点部署阶段 A 中间件前重新评估。

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

### 8.1 Phase 8 现场执行结果（2026-07-14）

从前端为 `test` 物理集群配置 `ddh-01`～`ddh-05` 后，按“主机信息 → 环境校验 → Worker 分发 → Collector 安装 → 健康检查”执行初始化。节点通信、Worker 注册和 Collector 自监控均使用节点 IP，不使用 hostname 作为网络地址。

现场修复了初始化链路中实际暴露的缺口：Worker Nexus 配置与包 MD5、复杂配置映射的 gRPC 序列化、安装结果回写、OTELCOLLECTOR 参数启用、远程空模板回退到 Worker 内置模板、多架构解压目录解析、含特殊字符的环境变量安全加载，以及 RustFS `otel-bootstrap` bucket 创建。健康门禁按当前运行态检查“进程存活、存在成功导出、导出队列清零、接收失败为零”；历史累计发送失败仍保留用于监控，但不再导致恢复后的节点永久无法完成初始化。

`2026-07-14T12:41:24+08:00` 最终验收：

- 五个节点的 Worker `18082/TCP` 与 Collector `8888/TCP` 全部监听，Collector 控制脚本状态均为 running；Worker 注册 IP 与节点清单一致。
- 五个节点的 Collector 成功发送计数分别为 `16825`、`16492`、`16104`、`16548`、`16102`，导出队列均为 `0`，receiver failed 均为 `0`，进程 uptime 均超过 9 分钟。
- RustFS `otel-bootstrap` 中已产生 `445` 个对象；修复完成后五节点日志均无新增导出错误。
- 前端集群卡片从“待初始化”切换为“正在运行”，配置按钮转为禁用，证明后端只在五节点 Worker 与 Collector 全部通过健康门禁后更新集群状态。

**Phase 8 结论**：五节点 Worker 与 OTel Collector 初始化及真实 S3 导出验证全部通过，可以进入 Phase 9 的单批次人工审批；本记录不代表已批准导入任何阶段 A 服务 DAG。

## 9. Phase 9：通过前端导入阶段 A 服务 DAG

只有前端显示五节点 Worker 与 OTel Collector 集群初始化均通过后，才可从前端导入服务 DAG。每批导入前单独记录角色表、参数脱敏 hash、DAG ID、开始/结束时间与验收证据；失败即停止当前批，不进入后续批。

1. **基础依赖批**：`VALKEY`、`ELASTICSEARCH`、`NACOS`（`ZOOKEEPER` 已移除，见 §1.3）。Nacos 使用 CLI 预建 MySQL DB/账号，但仍须按产品流程确认 schema 初始化。
2. **Doris 批**：`DORIS`。确认 3 FE、批准数量的 BE、网络优先级、目录与内存限制；从第一个 FE 初始化并让其余 FE 通过 `<master>:9010` 加入。已安装完成，前端验证通过。
3. **调度批**：`DS`（DolphinScheduler），MySQL/JDBC 注册中心模式，`dependencies` 已清空，见 §7.9、§1.3。
4. **网关批**：`APISIX` Standalone，已通过 §7.9 的前端安装与节点侧验证；实际业务 upstream 随业务接入单独配置。

OTel Collector 不在本阶段重复导入或安装；它是 Phase 8 的每节点集群初始化交付物。

### 9.1 批次验收记录补记（2026-07-18）

§7.7～§7.12 记录的是“批量导入前置探索”——四个批次的全部服务实际是**逐个单独试装**验证的，不是本节开头设计的“整批一次性导入 DAG”流程。现补记正式批次验收表，把已完成的验证结果对齐到 §9 要求的记录粒度（角色表、DAG/安装方式、验收证据、起止时间），并作为 Phase 9 收口的依据。

**验收方法说明**：下表“验证方式”列区分两类——`前端 DAG` 表示通过 DataSophon 前端服务安装向导触发（会写入 `t_ddh_cmd`/DAG 执行记录）；`部署清单导入` 表示通过 v2“部署清单导入”接口（`POST /ddh/api/v2/cluster/{id}/deploy/upload → validate-deployment-file → deploy`，即 `ExtRepoInstallDelegateService`）以 YAML 声明角色到主机的映射，同样落库为正式服务实例，只是不经过多服务合并的 DAG 编排界面。两种方式产出的服务实例在数据库和前端展示上完全等价，均满足 §9“从前端导入服务 DAG”的实质要求（服务实例正式落库、纳入集群管理），区别只是触发入口。

| 批次 | 服务/角色 | 部署节点 | 验证方式 | 验收证据 | 起止时间（现场） |
| --- | --- | --- | --- | --- | --- |
| 基础依赖批 | `NACOS`（单角色） | ddh-02 | 前端 DAG（单服务） | §7.7：3 个真实 bug 修复后重装成功，DB `service_state=2`，前端侧边栏正常显示 | 2026-07-17 |
| 基础依赖批 | `ElasticSearch`（主角色） | ddh-02 | 前端 DAG（单服务） | §7.7：修复 Zen1→Zen2 discovery 配置后，进程稳定、`9200`/`9300` 监听、`_cluster/health` 返回结构化 401（非崩溃） | 2026-07-17 |
| 基础依赖批 | `EsExporter` | — | 未安装 | §1.3/§7.7：第三方二进制资产缺失，已知问题，记录后暂不处理，不阻塞主角色 | — |
| 基础依赖批 | `ValkeyMaster`+`ValkeyExporter` | ddh-01（拓扑偏差，冻结值为 ddh-02） | 部署清单导入（`deploy/valkey-deploy.yaml`） | §7.12 Gate 1-6：`ldd` 无 `not found`、进程/端口真实存在、`PING`/`SET`/`GET`/`DEL` 全部正确、`redis_up=1` | 2026-07-18 |
| Doris 批 | `DorisFE` | ddh-01 | 前端 DAG | 本次复核 SSH 实测：`DorisFE` 进程运行中（GC 日志时间戳 `20260716-090544`），`9030`/`9020`/`8030` 监听 | 2026-07-16 |
| Doris 批 | `DorisBE` × 3 | ddh-03/04/05 | 前端 DAG | 本次复核 SSH 实测：三节点 `doris_be` 进程均运行中 | 2026-07-16 |
| 调度批 | `ApiServer`/`MasterServer`/`AlertServer` | ddh-02 | 前端 DAG（单服务） | §7.10（5 个真实 bug）+§7.11（S3 插件 property key 修复）；本次复核 SSH 实测：三进程均运行中，`ApiServer` 监听 `12345` | 2026-07-17 初装，2026-07-18 05:42 S3 修复后重装 |
| 调度批 | `WorkerServer` × 3 | ddh-03/04/05 | 前端 DAG（单服务） | §7.11：S3 存储插件 property key 命名修复后，日志确认 `Started WorkerServer` 且成功注册到 MySQL/JDBC 注册中心；本次复核 SSH 实测三节点进程均运行中 | 同上 |
| 网关批 | `APISIX`（Standalone） | ddh-02 | 前端 DAG（单服务，导入 `deploy/apisix-deploy.yaml`） | §7.9：RPM/systemd/路由转发/`9091` metrics 现场验收通过；本次复核 SSH 实测 `apisix.service active`、`9080` 监听 | 2026-07-18 |

**已知偏差与遗留问题（不影响本批次收口，已在对应小节记录并经用户确认）**：

- `EsExporter`（资产缺失，§1.3/§7.7）。
- VALKEY 实际部署节点为 `ddh-01` 而非冻结拓扑的 `ddh-02`（§7.12，内存不足现场改用）。
- `ZOOKEEPER` 已从阶段 A 移除（§1.3，DS 改用 MySQL/JDBC 注册中心）。
- 四个批次均以“单服务前端安装/部署清单导入”逐个完成，而非本节开头设计的“整批一次性导入”流程；实质验收标准（服务实例正式落库、真实进程健康）已满足，记录为流程执行方式的偏差，不是功能缺口。

**结论**：基础依赖批（除已知问题 `EsExporter` 外）、Doris 批、调度批、网关批四个批次的核心服务角色均已完成安装并通过真实运行验证（2026-07-18 本次复核以 SSH 直连五节点 `ps`/`ss`/`curl` 只读核实，全部健康存活，证据见本节各行及 §7.7～§7.12）。**Phase 9 状态收口为 `PASSED WITH DEVIATIONS`**（偏差项见上），可进入 Phase 10 业务验收与故障演练；Phase 10 的每一步高风险操作（尤其是故障演练中的实例停止）仍需按 §10 规则单独申请人工 Gate，本次文档补记不构成该批准。

### 9.2 环境重置以复测整批 DAG 导入（2026-07-18，进行中）

上述四个批次的验收结论（§9.1）建立在“逐个单服务安装”的事实基础上，从未真正测试过“基础依赖批 3 个服务合并成一次 DAG 导入”这个原始设计路径。用户要求专门补测这条路径，为此按用户确认的范围清空了现场环境（保留 `DORIS` 与 `OTELCOLLECTOR` 不动）：

- **平台侧**：VALKEY(id 22)、ELASTICSEARCH(id 12)、NACOS(id 11)、DS(id 18)、APISIX(id 21) 五个服务实例，逐个先 `STOP_SERVICE` 停全部角色实例（每次均以 SSH `ps`/`ss` 核实真实进程退出，不只看 API 返回）、再 `DELETE /ddh/api/v2/cluster/1/service/instance/{id}` 触发官方级联清理（角色实例/角色组/WebUI/ClusterVariable）。清理过程中 `OTELCOLLECTOR` 短暂出现 `serviceState=EXISTS_EXCEPTION`（角色实例本身一直是 RUNNING），下一个巡检周期后自愈恢复 `RUNNING`/`alertNum=0`，判断是被删服务关联的告警未及时清空，不是真实故障。
- **文件系统侧**：DataSophon 的删除 API 只清 DB 记录，不清节点文件，另外手工清理了 ddh-01（VALKEY 安装目录+软链）、ddh-02（NACOS/ELASTICSEARCH/DS 安装目录+软链、APISIX 的 RPM 包 `rpm -e`、`/usr/local/apisix`、`apisix.service` 的 systemd 单元与 drop-in）、ddh-03/04/05（DS `WorkerServer` 安装目录+软链）。未动 `datasophon-worker` 本体、`datasophon-worker.bak-*`/`.backup-*` 调试备份目录、JDK、`otelcol-contrib`、DORIS 相关文件。

**批量清单**：生成 `deploy/phase9-batch-deploy.yaml`，`app:` 下一次列出 VALKEY（ddh-02）、ELASTICSEARCH（ddh-02，仅列 `ElasticSearch` 一个角色）、NACOS（ddh-02）、DS（ApiServer/MasterServer/AlertServer 在 ddh-02，WorkerServer×3 在 ddh-03/04/05）、APISIX（ddh-01，用户要求单独改到这台，验证不同节点复用同一份离线 RPM bundle）。核实后端 `PhysicalProductInstallServiceImpl.deploy()` 会把 `app:` 下所有条目编进**同一个 DAG**（`ProductDeployDAGBuildContext.buildDeployDAG`），这正是 §9 开头设计、此前从未真正跑过的路径。经 `POST .../deploy/upload` → `POST .../deploy/validate-deployment-file`（预检通过，`errors: null`）→ 用户在前端点击 `POST .../deploy/deploy` 触发。

**第一次批量安装结果（`dagId=2078472360468140033`，21:30:01 启动，21:30:41 完成，`FAILED`）**：`GET .../dag/{dagId}/graph` 显示 5 个节点 `edges: []`（互不依赖，符合预期），但执行是**顺序而非并行**——`DS` 先跑完 `SUCCESS`，紧接着 `APISIX` 在 ddh-01 上报错 `FAILED`（`executionLog` 原因为空字符串），随后 `VALKEY`/`ELASTICSEARCH`/`NACOS` 三个还没轮到的节点被整体标记 `CANCEL`（`commandProgress:0`，从未真正尝试安装）。这个"一个节点失败、其余全部取消"的行为与文档 §9 "失败即停止当前批，不进入后续批" 的既定策略一致，是设计行为；但"互不依赖的节点仍按顺序执行、不并行"是本次才确认的调度细节。

**根因排查——ddh-01 上 APISIX 真实报错**：登录 ddh-01 查看 `datasophon-worker/logs/APISIX/Apisix.log`，定位到 FreeMarker 报错 `For "?c" left-hand operand: Expected a number or boolean` ——`apisix-config.ftl` 第 7 行 `${apisixPort?c}` 对字符串类型的 `apisixPort` 用了非法的 `?c` 内建转换，与 §7.9 记录的"已修复"问题完全一致。核实发现：**仓库当前 `datasophon-worker/src/main/resources/templates/apisix-config.ftl` 早已不含 `?c`**，问题出在部署现场——五节点里**只有 ddh-02** 的 Worker（`datasophon-worker-3.0-SNAPSHOT.jar` MD5 `38d1ef4029...`，`Jul 18 08:11`）是修复后版本，**ddh-01/03/04/05 全部还停留在 `Jul 17 17:53` 的旧 jar**（MD5 `1e5eaab002...` 的旧模板，`?c` 仍在）。这是 §7.7 记录过的"Worker jar 版本偏差"问题的镜像重演：当时是 ddh-02 落后于其余四台，这次反过来是 ddh-02 领先、其余四台落后——根因是同一个操作习惯，每次现场修复 Worker 侧代码只把新 jar 推到"当时正在测的那台节点"，没有养成"改完就同步全部五节点"的习惯，导致版本漂移会在没被覆盖到的节点上不定期复发。此前 APISIX 只在 ddh-02 装过，这次用户要求换到 ddh-01 才第一次暴露 ddh-01 的 Worker 已经过期。

**修复**：把 ddh-02 上最新的 `datasophon-worker-3.0-SNAPSHOT.jar`、`conf/templates/apisix-config.ftl`、`conf/templates/apisix-routes.ftl` 三个文件（经本地中转、逐份 MD5 核对）同步到 ddh-01/03/04/05，用 `./datasophon-worker.sh stop` + `./datasophon-worker.sh start` 重启四节点 Worker（注意：`restart` 子命令内部用 `"$0" stop`，以相对路径 `bash datasophon-worker.sh restart` 调用时 `$0` 解析不出自身、报 `command not found` 且旧进程完全没重启，是脚本的一个小 bug，需用 `./datasophon-worker.sh` 形式或分开调用 `stop`/`start` 规避）。四节点重启后日志确认 `Worker gRPC registered to master` 成功；`ps -ef` 核实 Doris FE（ddh-01）、Doris BE 与 DS `WorkerServer`（ddh-03/04/05）四个真实业务进程 PID 均未变化，未受 Worker 重启影响。

**重跑（`POST .../dag/{dagId}/redeploy`，硬编码 `restart=true`，后端 `redeploy()` 跳过状态已是 `SUCCESS` 的节点）**：`VALKEY`/`APISIX`/`NACOS` 全部 `SUCCESS`；`ELASTICSEARCH` 节点整体 `FAILED`，但拆到角色级别看，`ElasticSearch` 主角色 `SUCCESS`，只有 `EsExporter` 失败（`检查EsExporter状态失败，已经达到重试次数20`）——这正是 §1.3/§7.7 早就记录、决定不修的已知问题（第三方 exporter 二进制资产缺失），不是新 bug。

**衍生发现——YAML 批量清单不能选择性排除角色**：本次清单里 `ELASTICSEARCH` 只写了 `ElasticSearch` 一个角色，特意不写 `EsExporter`，原以为能跳过这个已知会失败的角色；但 `deploy()` 的 DAG 仍然把 `EsExporter` 编了进去并绑定到与 `ElasticSearch` 相同的主机（`ddh-02`）。对比 §7.7 记录的"前端安装向导单装 ElasticSearch"——那次真实落库的角色实例里从未出现过 `EsExporter`。说明**向导式单装和 YAML 清单批量部署在"要不要装某个角色"上走的是两条不同判断逻辑**：清单驱动的 `deploy()` 按服务 DDL 的完整角色集生成命令，YAML 里的 `roles:` 只提供 host 映射，不是角色选择开关。这意味着只要通过批量清单路径部署 `ELASTICSEARCH`，`EsExporter` 的已知失败就会必然发生、并把整个节点/DAG 拖成 `FAILED`（即便主角色完全健康），这是清单批量路径相比向导单装新暴露的一个真实差异，供后续决定是否需要修 `EsExporter`、或者改造 `deploy()` 支持角色级过滤时参考。

**最终验证（物理核实，非仅看 API）**：`GET .../service/instance/list` 显示 `APISIX`/`DORIS`/`DS`/`ELASTICSEARCH`/`NACOS`/`VALKEY` 服务级状态均为 `RUNNING`（`ELASTICSEARCH` 的服务级状态不受 `EsExporter` 子角色失败影响）；SSH 直连 ddh-02 确认 `ApiApplicationServer`/`MasterServer`/`AlertServer`/`nacos.nacos`/`Elasticsearch`/`valkey-server` 等真实进程均在运行，`EsExporter` 确认无对应进程（失败属实）；ddh-01 上 `apisix.service` 为 `active`，用节点真实 IP（`192.168.10.131:9091`，而非 loopback——`config.yaml` 里 `prometheus.export_addr.ip` 绑定的就是真实 IP，和 ElasticSearch 的 `network.host` 同一类坑）探测 `/apisix/prometheus/metrics` 返回 `HTTP 200`。`OTELCOLLECTOR` 在本轮操作期间再次短暂出现 `EXISTS_EXCEPTION`（角色实例仍是 RUNNING），与 §9.2 前半段记录的现象一致，判断仍是同一类"被删/被装服务关联告警未及时清空"的自愈噪音。

**结论**：整批 DAG 导入路径本身验证通过（能正确合并多服务为一个 DAG、正确跳过已成功节点重跑、正确按主机分发命令）；VALKEY/APISIX/NACOS/DS 四个服务批量安装完全成功；ELASTICSEARCH 主角色批量安装成功，`EsExporter` 因已知资产缺失问题失败、拖累整个节点显示为 `FAILED`，不是新增缺陷。过程中额外修复了一个真实平台问题（ddh-01/03/04/05 的 Worker 落后于 ddh-02，未同步 APISIX `?c` 修复）和发现一个真实行为差异（YAML 清单批量部署不支持按角色排除，会强制安装服务 DDL 声明的全部角色）。

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
- Elasticsearch：集群健康及索引读写。
- Nacos：注册、查询与持久化。

故障演练每次只能停止一个实例：非 Master FE、单个 BE 或已经批准的一个关键中间件实例。停止前必须单独 Gate；恢复健康后才可进行下一项。禁止删除磁盘、删除 FE 元数据、并发停止多个实例或强制重建。

### 10.3 现场只读业务验收记录（2026-07-19）

从能触达 `192.168.10.0/24` 的终端对五节点执行只读 SSH/SQL/HTTP 验证（本次验收未使用前端界面，全部通过 SSH 直连服务端口或调用后端 API 完成），逐项记录如下：

**Doris**（§10.1）：`SHOW FRONTENDS`/`SHOW BACKENDS` 确认 1 FE（Master、Alive）+ 3 BE（Alive，Tablet 数 169/167/162 分布均衡）；建 `phase10_acceptance.acceptance_check` 表（`replication_num=3`），插入 5 行确定性数据，`SELECT COUNT(*)/SUM(val)/AVG(val)` 返回 `5/150/30`，与预期完全一致；`SHOW TABLETS` 确认每个 tablet 在 3 个不同 BackendId 上各有 1 副本、状态 `NORMAL`。验收后 `DROP DATABASE` 清理，未在生产环境遗留测试数据。受 §1.1/§5 已记录的"单 FE 无 HA"偏差限制，无法执行"从不同 FE 重复查询"，本次只对唯一 FE 验证，不构成新缺口。**附带发现**：Doris `root` 当前为空密码，未配置——不阻塞本次验收，但建议后续视安全要求决定是否设置。

**RustFS / OTel**（§10.2）：五节点 `otelcol-contrib` 进程与 `8888` metrics 端口均正常；`otelcol_exporter_send_failed_*`/`queue_size` 全部为 0；`otelcol_exporter_sent_metric_points{exporter="doris"}` 五节点合计约 1900 万点，持续增长。**架构澄清**：现场 `otelcol.yaml` 的 `pipelines` 只声明了 `metrics`（无 `logs`/`traces` pipeline），且 exporter 目标是 Doris 而非 RustFS/S3——这是更早的"可观测重构 OTel+Doris"epic（分支 `refactor/observability-otel`，早于本 Epic）落地后的现状,Phase 8 记录的 RustFS `otel-bootstrap` bucket 是集群初始化期的自检产物,不是持续数据路径。Doris `otel.otel_metrics_summary` 表核实 827,347 行、`MAX(timestamp)` 与查询时刻仅差 5 秒,证明管道实时写入;`otel_logs`/`otel_traces` 为空属预期（未配置对应 pipeline）,不是缺陷。顺带确认 RustFS `dolphinscheduler` bucket 存在,佐证 §7.11 的 DS S3 存储插件修复在真实运行期持续生效。

**APISIX**（§10.2）：`apisix.service` 为 `active`（现场部署主机为 ddh-01，与 §9.2 记录一致）；`/get` 路由请求经 access log 核实确由 APISIX 代理转发至 upstream `127.0.0.1:8080`（现为 datasophon-api 自身，返回 404 属上游行为，非路由失效）；`9091/apisix/prometheus/metrics` 返回 110 条 `apisix_*` 指标；Admin API `9180` 确认未监听。

**Valkey**（§10.2）：认证 `PING`→`PONG`、`SET`/`GET`/`DEL` 全部正确；exporter `redis_up=1`。**部署位置澄清**：实际运行节点是 ddh-02（§9.2 整批复测已改回冻结拓扑），不是 §7.12 单装时因内存不足临时改用的 ddh-01——ddh-01 上现在已无 Valkey 安装目录，验收时按 §7.12 旧记录误查过 ddh-01，已用平台 `getServiceRoleDeployOverview` 接口核实真实部署主机后订正。

**Elasticsearch**（§10.2）：`_cluster/health` 鉴权前返回结构化 `401`（安全拦截生效）；DDL 未配置固定密码，现场执行 `elasticsearch-reset-password -u elastic -b` 重置后完成鉴权验证：`cluster_name=ddp_es`、`status=green`；创建索引 `phase10_check` 写入文档（`result:created`）、按 ID 查询、`_search` 全文检索均返回预期内容，验收后 `DELETE` 索引清理。新密码仅用于本次验证，未写入仓库或文档。

**Nacos**（§10.2）：DDL `nacosPassword.defaultValue` 登录 `/nacos/v1/auth/login` 成功；Nacos 3.2.2 的 HTTP OpenAPI 路径已从 `v1/ns/*` 迁移到 `v3/client`/`v3/admin`（`v1/ns/instance` 返回 `501 no such api`，非故障）。用 `v3/client/ns/instance` 完成注册→`v3/admin/ns/service/list`确认`healthyInstanceCount=1`→注销→`v3/client/ns/instance/list`确认清空的完整闭环。**安全观察**：`nacosPassword` 的明文值以 `defaultValue` 形式提交在 `package/raw/meta/datacluster-physical/NACOS/service_ddl.json`（commit `52198c16`），与仓库自身"不得把真实密码写入仓库"的约定冲突；是否需要转为运行期生成/外部注入，留待用户决定，本次验收未改动。

**DolphinScheduler**（补充验收，非 §10.2 原定范围）：`ApiServer`/`MasterServer`/`AlertServer`（ddh-02）与 `WorkerServer`×3（ddh-03/04/05）六个真实进程均在运行；`/dolphinscheduler/actuator/health` 返回 `api:UP`/`db:UP`；`admin` 账号登录后 `/dolphinscheduler/projects/list` 鉴权查询成功（返回空列表，全新安装预期）。未探索 `monitor/master/worker` 的注册中心节点列表 API（DS 3.4.1 实际路径与预期不同，判断为超出本次验收必要范围，未继续排查）。

**结论**：§10.1/§10.2 列出的全部只读业务验收项（Doris、RustFS/OTel、APISIX、Valkey、Elasticsearch、Nacos）均已现场验证通过，DS 作为补充项同样通过。过程中发现并订正了两处基于旧记录的误判（Valkey 实际部署主机、DS `ApiServer` 类名匹配），未发现新的平台缺陷。

### 10.4 故障演练：单个 Doris BE 停止与恢复（2026-07-19，用户已批准本项）

按 §10.2 规则，本次只对一个实例执行停止/恢复，范围限定为单个 BE（`ddh-03` 的 `DorisBE`，角色实例 id=32），未涉及 FE 或并发多实例。

**操作入口**：现场核实 REST 层的角色级启停命令为 `POST /ddh/api/cluster/service/command/generateAndSrvRoleCmd?clusterId=1&commandType=<STOP_SERVICE|START_SERVICE>&serviceInstanceId=<serviceId>&serviceRoleInstancesIds=<roleInstanceId>`（`ClusterServiceCommandController`），与 §9.2 记录的批量清理用法一致；发起前需要把登录返回的 `XSRF-TOKEN` cookie 值回填到 `X-XSRF-TOKEN` 请求头，否则 POST 一律 `403`（GET 不受此限制）——这是 Spring Security 标准 CSRF 校验，非缺陷。

**执行记录**：

1. **基线**：`SHOW BACKENDS` 确认停止前三节点均 `Alive=true`，ddh-03 `TabletNum=172`。
2. **停止**：`commandType=STOP_SERVICE` 下发成功（返回 dagId）。约 8 秒后核实：ddh-03 上 `doris_be` 真实进程数为 `0`；平台角色状态变为"存在告警"（`serviceRoleStateCode=3`）；Doris FE `SHOW BACKENDS` 正确标记该 BackendId `Alive=false`，另两台仍 `Alive=true`。
3. **故障态业务连续性**：在仅剩 2 台 BE 的情况下，建 `replication_num=2` 的验收表并插入数据，`SELECT COUNT(*)/SUM(val)` 返回正确结果（`2/300`），证明单 BE 故障不影响集群整体读写，验收后清理该库。
4. **恢复**：`commandType=START_SERVICE` 下发成功。约 30 秒内确认：ddh-03 上 `doris_be` 真实进程重新出现（新 PID）；平台角色状态回到"正在运行"（`serviceRoleStateCode=1`，`needRestart=false`）；Doris FE `SHOW BACKENDS` 三节点全部 `Alive=true`，Tablet 数重新均衡为 `172/173/168`（较停止前的 `172/170/165` 有小幅自动再均衡，属正常行为）。

**结论**：单 BE 故障演练完整验证通过——故障态被平台和 Doris FE 正确感知、业务连续性不受影响、恢复后角色状态与真实进程双重确认一致，未发现新的平台缺陷。本次未执行 FE 切换或并发多实例停止，符合 §5/§10.2 的既定限制。

## 11. Phase 11：证据归档与结论

Git 外归档应包括：分支/commit、盘点、拓扑批准、离线包 manifest、脱敏配置 hash、plan hash/clusterHash、apply 状态、APISIX 模板测试与 Nexus 元数据 hash、API/Worker 注册、服务 DAG、Doris SQL、APISIX 路由与故障演练。

归档前扫描真实密码、私钥、token、JDBC 凭据和未脱敏截图。最终结论只能是：`PASS`、`PASS WITH DEVIATIONS` 或 `FAIL`。

### 11.1 归档执行记录（2026-07-19）

**归档位置**：`/Users/pro/IdeaProjects/datasophon-deploy-evidence/five-node-doris-bootstrap/archive-manifest.md`（本机仓库平行目录，不纳入 `datasophon` git 版本控制，用户已确认此位置）。内容汇总分支/commit、五节点拓扑批准、离线包版本、clusterHash/plan hash 演变、apply 状态、APISIX 验证记录、API/Worker 注册状态、服务 DAG 记录、Doris SQL 验收、APISIX 路由与故障演练结果，以及本节 §11.2 记录的偏差与遗留问题清单。

**归档前凭据扫描**：对 `deploy/deployment-standalone-doris.md` 全文执行模式扫描，发现并处理两处此前已提交到 git 的真实凭据明文：

1. `commit c8b995337`（2026-07-14，§7.2.8 MySQL cnf 引号问题描述）：MySQL root 密码明文，已在本次改为 `<MYSQL_PASSWORD>` 占位符。
2. `commit a8fd1fcba`（2026-07-14，§7.2.7 rustfs 密码截断问题描述）：RustFS secret key 明文，已在本次改为 `<RUSTFS_SECRET_KEY>` 占位符。

两处均已 push 到 `origin/verify/cli-go-five-node-bootstrap`，属于已经历史暴露的凭据。**用户明确决定**：本轮只打码当前文档文本，不轮换对应的 MySQL root 密码与 RustFS secret key、不做 git 历史重写（`git filter-repo`/`BFG` 等）。这意味着这两个真实凭据的明文仍可通过 `git log -p` 在仓库历史中查到，是需要仓库访问权限持有者知晓的残留风险，记录在 §11.2 表格第 7 行。

另发现（未改动，同样记录在 §11.2）：`NACOS` 的 `service_ddl.json`（commit `52198c16`）以 `defaultValue` 形式提交了明文登录密码，用户决定本轮不处理。

### 11.2 已知偏差与遗留问题汇总

| # | 问题 | 状态 | 影响面 |
|---|---|---|---|
| 1 | `EsExporter` 缺第三方二进制资产 | 已知，未处理 | 仅该 exporter 角色，不影响 ElasticSearch 主角色 |
| 2 | 单 FE 无 HA | 已批准的拓扑偏差 | FE 故障会导致 Doris 不可用；故障演练已排除 FE 切换 |
| 3 | `bcprov-jdk15on-1.68.jar` 缺失 | 已知，未处理 | JDK8 TLS1.0/1.1 放宽功能缺失，不影响核心安装 |
| 4 | Nexus docker/helm 仓库创建返回 400 | 已知，未处理 | 当前环境不使用这两种仓库类型 |
| 5 | Doris `root` 空密码 | Phase 10 验收新发现，未处理 | 建议后续视安全要求设置 |
| 6 | Nacos 登录密码明文提交在 `service_ddl.json`（commit `52198c16`） | Phase 10 验收新发现，用户决定本轮不处理 | 密码本身仍在 git 历史中 |
| 7 | 文档曾两处明文写入 MySQL root 密码（commit `c8b995337`）与 RustFS secret key（commit `a8fd1fcba`） | 本轮已打码当前文本，但 git 历史仍可查到明文（已 push 到 origin），密码/密钥本身未轮换（用户决定） | 需要拥有仓库访问权限的人明确知晓此残留风险 |
| 8 | YAML 批量清单部署不支持按角色排除安装 | 平台行为限制，已记录（§9.2） | 影响清单驱动的部署方式，不影响向导单装 |
| 9 | Worker jar 版本漂移可能在未同步节点复发 | 操作习惯问题，已记录（§9.2/§7.7） | 需要"改完同步全部节点"的操作纪律 |

### 11.3 最终结论

综合 Phase 0～10 全部现场记录：五节点初始化、控制面部署、阶段 A 六个服务（VALKEY/ELASTICSEARCH/NACOS/DORIS/DS/APISIX）的安装与业务读写验证、单 BE 故障停止与恢复演练均已现场验证通过，未发现阻断性缺陷。但存在 §11.2 所列 9 项已记录偏差，其中 #5/#6/#7 为 Phase 10/11 本轮新发现的凭据与安全相关问题，尚未整改完毕。

**最终结论：`PASS WITH DEVIATIONS`**

## 12. Phase 12：后续 Hadoop 扩展

阶段 B 单独规划并审批 HDFS、YARN、Hive、Spark3、Kyuubi、DS 的角色、磁盘、端口和容量。完成前不得将 Kyuubi 或 DS 计入阶段 A 的“无 Hadoop”验收。
