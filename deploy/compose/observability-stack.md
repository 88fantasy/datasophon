# 可观测链路模拟测试栈

本文档说明 `docker-compose.observability.yml` 的启动、验证、排障与 DDL 导出流程。

## 架构

```
APISIX (apache/apisix:3.16.0-debian, standalone 模式, :9080/:9091)
  ├─[obs-apisix-load 持续压流 :9080/get]
  └─[prometheus scrape /apisix/prometheus/metrics, 15s]─▶
    OTel Collector (otelcol-contrib:0.154.0)
      └─[dorisexporter / Stream Load HTTP :8030]─▶
        Doris FE (:8030 HTTP, :9030 MySQL)
          └─▶ Doris BE (storage)
```

APISIX 跑 **standalone 模式**（`deployment.role_data_plane.config_provider: yaml`），
路由/上游/global_rule 声明在 `conf/apisix/apisix.yaml`（本地文件，无需 etcd）；
`conf/apisix/config.yaml` 开启 prometheus 插件导出端口 `:9091`。

| 容器 | 用途 | 宿主端口 |
|---|---|---|
| obs-doris-fe | Doris FE，SQL 查询/管理入口 | 8030, 9030 |
| obs-doris-be | Doris BE，数据存储与计算 | 8040 |
| obs-echo | APISIX 上游回显服务（`hashicorp/http-echo`） | — |
| obs-apisix | APISIX，被采集的 Prometheus 指标来源（standalone） | **9080**（网关）, **9091**（指标） |
| obs-apisix-load | 持续压流容器，让 RPS/带宽/延迟等请求类指标产生真实数据 | — |
| obs-doris-init | 一次性初始化容器，等待 BE 向 FE 注册完成后退出 | — |
| obs-otelcol | OTel Collector，采集 APISIX 指标写入 Doris | 8888 (self-metrics) |

版本对应 `package/manifest.json`：Doris 4.0.5、otelcol-contrib 0.154.0、APISIX 3.16.0。

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

预计首次启动耗时：Doris BE 注册 ~1-2 分钟，APISIX 秒级起，总计约 3-5 分钟。

## 验证链路

```bash
# 1. APISIX 指标端点可达（应返回 Prometheus 文本格式，含 apisix_* 指标；standalone 模式无需鉴权）
curl -s http://localhost:9091/apisix/prometheus/metrics | head -20

# 1b. 压流路由本身可达（obs-apisix-load 已在持续打流量，这一步仅供人工手动验证）
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9080/get

# 2. collector self-metrics 在线（sent > 0 表示数据已在流动）
curl -s http://localhost:8888/metrics | grep -E 'otelcol_exporter_sent|otelcol_exporter_send_failed'

# 3. Doris otel 库表已自动建好（dorisexporter create_schema=true 的产物）
mysql -h 127.0.0.1 -P 9030 -u root -e "SHOW TABLES FROM otel"

# 4. APISIX 指标已落库（约等 2-3 个抓取周期，即 30-45s 后再查）
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT metric_name, COUNT(*) c FROM otel.otel_metrics_gauge GROUP BY metric_name ORDER BY c DESC LIMIT 15"

# 4b. histogram 分位数表(APISIX 延迟指标)
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT DISTINCT metric_name FROM otel.otel_metrics_histogram WHERE metric_name LIKE 'apisix%'"
```

**成功判据**：步骤 1 返回 Prometheus 文本（含 `apisix_http_requests_total` 等）；步骤 3 列出 `otel_metrics_gauge` 等表；步骤 4 查到含 `apisix_*` 的 `metric_name` 行；步骤 4b 查到 `apisix_http_latency`。

**已知限制（沙箱级，非本链路专属）**：单 BE + `replication_num=1` 在高频并发 Stream Load
（otelcol 4 consumers × 多张表 × 15s 抓取间隔）下，个别 tablet 曾出现 Doris 查询侧报
`fail to find path in version_graph` 且不会自愈（重启 BE 也无效，写入侧 publish version
仍持续成功，`otelcol_exporter_sent_metric_points` 正常增长）。这是该本地沙箱的已知脆弱点，
不是 APISIX/otelcol 配置本身的问题；遇到时优先 `down -v` 重建（清空卷），必要时降低压流频率
（调大 `obs-apisix-load` 里的 `sleep` 间隔）。

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
- `create table failed: no backend` → replication_num 与 BE 数不匹配，检查 `otelcol-nexus.yaml` 中 `replication_num: 1`。

**Nexus 指标 401 / 403**：检查 `otelcol-nexus.yaml` 中 basic_auth 账号密码是否与 Nexus 一致（默认 admin/admin123）。

**Doris FE 启动报 IP mismatch**：清除卷后重建（`docker compose ... down -v`），固定 IP 与 volume 内 priority_networks 不一致时会报此错。

**Doris arm64 镜像问题**：若 FE/BE 在 Apple Silicon 上无法启动，可在 compose 中给 FE/BE 加 `platform: linux/amd64` 走模拟层（需重启 Docker Desktop）。

## 停止 / 清理

```bash
# 停止并保留数据卷
docker compose -f deploy/compose/docker-compose.observability.yml down

# 停止并清除所有数据（完全重置）
docker compose -f deploy/compose/docker-compose.observability.yml down -v
```
