# 五节点无 Hadoop Doris 集群部署与验收手册

> 范围：使用 `datasophon-cli-go` 初始化五台虚拟机并创建 DataSophon 控制面基础设施；随后从 DataSophon 前端创建集群、完成 Worker 与 OTel Collector 集群初始化，最后通过前端导入 DAG 安装剩余阶段 A 组件。所有现场配置、plan 和证据均保存在 Git 外。
>
> 凭据规则：本文只使用 `<ROOT_PASSWORD>`、`<MYSQL_PASSWORD>`、`<RUSTFS_ACCESS_KEY>` 等占位符。不得把真实密码、私钥、token、JDBC 凭据或未经脱敏的截图写入仓库、终端录屏或验收报告。
>
> **文档范围说明（2026-08-16 精简）**：本文只保留**设计、结论、当前状态、下一步计划**四类内容。Phase 0～11 的逐条现场执行记录与排错日志（精简前旧编号 §3～§10 的各小节，约 890 行）已删除——其中的缺陷均已修进代码并有对应 commit，需要考古时用 `git log -p -- deploy/deployment-standalone-doris.md` 查阅。

---

## 1. 设计

### 1.1 节点清单与冻结拓扑

拓扑于 Phase 2 冻结为 **`1 FE + 3 BE`**。

| Hostname | IP | 基础设施 / 控制面 | Doris |
| --- | --- | --- | --- |
| `ddh-01` | `192.168.10.131` | API/Master、Nexus、MySQL、NTP Server、RustFS、Worker、OTel Collector | 单 FE，不部署 BE |
| `ddh-02` | `192.168.10.132` | Worker、OTel Collector、NTP Client；阶段 A 中间件（`VALKEY`/`ELASTICSEARCH`/`NACOS`/`APISIX`）主要承载节点 | 不部署 |
| `ddh-03` | `192.168.10.133` | Worker、OTel Collector、NTP Client | BE |
| `ddh-04` | `192.168.10.134` | Worker、OTel Collector、NTP Client | BE |
| `ddh-05` | `192.168.10.135` | Worker、OTel Collector、NTP Client | BE |

**节点基线**：五台均为 `openEuler 22.03 LTS-SP3 x86_64`、`16 vCPU / 30 GiB RAM / 4 GiB swap`；系统盘 `/dev/vda3` 可用约 `37 GiB`；独立数据盘 `/dev/vdb` 已格式化 `ext4`、UUID 持久挂载至 `/data`，每台可用 `466.1 GiB`。

**已知设计偏差**：单 FE 不具备 Doris 元数据高可用，`ddh-01` 或 FE 故障将导致 Doris 不可用。故障演练不执行 FE 切换，结论不得表述为「Doris 高可用验收通过」。

### 1.2 三层部署边界

1. **CLI 节点与控制面基础设施层**：`datasophon-cli-go` 初始化 OS、hostname、`/etc/hosts`、SSH、JDK、Nexus、MySQL、RustFS、NTP、系统依赖与离线包环境，创建 MySQL 数据库、上传 package，并创建/启动 `datasophon-api`。
2. **DataSophon 前端集群初始化层**：API 启动后，从前端新建集群、选择框架、配置五节点清单；前端为每个节点安装 Worker 与 OTel Collector，并完成集群初始化健康检查。
3. **DataSophon 前端 DAG 层**：仅在集群初始化通过后，从前端导入 DAG 安装其余组件；Doris FE/BE 与其余服务角色均不得写入 CLI YAML。

`-t hadoop` 仅表示 CLI 的非 Kubernetes 物理机 scope，不代表本阶段安装 HDFS、YARN 或任何 Hadoop 服务。

**CLI 执行主机约束**：`create cluster plan/apply` 在 `setup()` 阶段会在**运行 CLI 的本机**校验 `datasophonPath`/`installPath`，同一路径随后又通过 SSH 传给各节点。因此 CLI 必须在集群节点自身（本次为 `ddh-01`）上运行，不能从外部跳板机以 `--installPath /data` 执行。

**CLI YAML 约束**：loader 启用严格字段解析，不得添加 `doris`、`feNodes` 或任何服务 DAG 专用字段。`clusterHash` 是解析后 `ClusterConfig` JSON 的 SHA-256 前 16 位，任意语义变更必须重新 plan 并重走审批。

