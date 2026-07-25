# CLI 引导期基础设施监控采集实施计划(base-otel-collector + mysqld_exporter)

> 状态:已实现并通过代码级验证；五节点真机端到端验收仍需在目标环境执行。本文同时作为实现说明与验收清单。

## Context(为什么做这件事)

部署手册 `deploy/deployment-standalone-doris.md` 里,MySQL / Nexus / RustFS 由 `datasophon-cli-go` 在**控制面自身还没起来的引导期**(Phase 6)通过 SSH 直接安装。它们没有 `service_ddl.json`、没有纳管角色,数据库里也没有对应的 `ClusterServiceRoleInstanceEntity` 行。

而现有 metrics 采集完全依赖 Master 端 `OtelScrapeConfigBuilder`——它只遍历"通过 DAG 装的纳管角色"来生成 otelcol scrape job,结构上**根本看不见这三个引导期组件**。这是一个典型的控制面自举悖论:被编排系统依赖的基础设施,先于编排系统存在。

三者的监控看板(`docs/monitoring/`)其实都已 `🎉落地完成`,唯一缺的是**采集这一环**。本任务由 CLI 在引导期自己装一个**独立的、非纳管的 otel-collector**,专门采集这三类基础设施指标;并在 MySQL 节点旁挂 `mysqld_exporter`。

**已确认的两个关键决策:**
- **Sink = RustFS S3 兜底**:CLI 引导期 Doris 尚未安装(Phase 9 才由前端 DAG 装),唯一可用下游是 CLI 刚装好的 RustFS(S3)。collector 用 `awss3` exporter 写 S3,与可观测 epic 的"引导期 staged exporter → S3 兜底"完全一致。**S3→Doris 回灌沿用 epic 现有 `awss3receiver` 机制,不在本次 CLI 改动范围**(单独跟进)。
- **位置可配、绑节点 IP**:collector 装在可配置的 `node`;`mysqld_exporter` 的 web 端点绑节点 IP,collector 按节点 IP 抓取,支持跨节点。实际单节点部署里 registry/mysql/rustfs 都在 ddh-01,自然收敛为本机。

## 采集拓扑

```
                       ┌─────────────────────────────────────────┐
  MySQL :3306 ──(local)──> mysqld_exporter <mysqlNodeIP>:9104 ──┐  │
                                                                 │scrape(prometheus receiver)
  Nexus <regNodeIP>:8081 /service/rest/metrics/prometheus ──────┤(basic_auth: nx-metrics-all)
   (需 nx-metrics-all 账号 + Basic Auth)                          │
                                                                 ▼
  RustFS ──OTLP/HTTP push──> base-otel-collector <colNodeIP>:4318 ──awss3 exporter──> RustFS S3
   (RUSTFS_OBS_ENDPOINT=http://<colNodeIP>:4318)                  (marshaler: otlp_json)
```

三个目标随 DAG 陆续起来;prometheus 抓取容忍初始 target down,rustfs OTLP push 失败自动重试,collector→S3 失败落 `file_storage` 磁盘队列。无强制启动顺序耦合。

---

## 变更清单

### A. 配置 schema(`internal/config/`)

**A1. 新增顶层配置块** `internal/config/global.go`,并在 `internal/config/cluster.go` 的 `ClusterConfig`(行 26-37)登记字段 `BaseOtelCollector BaseOtelCollector \`yaml:"baseOtelCollector"\``。
> loader 走 `KnownFields(true)` 严格解析(`internal/config/loader.go:22`),**不登记则 `Load` 直接报未知字段**。

