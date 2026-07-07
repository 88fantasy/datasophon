# 可观测链路模拟测试栈

本文档说明 `docker-compose.observability.yml` 的启动、验证、排障与 DDL 导出流程。

## 架构

```
OTel Collector (otelcol-contrib:0.154.0)
  ├─[otlp receiver :4317/:4318]◀── datasophon-api / worker（OTel Java Agent 直发 traces/logs）
  ├─[prometheus/self :8888]      collector 自身 self-metrics
  ├─[prometheus/doris :8030/:8040]  Doris FE/BE 指标
  ├─[host_metrics]               容器视角 system.* 指标
  └─[dorisexporter / Stream Load HTTP :8030]─▶
      Doris FE (:8030 HTTP, :9030 MySQL)
        └─▶ Doris BE (storage)
```

> JuiceFS / ZooKeeper / RustFS 的 Prometheus scrape 采集链路已在生产验证通过（生产
> `OtelScrapeConfigBuilder` 按角色 DDL 通用生成 scrape job，无需沙箱专门验证），对应的
> `obs-juicefs*`/`obs-redis`/`obs-rustfs*`/`obs-zookeeper` 沙箱容器已从本 compose 移除，
> 详见 `docs/monitoring/juicefs-otel-verification.md`、`docs/monitoring/zookeeper-otel-verification.md`、
> `docs/monitoring/rustfs-otel-verification.md`（历史验证记录）。

| 容器 | 用途 | 宿主端口 |
|---|---|---|
| obs-doris-fe | Doris FE，SQL 查询/管理入口 | 8030, 9030 |
| obs-doris-be | Doris BE，数据存储与计算 | 8040 |
| obs-doris-init | 一次性初始化容器，等待 BE 向 FE 注册完成后退出 | — |
| obs-otelcol | OTel Collector，接收 api/worker OTLP + 采集 Doris/host 指标写入 Doris | 8888 (self-metrics), 4317 (OTLP gRPC), 4318 (OTLP HTTP) |

版本对应 `package/manifest.json`：Doris 4.0.5、otelcol-contrib 0.154.0。

## 前置条件

- Docker Desktop 已运行（Mac 上 Doris BE 依赖的 `vm.max_map_count` 由 Docker Desktop VM 满足）
- Linux 裸宿主需额外执行：`sudo sysctl -w vm.max_map_count=2000000`

## 启动

```bash
# 从仓库根目录执行
docker compose -f deploy/compose/docker-compose.observability.yml up -d

# 查看启动状态（obs-doris-init 退出码 0 = BE 注册完成，是 collector 启动的前提）
docker compose -f deploy/compose/docker-compose.observability.yml ps
```

预计首次启动耗时：Doris BE 注册 ~1-2 分钟，总计约 2-3 分钟。

## 验证链路

```bash
# 1. collector self-metrics 在线（sent > 0 表示数据已在流动）
curl -s http://localhost:8888/metrics | grep -E 'otelcol_exporter_sent|otelcol_exporter_send_failed'

# 2. Doris otel 库表已自动建好（dorisexporter create_schema=true 的产物）
mysql -h 127.0.0.1 -P 9030 -u root -e "SHOW TABLES FROM otel"

# 3. Doris FE/BE 自身指标已落库（约等 2-3 个抓取周期，即 30-45s 后再查；service_name 对齐
#    prometheus/doris 的 job_name: doris）
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT metric_name, COUNT(*) c FROM otel.otel_metrics_gauge WHERE service_name = 'doris' GROUP BY metric_name ORDER BY c DESC LIMIT 15"

# 4. host_metrics（容器视角 system.* 指标）已落库，service_name 应为 node
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT DISTINCT service_name FROM otel.otel_metrics_gauge WHERE metric_name LIKE 'system.%'"

# 5. OTLP traces/logs（需另外启动 datasophon-api/worker 并把 OTel Java Agent 指向本 collector
#    的 4317/4318 端口后才会有数据，此 compose 本身不产生 traces/logs）
mysql -h 127.0.0.1 -P 9030 -u root -e "SELECT COUNT(*) FROM otel.otel_traces"
```

**成功判据**：步骤 1 sent 计数持续增长；步骤 2 列出 `otel_metrics_gauge` 等表；步骤 3 查到
`doris_fe_*`/`doris_be_*` 一类指标；步骤 4 查到 `service_name = node` 且指标名以 `system.` 开头的行；
步骤 5 仅在接入了真实 api/worker OTLP 上报后才有非零计数。

**已知限制（沙箱级，非本链路专属）**：单 BE + `replication_num=1` 在高频并发 Stream Load
下，个别 tablet 曾出现 Doris 查询侧报 `fail to find path in version_graph` 且不会自愈（重启 BE
也无效，写入侧 publish version 仍持续成功，`otelcol_exporter_sent_metric_points` 正常增长）。
这是该本地沙箱的已知脆弱点，不是 otelcol 配置本身的问题；遇到时优先 `down -v` 重建（清空卷）。

## 导出权威 DDL（满足设计 §4.2）

链路验证通过后，执行以下命令导出 `create_schema=true` 自动建的精确表结构，
存档为生产自管 schema 的权威来源：

```bash
for t in otel_metrics_gauge otel_metrics_sum otel_metrics_histogram; do
  echo "-- Table: otel.$t"
  mysql -h 127.0.0.1 -P 9030 -u root -N -e "SHOW CREATE TABLE otel.$t\G" 2>/dev/null || \
    echo "-- (table not found, skip)"
  echo ""
done > deploy/observability/otelcol/doris-otel-schema.sql
echo "DDL exported to deploy/observability/otelcol/doris-otel-schema.sql"
```

导出后校验 `otel_metrics_gauge` 含 `metric_name / attributes / value / timestamp` 列
（与 `OtelMetricsQueryService.java` 的查询契约一致）：

```bash
grep -E 'metric_name|attributes|`value`|`timestamp`' \
  deploy/observability/otelcol/doris-otel-schema.sql
```

## 排障

**collector 一直 restart**：先看日志确认错误类型：
```bash
docker compose -f deploy/compose/docker-compose.observability.yml logs obs-otelcol
```
- `connection refused` 连接 Doris → BE 还在注册中，等 `obs-doris-init` 退出码 0 后再起。
- `create table failed: no backend` → replication_num 与 BE 数不匹配，检查 `otelcol-juicefs.yaml` 中 `replication_num: 1`。

**Doris FE 启动报 IP mismatch**：清除卷后重建（`docker compose ... down -v`），固定 IP 与 volume 内 priority_networks 不一致时会报此错。

**Doris arm64 镜像问题**：若 FE/BE 在 Apple Silicon 上无法启动，可在 compose 中给 FE/BE 加 `platform: linux/amd64` 走模拟层（需重启 Docker Desktop）。

## 停止 / 清理

```bash
# 停止并保留数据卷
docker compose -f deploy/compose/docker-compose.observability.yml down

# 停止并清除所有数据（完全重置）
docker compose -f deploy/compose/docker-compose.observability.yml down -v
```