### 1.3 服务范围

| 阶段 | 服务 | 计入验收 |
| --- | --- | --- |
| 集群初始化 | `OTELCOLLECTOR`（前端逐节点安装；CLI 部署 RustFS 作为其 S3 兼容存储） | 是 |
| 阶段 A DAG | `DORIS`、`VALKEY`、`ELASTICSEARCH`、`NACOS`、`DS`、`APISIX` | 是 |
| 阶段 B | `KYUUBI`、`SPARK3`、`HIVE`、`HDFS`、`YARN` 等 | 否，另行立项 |

- **`ZOOKEEPER` 已从阶段 A 移除**（2026-07-17 决定）：DolphinScheduler 改用 MySQL 作为注册中心（`registry.type=jdbc`）。
- **`DS` 已从阶段 B 移入阶段 A**（2026-07-17 决定）：`service_ddl.json` 的 `dependencies` 清空为 `[]`，核心四角色（ApiServer/MasterServer/WorkerServer/AlertServer）不再需要 Spark/Hive/HDFS/ZooKeeper。Spark/Hive 任务插件仍可选。
- **`APISIX`** 使用 `3.17.0` Standalone 数据面模式（`role: data_plane` + `config_provider: yaml`），不依赖 etcd，关闭 Admin API，开启 `9091` Prometheus export server。不含路由 CRUD、热更新控制面或 etcd 兼容分支。

DAG 导入按四批推进，失败即停止当前批：基础依赖批（`VALKEY`/`ELASTICSEARCH`/`NACOS`）→ Doris 批 → 调度批（`DS`）→ 网关批（`APISIX`）。OTel Collector 不在此阶段重复导入。

### 1.4 `/data` 容量预算

CLI 参数固定为 `datasophonPath=/data/datasophon`、`installPath=/data`。

| 节点 | 用途 | 预算 |
| --- | --- | ---: |
| `ddh-01` | `/data/nexusDir`（Nexus，含 blob） | 80 GiB |
| `ddh-01` | `/data/rustfs/data`（由 `--installPath` 推导，不依赖 `rustfs.config.volumes`） | 120 GiB |
| `ddh-01` | `/data/doris/fe/meta` | 20 GiB |
| `ddh-01` | DataSophon/安装包/临时文件 | 50 GiB |
| `ddh-02` | 阶段 A 中间件数据+软件目录（各中间件独立子目录） | ≤300 GiB |
| `ddh-02` | 日志/升级暂存 | 50 GiB |
| `ddh-03/04/05` | `/data/doris/be/storage` | 各 350 GiB |
| `ddh-03/04/05` | 软件/日志/暂存 | 各 40 GiB |

MySQL datadir 仍是 `/var/lib/mysql`（系统盘），`--installPath=/data` 不会迁移；验证期预算 8～10 GiB 并监控系统盘剩余。`cluster plan` 只处理 `rustfs.nodes[0]`，RustFS 固定单节点 `ddh-01`。禁止让 RustFS 与 Doris 数据在同一磁盘无预算共置。

### 1.5 内存预算

单 FE `-Xms4g -Xmx4g`；`ddh-03～05` BE 统一 `mem_limit=14G`（**禁止使用默认 `100%`**）；`ddh-01` 的 API/Nexus/MySQL/RustFS 各预留约 2 GiB，并保留至少 8 GiB 给 OS/页缓存/native memory。

### 1.6 端口矩阵

| 服务 | 端口 |
| --- | --- |
| SSH | `22/TCP` |
| DataSophon API / Master gRPC / Worker gRPC | `8080/TCP`、`18081/TCP`、`18082/TCP` |
| MySQL / Nexus / NTP / RustFS | `3306/TCP`、`8081/TCP`、`123/UDP`、`9040/TCP`、`9041/TCP` |
| Doris FE | `18030`、`9020`、`9030`、`9010/TCP` |
| Doris BE | `9060`、`18040`、`8060`、`9050/TCP` |
| APISIX / metrics | `9080/TCP`、`9091/TCP` |

Doris 网络优先级固定 `fe_priority_networks=192.168.10.0/24`、`be_priority_networks=192.168.10.0/24`。

### 1.7 Doris 4.1.3 升级顺序设计