```go
type BaseOtelCollector struct {
    Enable         bool           `yaml:"enable"`
    Node           string         `yaml:"node"`           // collector 运行节点(hostname)
    OtlpHTTPPort   string         `yaml:"otlpHttpPort"`   // 默认 4318,rustfs push + 本 receiver
    OtlpGRPCPort   string         `yaml:"otlpGrpcPort"`   // 默认 4317
    SelfMetricsPort string        `yaml:"selfMetricsPort"`// 默认 8899(避免撞纳管 collector 的 8888)
    S3Bucket       string         `yaml:"s3Bucket"`       // 默认 otel(复用 rustfs 里的 bucket)
    S3Prefix       string         `yaml:"s3Prefix"`       // 默认 otel-base
    S3Region       string         `yaml:"s3Region"`       // 默认 us-east-1(rustfs 忽略但 exporter 必填)
    MemLimitMiB    int            `yaml:"memLimitMiB"`    // 默认 512
    MysqldExporter MysqldExporter `yaml:"mysqldExporter"`
    NexusMetrics   NexusMetrics   `yaml:"nexusMetrics"`
}

type MysqldExporter struct {
    Enable          bool   `yaml:"enable"`
    Port            string `yaml:"port"`           // 默认 9104
    MonitorUser     string `yaml:"monitorUser"`    // 默认 exporter
    MonitorPassword string `yaml:"monitorPassword"`
}

type NexusMetrics struct {
    MetricsUser     string `yaml:"metricsUser"`     // 默认 metrics
    MetricsPassword string `yaml:"metricsPassword"`
    MetricsPath     string `yaml:"metricsPath"`     // 默认 /service/rest/metrics/prometheus
}
```

S3 endpoint 与凭据**不新增字段**,从已有 `Rustfs` 配置推导:endpoint=`http://<rustfs.Nodes[0] IP>:<rustfs.Config.APIPort>`,access/secret=`rustfs.Config.User`/`rustfs.Config.Password`。

**A2. Packages 新增两个制品字段** `internal/config/global.go` 的 `Packages`(行 128+):
```go
OtelColContrib Package `yaml:"otelColContrib"`
MysqldExporter Package `yaml:"mysqldExporter"`
```

**A3. 样例配置** `internal/config/configs/cluster-config.yml`:加 `baseOtelCollector:` 块(默认 `enable: false` 向后兼容)、`packages.otelColContrib` / `packages.mysqldExporter` 两个包名。同步 `create config` 渲染模板占位符(若有)。

---

### B. 修复 RustFS 采集接线缺口(`internal/plan/builders_cluster.go`)

**这是采集缺口的根因**:plan 版 `rustfsTask`(行 76-87)**没有 `ObsEndpoint` 字段**,`buildRustfs`(行 179-190)也从不读它,导致 `create cluster` 装的 rustfs 恒不上报。standalone 版 `internal/cli/create/rustfs.go` 早已实现,直接搬:

- plan 版 `rustfsTask` struct 加 `ObsEndpoint string` 字段。
- `start()`/写 start.sh 逻辑复制 `create/rustfs.go:108-131` 的 `RUSTFS_OBS_ENDPOINT`/`RUSTFS_OBS_SERVICE_NAME` 导出(用 `shellutil.Quote`)。
- `buildRustfs` 里:`ObsEndpoint = fmt.Sprintf("http://%s:%s", <collector 节点 IP>, cfg.BaseOtelCollector.OtlpHTTPPort)`,仅当 `cfg.BaseOtelCollector.Enable` 时非空。collector 节点 IP 用 `requireNode(ctx.GlobalNodes, cfg.BaseOtelCollector.Node)` 解析。

---

### C. base-otel-collector 安装任务(新增)

**C1. handler** 新增 `internal/plan/otelcollector.go`(或并入 `builders_cluster.go`),struct `baseOtelCollectorTask` 实现 `Name()="安装基础采集collector"` + `Handle(*ssh.Client,bool)`。`doRun` 照抄 rustfs 的"下载解压 + 写配置 + checkStart + start"套路(`create/rustfs.go:43-106` 为模板):
- 取包:按 `exec.GetArch()` 选 `cfg.Packages.OtelColContrib.X86_64/.Aarch64`,从 `PackagePath` 取 `otelcol-contrib_0.156.0_linux_{amd64,arm64}.tar.gz`;解压到稳定目录 `base-otel-collector/releases/<version>/`,再原子切换 `base-otel-collector/otelcol-contrib` 软链。升级失败时回滚到旧软链并重启旧版本。
- 生成 `config/otelcol.yaml`:用 `//go:embed` 的 Go `text/template` 资产渲染,结构对齐 `datasophon-worker/.../templates/otelcol.ftl` 的 **s3 分支**,只保留 metrics 相关:
  - `extensions: file_storage/queue`
  - `receivers`: `otlp`(grpc `${OtlpGRPCPort}` / http `${OtlpHTTPPort}`,0.0.0.0)、`prometheus/infra`(两个 scrape job:见下)
  - `processors`: `memory_limiter`(`${MemLimitMiB}`)、`filter/drop_empty_summary`(通用防 NaN,照抄 ftl:52-55)、`batch`。**不带** ZK 专属 `filter/drop_zk_decaying_summary`。
  - `exporters.awss3`: 照抄 ftl s3 分支(`s3uploader` region/bucket/prefix/endpoint、`s3_force_path_style: true`、`marshaler: otlp_json`、`sending_queue.storage: file_storage/queue`、`retry_on_failure`)。
  - `service.pipelines.metrics`: `[otlp, prometheus/infra]` → `[memory_limiter, filter/drop_empty_summary, batch]` → `[awss3]`。
