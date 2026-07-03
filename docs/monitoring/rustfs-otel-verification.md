# RustFS 指标接入验证（Phase 2 SQL，GATE）

> **用途**：Phase 1 让 RustFS 通过 OTLP 把指标上报到 OTel Collector → Doris 后，用本文档的 SQL
> 核对落库指标能否被 MinIO 官方看板复用。**结论决定是否进入看板开发（Phase 3）**。
>
> **2026-07-04 已用真实环境跑通并回填结论**（见文末「验证结论」）：本机 `deploy/compose/docker-compose.observability.yml`
> 沙箱栈（`obs-otelcol` + `obs-doris-fe/be`，均已在跑）+ 手动起的 `rustfs/rustfs:1.0.0-beta.8` 容器
> （挂 `compose_obs_net`，`RUSTFS_OBS_ENDPOINT=http://obs-otelcol:4318`），单节点 SNSD 拓扑，未额外打
> S3 流量（数据是 rustfs 自身周期上报的运行时/系统指标 + 少量内部 ListBuckets 探测）。

## 前置条件

1. RustFS 已用 `datasophon-cli create rustfs`（config 模式）启动，`rustfs.config.obsEndpoint` 已设为
   本节点 OTel Collector 的 OTLP/HTTP 端点（如 `http://127.0.0.1:4318`）。参考
   `datasophon-cli-go/internal/cli/create/rustfs.go`：非空时 `start.sh` 会 `export RUSTFS_OBS_ENDPOINT=...`
   `export RUSTFS_OBS_SERVICE_NAME=rustfs` 再拉起 rustfs 进程。
2. 该节点的 OTel Collector（`OTELCOLLECTOR` 服务角色）处于 `RUNNING`，且 `exporterMode=doris`（Doris 已就绪，
   非引导期 S3 兜底）。collector 配置模板 `datasophon-worker/.../templates/otelcol.ftl` 的 `otlp` receiver
   （grpc:4317 / **http:4318**）已经在 `metrics` pipeline 中，接线本身不需要改动。
3. 已对 RustFS 打过一些 S3 读写流量（避免指标全是 0/缺失，尤其是 histogram/counter 类）。
4. 有 Doris `otel` database 的只读 MySQL 协议账号。

## Step 1 — 确认 rustfs 是否真的在上报

```sql
-- 任意 otel_metrics_* 表命中即说明上报链路通了
SELECT COUNT(*) FROM otel.otel_metrics_gauge     WHERE service_name = 'rustfs';
SELECT COUNT(*) FROM otel.otel_metrics_sum       WHERE service_name = 'rustfs';
SELECT COUNT(*) FROM otel.otel_metrics_histogram WHERE service_name = 'rustfs';
```

> `service_name` 是 `otel_metrics_*` 表的顶层列（dorisexporter 直接落，非 `resource_attributes` 内嵌路径），
> 对应 rustfs 启动时设置的 `RUSTFS_OBS_SERVICE_NAME=rustfs`。若三条全 0：先查 collector 日志确认 4318 端口
> 收到请求（`RUSTFS_OBS_ENDPOINT` 是否真被 rustfs 进程读取到、端口是否被防火墙挡），再查 collector
> 是否处于 `exporterMode=doris`（S3 引导期不会写 Doris）。

## Step 2 — 枚举 rustfs 实际发出的指标名（决定命名空间：`minio_*` / `rustfs_*` / 其他）

```sql
SELECT DISTINCT metric_name FROM otel.otel_metrics_gauge     WHERE service_name = 'rustfs' ORDER BY 1;
SELECT DISTINCT metric_name FROM otel.otel_metrics_sum       WHERE service_name = 'rustfs' ORDER BY 1;
SELECT DISTINCT metric_name FROM otel.otel_metrics_histogram WHERE service_name = 'rustfs' ORDER BY 1;
SELECT DISTINCT metric_name FROM otel.otel_metrics_summary   WHERE service_name = 'rustfs' ORDER BY 1;
```

**结果记录处（已回填，2026-07-04）**：

RustFS 1.0.0-beta.8 共发出 **116 个 `otel_metrics_gauge`指标 + 41 个`otel_metrics_sum`指标 + 10 个
`otel_metrics_histogram`指标**（`otel_metrics_summary`/`otel_metrics_exponential_histogram` 为空，本次未产生数据）。
**全部为 `rustfs_*` 前缀原生命名空间（另有 2 个 OTel 语义化命名的 `rustfs.lock.*` 点分格式），
不存在任何 `minio_*` 命名的指标**——GitHub #796/#1228 悬而未决的"minio_ vs rustfs_"问题在此有了确定答案。

覆盖面按类别摘录（完整清单见本次会话记录，或重跑 Step2 SQL）：

- **容量/集群**：`rustfs_cluster_capacity_{usable_total,used,free}_bytes`、`rustfs_cluster_buckets_total`、
  `rustfs_cluster_objects_total`、`rustfs_cluster_usage_objects_{count,total_bytes}`