目标版本固定 `4.1.3`，拓扑保持 `1 FE + 3 BE`。平台侧已实现的升级 DAG 契约：

- **顺序**：逐台 BE → FE 最后（BE-first / FE-last）。
- **门禁**：每个节点完成后经 FE 校验 `Alive` 与目标版本，任一失败立即熔断，不继续下一节点。
- **调度开关**：升级开始前将 `disable_balance`、`disable_colocate_balance`、`disable_tablet_scheduler` 置 `true`；无论成功或熔断，结束路径必须恢复为 `false`。
- **Worker 行为**：软链切换前迁移 `fe/be/custom_lib`。
- **`UPGRADE_SERVICE` 特例**：API 重启后 `UseRoleGroup_<serviceInstanceId>` 内存缓存可能缺失，此时读取角色实例持久化的 `roleGroupId`；首次安装契约不变。

### 1.8 变更与演练规则

- 故障演练**每次只能停止一个实例**（非 Master FE、单个 BE，或已批准的一个关键中间件实例）。停止前必须单独审批，恢复健康后才可进行下一项。
- 禁止删除磁盘、删除 FE 元数据、并发停止多个实例或强制重建。
- Gate 状态只能取 `NOT STARTED`、`IN PROGRESS`、`BLOCKED`、`PASSED`、`FAILED`、`ROLLED BACK`。

---

## 2. 结论：阶段 A（2026-07-19 归档）

### 2.1 Phase 状态一览

| Phase | 目标 | 状态 |
| --- | --- | --- |
| 0 | 五节点只读盘点与数据盘准备 | PASSED |
| 1 | APISIX Standalone 产品适配与现场安装 | PASSED |
| 2 | 冻结拓扑、容量与服务角色 | PASSED |
| 3 | 网络、时间、磁盘与离线包预检 | PASSED WITH DEVIATIONS |
| 4 | CLI 配置生成与五节点审阅 | PASSED |
| 5 | CLI plan 生成与人工审批（clusterHash `25ad4ff34cff4283`） | PASSED |
| 6 | CLI apply 基础环境初始化（34 Step：24 completed + 10 skipped + 0 failed） | PASSED |
| 7 | 基础环境、RustFS 与 API 健康（DB 迁移至 2.2.3） | PASSED |
| 8 | 前端集群初始化：五节点 Worker 与 OTel Collector | PASSED |
| 9 | 前端导入阶段 A 服务 DAG（六服务） | PASSED WITH DEVIATIONS |
| 10 | 阶段 A 业务验收与故障演练（单 BE 停止/恢复） | PASSED WITH DEVIATIONS |
| 11 | 阶段 A 证据归档与结论 | PASSED WITH DEVIATIONS |
| 12 | 阶段 B Hadoop 扩展 | BLOCKED（单独立项） |
| 13 | GRAVITINO 元数据服务接入 | PASSED |
| 14 | Gravitino catalog 创建：Paimon fs / Paimon S3 / Doris | PASSED |
| 15 | Gravitino 血缘权威端替换与 Datasophon 查询代理 | PASSED |
| 16 | 前端血缘图/链路图容器高度修复部署 | PASSED |
| 17 | GRAVITINO 监控看板补全（20 面板） | PASSED WITH DEVIATIONS |

> 归档包位于 `/Users/pro/IdeaProjects/datasophon-deploy-evidence/five-node-doris-bootstrap/archive-manifest.md`（本机平行目录，不纳入本仓库版本控制）。

### 2.2 已知偏差与遗留问题