- 两个 prometheus scrape job(缩进对齐 ftl 8 空格):
  - **mysql**:`job_name: 'mysql'`,`metrics_path: /metrics`,target `<mysqlNodeIP>:<MysqldExporter.Port>`。仅当 `cfg.Mysql.Enable && MysqldExporter.Enable`。
  - **nexus**:`job_name: 'nexus'`,`metrics_path: <NexusMetrics.MetricsPath>`,target `<regNodeIP>:<registry webPort>`,**加 `basic_auth: {username, password}`**(代码库无先例,需新写;prometheusreceiver 原生支持)。仅当 `cfg.Registry.Enable`。
- 生成 `config/otelcol.env`:写 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`(= rustfs user/password)。**S3/Nexus 凭据走 env 不进 yaml 明文**。配置、env 和脚本均使用同目录临时文件原子替换,env 权限为 `0600`。
- `control.sh` 以 `//go:embed` 固化并由测试与 `package/raw/meta/datacluster-physical/OTELCOLLECTOR/script/control.sh` 做字节级同步校验。配置和版本均无变化时不重启。

**C2. BuildFunc** `buildBaseOtelCollector(ctx)` 仿 `buildRustfs`:`requireNode(ctx.GlobalNodes, cfg.BaseOtelCollector.Node)` → `singleHostAction(node, task)`。

**C3. 注册 Step** `internal/plan/registry.go` `InitALLRegistry`:在 **`init-rustfs`(行 20)之后**插入 `init-base-otel-collector`(S3 sink 已就绪,rustfs 已能拿到 push 端点)。`Condition: ctx.Cfg.BaseOtelCollector.Enable`,`Build: buildBaseOtelCollector`。

---

### D. Nexus 监控账号(nx-metrics-all)

CLI 装 Nexus 时创建只读 metrics 账号(而非复用 admin)。`internal/bootstrap/NexusClient` 作为 plan 与 standalone create 路径共享的 REST 客户端:
- role 不存在时 `POST`,存在时 `PUT`,持续收敛 `nx-metrics-all` 权限。
- user 不存在时创建,存在时更新元数据并调用 `change-password`,支持密码轮换。
- 所有响应都校验状态码并限制错误响应体读取大小。

参数从 `cfg.BaseOtelCollector.NexusMetrics` 读。仅当 `cfg.BaseOtelCollector.Enable`。

---

### E. mysqld_exporter 安装 + 监控账号(新增)

**E1. 监控账号** 由 `mysqldExporterTask` 在 exporter 安装前收敛,GRANT 使用最小权限:
```sql
CREATE USER IF NOT EXISTS 'exporter'@'localhost' IDENTIFIED BY '...' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'localhost';
```
host 用 `localhost`(exporter 与 mysql 同节点,本机连 127.0.0.1:3306),最小暴露。

**E2. exporter 安装 handler** 新增 struct(照抄 rustfs `doRun`/`writeStartScript`/`checkStart`):
- 取包 `cfg.Packages.MysqldExporter`,解压,`chmod +x mysqld_exporter`。
- 凭据写入权限 `0600` 的 `.my.cnf`,启动参数使用 `--config.my-cnf`,不把密码暴露到进程环境或命令行。
- 版本解压到 `mysqld_exporter/releases/<version>/`,稳定软链为 `mysqld_exporter/mysqld_exporter`;配置或版本变化才受控重启,失败回滚旧版本。
- `checkStart` 按完整二进制路径精确匹配进程。