- **纠删集/健康**（MinIO 官方看板没有的细粒度）：`rustfs_cluster_erasure_set_{health,read_health,write_health,
  online_drives_count,healing_drives_count,read_quorum,write_quorum}` 等一整套
- **副本**（MinIO 官方看板没有的细粒度）：`rustfs_replication_{current,average,max}_active_workers` /
  `_data_transfer_rate` / `_queued_bytes` / `_queued_count`
- **S3 操作**：`rustfs_s3_operations_total`（sum，带 `bucket`+`op` 标签，见 Step3）
- **HTTP 层**：`rustfs_http_server_{requests_total,failures_total,request_duration_seconds,
  request_body_bytes_total,response_body_bytes_total}`
- **磁盘/驱动器**：`rustfs_system_drive_{free,used,total}_bytes`、`_reads_per_sec`、`_writes_per_sec`、
  `_reads_await`、`_health`、`_online_count`/`_offline_count`
- **进程/系统资源**：`rustfs_process_{cpu_percent,memory_bytes,uptime_seconds}`、
  `rustfs_system_process_cpu_total_seconds`(sum)、`rustfs_system_memory_*`、`rustfs_system_cpu_*`
- **IAM/通知**：`rustfs_cluster_iam_*`、`rustfs_notification_events_{sent,errors,skipped}_total`

**明确缺口**：未发现任何"集群节点在线数"量级的指标（对应 MinIO `minio_cluster_nodes_online_total`）——
本次是单节点 SNSD 拓扑，节点身份只能从 `resource_attributes.network.local.address` 反推
（`COUNT(DISTINCT ...)` 代替），没有现成 gauge 可直接对应；多节点拓扑下是否存在专门的节点计数指标
本次沙箱无法验证。

## Step 3 — 抽查标签维度（`attributes` / `resource_attributes` 是否有 MinIO 看板依赖的 label）

```sql
-- 任取几行看 attributes/resource_attributes 的 key 集合，VARIANT 列直接 SELECT 即可看到 JSON 结构
SELECT metric_name, attributes, resource_attributes
FROM otel.otel_metrics_sum
WHERE service_name = 'rustfs'
ORDER BY timestamp DESC
LIMIT 20;
```

重点确认是否存在 `bucket` / `api`（操作名，如 GetObject/PutObject）/ `server`（节点地址）/ `drive`（磁盘路径）
这几个 MinIO 官方看板常用的分组维度。**若指标名匹配但这些 label 缺失，说明 rustfs 的 OTel 语义与 MinIO
Prometheus exporter 的语义不完全对等**，需要在 spec 里降级为总量/instance 维度面板。

**结果记录处（已回填，2026-07-04）**：label 维度比预期丰富，关键分组维度均已具备：

|                      指标                       |            `attributes` 示例             |
|-----------------------------------------------|----------------------------------------|
| `rustfs_system_drive_free_bytes`              | `{"drive":"/data","server":"/data"}`   |
| `rustfs_s3_operations_total`                  | `{"bucket":"*","op":"s3:ListBuckets"}` |
| `rustfs_http_server_request_duration_seconds` | `{"status_class":"4xx"}`               |

`resource_attributes` 走标准 OTel semconv 嵌套结构（非顶层 `service_name` 已覆盖的部分）：
`{"deployment":{"environment":{"name":"production"}},"network":{"local":{"address":"10.10.30.132"}},
"service":{"name":"rustfs","version":"1.0.0"},"telemetry":{"sdk":{...}}}`——`network.local.address`
是目前能拿到的节点身份标识（弥补 Step2 里"无节点计数指标"的缺口，可用于按节点 distinct 计数）。

**结论：`bucket`/`op`/`drive`/`server`/`status_class` 这几个关键维度都存在**，支持做按桶、按操作、
按磁盘、按状态码分类的面板——但这些 key **均不在** `OtelMetricsQueryService` 当前的属性过滤白名单
（`group/type/mode/path/device`）里，Phase 3 若要用这些维度必须先扩白名单（计划里已列为条件动作项）。

## Step 4 — 对照 MinIO 官方看板

MinIO 官方看板源：`https://github.com/minio/minio/blob/master/docs/metrics/prometheus/grafana/README.md`
（`minio-dashboard.json` grafana ID 13502 + `minio-node.json` / `minio-bucket.json` /
`minio-replication-{node,cluster}.json`）。

> **重要**：不要凭记忆或网络搜索结果臆断 MinIO 指标名清单——本次探索发现 MinIO 官方文档站（社区版 vs
> AIStor 商业版）对同一类指标使用了不同前缀/命名（如 `minio_s3_requests_total` vs
> `minio_api_requests_total`，`minio_cluster_capacity_usable_free_bytes` vs
> `minio_cluster_health_capacity_usable_free_bytes`），存在版本漂移。**权威做法是直接拉取
> `minio-dashboard.json` 原始文件，从每个 panel 的 `targets[].expr` 里提取真实用到的指标名**，
> 逐条与 Step 2 的 DISTINCT 清单比对，而非依赖本文档罗列的猜测清单。