| # | 问题 | 状态 | 影响面 |
|---|---|---|---|
| 1 | `EsExporter` 缺第三方二进制资产 | 已知，未处理 | 仅该 exporter 角色，不影响 ElasticSearch 主角色 |
| 2 | 单 FE 无 HA | 已批准的拓扑偏差 | FE 故障会导致 Doris 不可用；故障演练已排除 FE 切换 |
| 3 | `bcprov-jdk15on-1.68.jar` 缺失 | 已知，未处理 | JDK8 TLS1.0/1.1 放宽功能缺失，不影响核心安装 |
| 4 | Nexus docker/helm 仓库创建返回 400 | 已知，未处理 | 当前环境不使用这两种仓库类型 |
| 5 | Doris `root` 空密码 | 未处理 | 建议后续视安全要求设置 |
| 6 | Nacos 登录密码明文提交在 `service_ddl.json`（commit `52198c16`） | 用户决定本轮不处理 | 密码仍在 git 历史中 |
| 7 | 文档曾两处明文写入 MySQL root 密码（commit `c8b995337`）与 RustFS secret key（commit `a8fd1fcba`） | 当前文本已打码，但 git 历史仍可查到明文（已 push 到 origin），凭据未轮换（用户决定） | 需仓库访问权限持有者明确知晓此残留风险 |
| 8 | YAML 批量清单部署不支持按角色排除安装（`deploy()` 按服务 DDL 全量角色生成命令） | 平台行为限制，已记录 | 影响清单驱动部署，不影响前端向导单装 |
| 9 | Worker jar 版本漂移可能在未同步节点复发 | 操作习惯问题 | 需要「改完同步全部节点」的操作纪律 |
| 10 | `control.sh`（含 OTELCOLLECTOR）无并发保护，共享角色被密集 restart 会导致 pid 文件与真实进程错位 | 已修复 `OTELCOLLECTOR` 脚本模板（加 `flock`），**已装节点需重新分发脚本才生效，本次未执行分发**；其余复用同一 `control.sh` 模式的服务未逐一排查 | 角色状态误报 STOP，数据链路实际健康 |
| 11 | 节点间 UDP/123 被主机外部网络策略阻断，chrony 无持续纠偏 | 见 §3.2，用户批准作为现场偏差继续 | 时间会重新漂移；最终验收必须保留该偏差 |

### 2.3 最终结论

阶段 A（Phase 0～10）：五节点初始化、控制面部署、六个服务（VALKEY/ELASTICSEARCH/NACOS/DORIS/DS/APISIX）的安装与业务读写验证、单 BE 故障停止与恢复演练均已现场验证通过，未发现阻断性缺陷。

**阶段 A 最终结论：`PASS WITH DEVIATIONS`**（偏差见 §2.2）。

> 阶段 A 的结论不受 §3 Doris 4.1.3 升级进展影响；升级是在已验收基线之上的独立变更。

---

## 3. 当前状态：Doris 4.1.3 升级（2026-08-14）

### 3.1 门禁状态

| 门禁 | 状态 | 结论 |
|---|---|---|
| 平台代码与元数据 | PASSED | DORIS DDL/manifest 已切 4.1.3；升级 DAG 契约见 §1.7；API/OTel 定向测试 18 项、Worker 1 项通过 |
| NTP | PASSED WITH DEVIATION | 已一次性校准系统时间与 RTC，最大绝对偏差 `0.031347s`（≤1 秒）；但 chrony 无持续纠偏能力，见 §3.2 |
| 官方包与兼容性 | PASSED | 包校验、Nexus 上传、隔离 FE 加载生产 metadata 热备份 + MySQL 协议握手均通过 |
| 平台部署与逐节点升级 | **FAILED** | ddh-03/ddh-04 已升至 4.1.3；ddh-05 触发 BE 崩溃循环，守卫熔断，见 §3.2 |
| 业务验收 | BLOCKED | 升级 DAG 强制门禁失败，4.1.3 集群健康、三副本表、OTel、Workload Group、物化视图/Job、平台查询与日志验收均未执行 |

### 3.2 现场事实

**节点版本现状**：`ddh-03`、`ddh-04` 已运行 `doris-4.1.3-rc02-7126cf65d96` 且 FE 确认 Alive；`ddh-05` 仍为 `doris-4.0.6-rc02-1663f25c16f` 且**当前处于停止状态**；FE（`ddh-01`）仍为 `4.0.6`，未升级。升级 DAG `2088204713310441473` 已标记 `FAILED`，三个调度开关已恢复为 `false`。

**崩溃循环根因**：BE 在持续 Stream Load 下 SIGSEGV，栈均为 `tablet_writer_add_block → streamvbyte_decode → DataTypeNumberBase::deserialize`。写入源定位到 `db=otel, tbl=otel_logs`（label 前缀 `open_telemetry_otel_otel_logs_`）。关键事实是**该崩溃与版本无关**：同一批写入中 `ddh-04`（4.1.3）与 `ddh-05`（4.0.6）在相邻 1 秒内同时崩溃，ddh-03 日志同时记录向两节点的 RPC EOF。因此只要写入持续，恢复任一 BE 都会重新进入崩溃循环。