**E3. BuildFunc + Step** `buildMysqldExporter(ctx)` target=`requireNode(ctx.GlobalNodes, cfg.Mysql.Node)`(exporter 随 mysql 走)。在 `InitALLRegistry` 的 **`init-mysql-app-db`(行 101)之后**插入 `init-mysqld-exporter`(mysql 与账号都已就绪)。`Condition: ctx.Cfg.Mysql.Enable && ctx.Cfg.BaseOtelCollector.MysqldExporter.Enable`。E1 建账号可作为该 Step 内的前置动作,或单独一个 `init-mysql-exporter-account` Step 紧挨其前。

---

### F. 制品 vendoring

- **otelcol-contrib 0.156.0**:`package/manifest.json` 使用 `repoTypes: ["raw", "base"]`;下载一次后通过 hardlink(跨文件系统回退 copy)同时服务平台纳管与 CLI 引导期两类消费者。
- **mysqld_exporter 0.16.0**:两架构制品使用 `repoType: base`,文件名与 `cluster-config.yml` 的 `packages.mysqldExporter` 一致。

---

## 复用清单(实现时照抄对象)

| 目标 | 照抄对象 | 位置 |
|---|---|---|
| 下载解压 + start.sh + checkStart | `rustfsTask` | `internal/cli/create/rustfs.go:43-131` |
| 注入环境变量到 start.sh | `writeStartScript` | `create/rustfs.go:108-131` |
| 建最小权限 MySQL 账号 | `initCommonAccount`(改 GRANT) | `internal/cli/init/mysql_app_db.go:66-101` |
| Nexus REST 建 role/user | 共享 `NexusClient` upsert | `internal/bootstrap/nexus_client.go` |
| 从 registry 下载制品 | `DownloadFromRegistry` | `internal/cli/init/util.go:15` |
| otelcol s3 exporter / NaN filter / 结构 | `otelcol.ftl`(s3 分支) | `datasophon-worker/.../templates/otelcol.ftl` |
| control.sh 启停(flock/env/restart) | 现成脚本 | `.../OTELCOLLECTOR/script/control.sh` |
| 新 Step 接入 DAG | `buildRustfs` + `InitALLRegistry` | `internal/plan/builders_cluster.go` / `registry.go` |

---

## 验证

**代码级(必须全绿):**
```bash
cd datasophon-cli-go
make vet
make test           # 附带新 handler / config 解析 / plan 生成单测
```
- 单测覆盖:严格配置解析与默认/边界校验、plan 顺序和 target、模板 YAML、嵌入脚本同步、双架构制品路由、Nexus upsert/密码轮换、敏感文件原子写入、升级/无变化不重启、dry-run 不触发远端读取或 REST。

**plan 级(dry-run 不碰真机):**
```bash
DDH_HOME=/tmp datasophon-cli create cluster plan -t hadoop -p <dir> --installPath <p> -n <pkgs>
```
校验:①新 step 出现在 rustfs / mysql-app-db 之后正确位置;②`enable: false` 时被标记 skipped;③`clusterHash` 变化符合预期(新增 config 字段)。

**真机端到端(五节点沙箱,人工审批后):**
1. collector 进程存活,`config/otelcol.yaml` 三目标齐全,`otelcol.env` 有 AWS 凭据。
2. rustfs 进程环境含 `RUSTFS_OBS_ENDPOINT` 指向 collector 节点。
3. mysqld_exporter 绑 `<nodeIP>:9104`,`curl http://<nodeIP>:9104/metrics` 有 `mysql_up 1`。
4. Nexus metrics 账号可认证:`curl -u metrics:<pwd> http://<regIP>:8081/service/rest/metrics/prometheus` 有指标。
5. RustFS S3 bucket 出现 `otel-base/` 前缀对象(collector 写入成功)。

---

## 范围边界(明确不做)

- **S3→Doris 回灌**:沿用 epic 现有 `awss3receiver` 时间窗回放机制,不在本次 CLI 改动;本任务只保证基础设施指标进 S3。
- **切 Doris 直连**:未来若需 collector 稳态直写 Doris,是二期(改 exporterMode + Doris 账号),本次固定 s3 模式。
- **Master 端 `OtelScrapeConfigBuilder`**:不改动,本方案刻意绕开纳管角色模型,不把基础设施伪装成 `ClusterServiceRoleInstanceEntity`。
- **`create node` 扩容 DAG**(`InitNodeRegistry`):本次只接 `create cluster`(initALL),扩容路径另评估。