对照结果按三档记录：

|                             档位                             |                        含义                        |                      处理                       |
|------------------------------------------------------------|--------------------------------------------------|-----------------------------------------------|
| ✅ 命中                                                       | 指标名与 label 均匹配 dashboard JSON 的 `targets[].expr` | 直接进 Phase 3，PromQL/描述符可直接照搬                   |
| 🟡 命名不同                                                    | 语义对应但指标名/label 命名不同（如 rustfs 用了 OTel semconv 名）  | 产出「rustfs→minio 指标名映射表」，Phase 3 按映射改写         |
| 🔴 缺失                                                      | dashboard 依赖的指标 rustfs 完全没发                      | 记录缺口面板；若因 `OtelMetricsQueryService` 属性白名单（当前仅 |
| `group/type/mode/path/device`）挡住了 `bucket`/`api`，需先扩白名单再复核 |

**结论记录处（已回填，2026-07-04）**：

**🟡 命名不同 —— 不是 ✅ 直接命中，但结论正面，建议进 Phase 3（用 rustfs 原生指标名重新设计，而非移植 MinIO JSON）。**

- RustFS 100% 使用自有 `rustfs_*` 命名空间，**没有一个 `minio_*` 指标**——MinIO 官方 `minio-dashboard.json`
  等看板 JSON 的 PromQL 无法直接导入使用，必须逐面板重写指标名。
- 但语义覆盖面很好，MinIO 官方看板四类核心面板（容量、节点/磁盘健康、S3 请求量/延迟/错误、网络吞吐）
  在 rustfs 侧都能找到对应或更细粒度的指标；`bucket`/`op`/`drive`/`server`/`status_class` 等关键分组
  label 也都具备。RustFS 甚至多出纠删集健康、副本队列、IAM 认证插件等 MinIO 基础看板没有的维度。
- 明确缺口：① 无节点在线计数类 gauge（需用 `resource_attributes.network.local.address` distinct 计数替代，
  单节点沙箱无法验证多节点场景下是否存在）；② 后端属性白名单需扩容（`bucket/op/drive/server/status_class`）。
- ~~③ 本次未打真实 S3 读写流量~~ **已用 `aws s3api`/`aws s3 cp` 打点补全（2026-07-04）**：用真实 S3 读写
  （`put-object` ×16、`get-object` ×15、`head-object` ×10、`list-objects-v2`、`create-bucket`、
  `copy-object`、单个 + 批量 `delete-object(s)`、`get-bucket-location`、大文件触发自动分片上传
  `create-multipart-upload`/`upload-part`/`complete-multipart-upload`）后，`rustfs_s3_operations_total`
  的 `op` 取值从仅 1 个（`s3:ListBuckets`）扩到 **13 个**：
  `s3:{ListBuckets,CreateBucket,PutObject,HeadObject,GetObject,ListObjectsV2,CreateMultipartUpload,
  DeleteObjects,UploadPart,CopyObject,CompleteMultipartUpload,GetBucketLocation,DeleteObject}`。
  用 `MAX(value)` 核对累计计数器语义正确：`PutObject=16`（15 次小文件+1 次中等文件，大文件走 multipart
  单独计入 UploadPart 不计入 PutObject）、`GetObject=15`、`HeadObject=10`，与实际调用次数逐一吻合——
  **`rustfs_s3_operations_total` 是可信的、细粒度到具体 S3 API 动词的计数器，足以支撑 MinIO 风格的
  「按操作类型」面板**。
- **建议**：Phase 3 直接基于 `docs/monitoring/rustfs-otel-verification.md` 本节列出的 `rustfs_*` 指标清单
  设计 `panel-catalog/Rustfs.json`（**不是**移植 MinIO JSON），MinIO 官方看板仅作面板类别/布局参考
  （容量/健康/流量/延迟/错误五大类），具体 PromQL/描述符全部用 rustfs 原生指标名。前置动作只剩
  ① 后端属性白名单扩容（`bucket/op/drive/server/status_class`）；② 多节点场景下是否有专门节点计数指标
  待多节点环境验证（不阻塞单节点/instance 维度的 Phase 3 起步）。

## 参考：Doris otel_metrics_* 表结构要点

来自 `datasophon-api/src/main/resources/observability/doris/V1__otel_tables.sql`：

- `service_name VARCHAR(200)`、`metric_name VARCHAR(200)` 均为顶层列，直接等值查询即可，无需下钻 JSON。
- `attributes VARIANT`、`resource_attributes VARIANT`：前者是指标自身的维度标签，后者是 OTel Resource
  级属性（服务名/实例等，多数场景已被顶层 `service_name` 覆盖，直查 `attributes` 即可）。
- histogram/summary 表额外有分位数相关列（`otel_metrics_histogram` 存桶计数，`otel_metrics_summary`
  存 `quantile_values array<struct<quantile,value>>`，取分位数需 `LATERAL VIEW EXPLODE`，参考
  `OtelMetricsQueryService` 里 summary 表的既有查询逻辑）。