**写入源已识别三处**（OTel 并非唯一）：

1. 五节点 OTel Collector —— **已通过各自 `control.sh stop` 全部停止**，进程不存在、`8888` 端口关闭。
2. Flink T9 作业 —— Collector 停止后仍于 `18:15:28`、`18:16:30` 向 `lineage_flink_verify.dwd_odr_oper_surgery_records_full_hourly` 发起 Stream Load。**未处理**。
3. FE 内置 `AuditLoader` —— 约每 60 秒写入 `__internal_schema.audit_log`。**未处理**。

**NTP 偏差**：四台 client 对 ddh-01 均为 `^?`、reach 0、`Reference ID=00000000`、`Leap status=Not synchronised`。ddh-01 chronyd 正常监听 `123`、自身 `Stratum=10`，但 `chronyc clients` 无任何 client；UDP/9123 临时探测同样无报文到达，firewalld inactive、iptables ACCEPT——证据指向节点间 UDP 被**主机外部网络策略**阻断。已用 SSH 以 ddh-01 为基准执行 `date -s` + `hwclock --systohc` 完成一次性校准（偏差 `0.031347s`），用户批准以此偏差继续升级，但不能视为 NTP 门禁通过。

### 3.3 回滚与复现资产

| 资产 | 位置 / 值 |
|---|---|
| FE metadata 热备份（升级前，1.2 GiB） | `ddh-01:/data/doris/fe/doris-meta.backup-before-4.1.3-20260814T1716` |
| metadata 备份副本 | `ddh-02:/data/doris-compat-4.1.3/meta`（隔离测试用） |
| 升级前 checkpoint | `image.20662351` |
| 4.1.3 x64 官方包 SHA-512 | `265ea3324ac9db59e97bfcca452d287ae8f48f23dbdc010cafa8b32667a69adff7d4251d06d22dbf9918dc74af5b12f8655a81b6cb99152e60e45246167beab6`（`3614068121` bytes） |
| 升级前 OTel 基线 | `otel_metrics_services=14`；Gauge/Sum/Histogram/Summary 四表行数 `529208716` / `936337106` / `13658505` / `20297981`，最新时间均到 `2026-08-14 17:16:47` |
| chrony 配置备份（四台 client） | `/etc/chrony.conf.bak-doris413-20260814` |

**当前结论：`FAILED`。** 在恢复方案获批、全节点恢复并重新完成业务验收前，不得写成升级完成。

---

## 4. 下一步计划

按顺序执行，每一步需单独批准；任一步失败即停止并重新评估。

| # | 动作 | 状态 | 说明 |
|---|---|---|---|
| 1 | 对 Flink T9 作业执行 savepoint 后 cancel | **待批准** | 消除 §3.2 写入源 2；savepoint 是保证血缘验证作业可恢复的前提 |
| 2 | 临时关闭动态变量 `enable_audit_plugin` | **待批准** | 消除 §3.2 写入源 3；升级完成后须恢复 |
| 3 | 在确认零写入的窗口内启动 `ddh-05`（4.0.6），经 FE 确认 Alive | 待执行 | 形成完整的混合版本集群，为续跑 DAG 提供前置条件 |
| 4 | 续跑升级 DAG：升 `ddh-05` BE → 最后升 FE（`ddh-01`） | 待执行 | 沿用 §1.7 熔断契约；失败即停，不自动回退已升级节点 |
| 5 | 恢复写入：启动五节点 OTel Collector、恢复 `enable_audit_plugin`、从 savepoint 恢复 T9 | 待执行 | 恢复后须比对 §3.3 OTel 基线，确认无数据缺口 |
| 6 | 执行业务验收 | BLOCKED | 4.1.3 集群健康、三副本表读写、OTel 链路、Workload Group、物化视图/Job、平台查询与日志 |
| 7 | 放通节点间 UDP/123 并恢复 chrony 持续纠偏 | 遗留 | 需协调主机外部网络策略；不阻塞 1～6，但最终验收必须保留该偏差记录 |

### 阶段 A 遗留跟进（与升级独立）

| # | 动作 | 关联 |
|---|---|---|
| A | 向已安装节点重新分发含 `flock` 的 `control.sh`（走 Nexus 上传 + Worker 重装/更新配置） | §2.2 #10 |
| B | 排查其余服务是否存在同类「无锁 pid 文件 + 共享角色被多处 restart」模式 | §2.2 #10 |
| C | 视安全要求设置 Doris `root` 密码 | §2.2 #5 |
| D | 决定是否轮换已在 git 历史暴露的 MySQL root 密码与 RustFS secret key | §2.2 #7 |

## 5. 后续：阶段 B Hadoop 扩展

阶段 B 单独规划并审批 HDFS、YARN、Hive、Spark3、Kyuubi 的角色、磁盘、端口和容量。完成前不得将 Kyuubi 计入阶段 A 的「无 Hadoop」验收。

---

## 6. Flink 1.20.x OTLP metrics reporter 验证（2026-08-23）

本节记录 `datasophon-flink-metrics-otel` 的独立现场验证，不改写 §3～§4 的历史升级过程。验证时 Doris 当前状态为 `1 FE + 3 BE` 全部 Alive，版本均为 `doris-4.1.3-rc02-7126cf65d96`。

### 6.1 验证拓扑与隔离措施

| 项 | 验证值 |
|---|---|
| Flink | `ddh-02`，Flink `1.20.4` standalone 临时集群 |
| Reporter | `datasophon-flink-metrics-otel/target/flink-metrics-otel-1.20-1.0.0-SNAPSHOT.jar` |
| Reporter SHA-256 | `75ac35b2a8effd1874386bc268f65ce5acf86c079d24b11eaef5c95bd4243599` |
| OTLP 接收端 | `ddh-02:4317`，OTel Collector `0.156.0` |
| Doris 查询端 | `ddh-01:9030`，数据库 `otel` |
| 唯一资源标识 | `service.name=flink-120-otel-final-20260823-1701` |
| 隔离端口 | REST `18091`；RPC/data/blob `16122`～`16125` |

临时集群从现有 Flink 1.20.4 安装目录复制，但使用独立配置、PID、日志、checkpoint 和端口；未停止或修改现有 `flink-cluster-cdc`。Reporter 以 Flink plugin 方式放入 `plugins/metrics-otel/`，JobManager 与 TaskManager 日志均确认以 5 秒周期加载 `org.apache.flink.metrics.otel.OpenTelemetryMetricReporter`。

### 6.2 验证结果

| 检查项 | 结果 | 证据 |
|---|---|---|
| Flink plugin SPI 加载 | PASSED | JobManager/TaskManager 均创建 `metrics-otel` plugin loader，无 `ClassNotFoundException` / `NoClassDefFoundError` |
| OTLP/gRPC → Collector → Doris | PASSED | 唯一 `service.name` 最终写入 Gauge `1033` 行、Sum `84` 行 |
| Counter/Meter 语义 | PASSED | Sum 行为 `aggregation_temporality=Delta`、`is_monotonic=1` |
| 指标命名 | PASSED | Doris 可见 `flink.jobmanager.*`、`flink.taskmanager.*` 点分逻辑作用域名称 |
| 作业属性 | PASSED | 内置 `StateMachineExample` 作业 `7aec49673f177729fecd40cb28d42c83` 产生 `207` 条含 `job_id` 的 Gauge 行，同时包含 `job_name`、`host` |
| 导出错误 | PASSED | 临时集群日志无 OTLP 导出失败；Collector 日志无该 `service.name` 对应 error/warn |
| 兼容性构建 | PASSED | Flink `1.20.0`、`1.20.4`、`1.20.5` 均完成 `clean verify` |

验证完成后已取消示例作业、停止临时集群，并删除 `/data/install_datasophon/flink-otel-final-20260823-1701`。临时端口全部释放；现有 Flink 1.20.4 CDC 集群 REST `8081` 仍为 1 个 TaskManager、4 个可用 slot，原 JobManager/TaskManager 进程保持运行。Doris 中带唯一 `service.name` 的验收数据作为链路证据保留。

**结论：`PASSED`。** Flink 1.20.x reporter 可按 Flink 2.0 的 metrics OTLP 契约运行，并在本环境完成 Flink → OTLP/gRPC → OTel Collector → Doris 的真实链路验证。
