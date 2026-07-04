# 可观测链路模拟测试栈

本文档说明 `docker-compose.observability.yml` 的启动、验证、排障与 DDL 导出流程。

## 架构

```
Redis (7-alpine, JuiceFS 元数据引擎)
RustFS (S3 兼容对象存储, :9000 API / :9001 控制台)
  └─[obs-juicefs-format 一次性 format]
    JuiceFS Client (juicedata/mount:ce-v1.3.1, FUSE 挂载 + 持续读写压流)
      └─[prometheus scrape /metrics:9567, job_name=JuicefsMount, 15s]─▶
        OTel Collector (otelcol-contrib:0.154.0)
          └─[dorisexporter / Stream Load HTTP :8030]─▶
            Doris FE (:8030 HTTP, :9030 MySQL)
              └─▶ Doris BE (storage)
```

JuiceFS 挂载依赖 Redis（元数据引擎）+ RustFS（S3 兼容对象存储后端）；`obs-juicefs-format` 一次性
执行 `juicefs format` 创建文件系统后退出，`obs-juicefs` 常驻挂载并跑一个读写循环（write/read/list/
delete）以产生真实的 FUSE 操作、对象存储请求、缓存命中等指标。JuiceFS client 用
`--metrics=0.0.0.0:9567` 暴露 `/metrics`（与生产 `OtelScrapeConfigBuilder` 默认路径一致），
otelcol 的 `job_name` 固定为 `JuicefsMount`（对齐生产角色名），使落库 `service_name` 与
`docs/monitoring/juicefs-otel-verification.md` 的验证 SQL 保持一致。

| 容器 | 用途 | 宿主端口 |
|---|---|---|
| obs-doris-fe | Doris FE，SQL 查询/管理入口 | 8030, 9030 |
| obs-doris-be | Doris BE，数据存储与计算 | 8040 |
| obs-redis | JuiceFS 元数据引擎 | — |
| obs-rustfs-init | 一次性初始化容器，chown `/data` 卷给 RustFS 非 root 进程（UID/GID 10001） | — |
| obs-rustfs | RustFS，JuiceFS 数据后端（S3 兼容对象存储） | **9000**（S3 API）, **9001**（控制台） |
| obs-juicefs-format | 一次性初始化容器，`juicefs format` 创建文件系统后退出 | — |
| obs-juicefs | JuiceFS 挂载 + 持续读写压流容器，暴露 `:9567/metrics` | — |
| obs-doris-init | 一次性初始化容器，等待 BE 向 FE 注册完成后退出 | — |
| obs-otelcol | OTel Collector，采集 JuiceFS 指标写入 Doris | 8888 (self-metrics) |

版本对应 `package/manifest.json`：Doris 4.0.5、otelcol-contrib 0.154.0、JuiceFS 1.3.1
（镜像 `juicedata/mount:ce-v1.3.1`）。RustFS 固定 `1.0.0-beta.8`（与既有 rustfs 沙箱验证一致，见
`docs/monitoring/rustfs-otel-verification.md`）。

## 前置条件

- Docker Desktop 已运行（Mac 上 Doris BE 依赖的 `vm.max_map_count` 由 Docker Desktop VM 满足）
- Linux 裸宿主需额外执行：`sudo sysctl -w vm.max_map_count=2000000`
- JuiceFS 挂载容器（`obs-juicefs`/`obs-juicefs-format`）需要 FUSE：`cap_add: SYS_ADMIN` +
  `/dev/fuse` 设备，Docker Desktop for Mac 已内置支持；Linux 裸宿主需确认 `/dev/fuse` 存在。

## 启动

```bash
# 从仓库根目录执行
docker compose -f deploy/compose/docker-compose.observability.yml up -d

# 查看启动状态（obs-doris-init 退出码 0 = BE 注册完成，是 collector 启动的前提）
docker compose -f deploy/compose/docker-compose.observability.yml ps
```

预计首次启动耗时：Doris BE 注册 ~1-2 分钟，JuiceFS format + 挂载约 10-20 秒，总计约 3-5 分钟。

## 验证链路

```bash
# 1. JuiceFS 指标端点可达（应返回 Prometheus 文本格式，含 juicefs_* 指标）
docker compose -f deploy/compose/docker-compose.observability.yml exec obs-juicefs \
  wget -qO- http://127.0.0.1:9567/metrics | head -20

# 1b. RustFS 控制台可达（人工验证对象存储后端正常）
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9001

# 2. collector self-metrics 在线（sent > 0 表示数据已在流动）
curl -s http://localhost:8888/metrics | grep -E 'otelcol_exporter_sent|otelcol_exporter_send_failed'

# 3. Doris otel 库表已自动建好（dorisexporter create_schema=true 的产物）
mysql -h 127.0.0.1 -P 9030 -u root -e "SHOW TABLES FROM otel"

# 4. JuiceFS 指标已落库（约等 2-3 个抓取周期，即 30-45s 后再查）
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT metric_name, COUNT(*) c FROM otel.otel_metrics_gauge GROUP BY metric_name ORDER BY c DESC LIMIT 15"

# 4b. histogram 分位数表(JuiceFS 延迟/吞吐指标)
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT DISTINCT metric_name FROM otel.otel_metrics_histogram WHERE metric_name LIKE 'juicefs%'"

# 4c. service_name 是否落为生产同名角色 JuicefsMount（验证 SQL 复用前提）
mysql -h 127.0.0.1 -P 9030 -u root \
  -e "SELECT DISTINCT service_name FROM otel.otel_metrics_gauge WHERE service_name = 'JuicefsMount'"
```

**成功判据**：步骤 1 返回 Prometheus 文本（含 `juicefs_uptime` 等）；步骤 3 列出 `otel_metrics_gauge` 等表；步骤 4 查到含 `juicefs_*` 的 `metric_name` 行；步骤 4b 查到 `juicefs_fuse_ops_durations_histogram_seconds`；步骤 4c 查到一行 `JuicefsMount`。完整验证记录见
`docs/monitoring/juicefs-otel-verification.md`（回填该文档的“待回填”占位）。

**已知限制（沙箱级，非本链路专属）**：单 BE + `replication_num=1` 在高频并发 Stream Load
（otelcol 4 consumers × 多张表 × 15s 抓取间隔）下，个别 tablet 曾出现 Doris 查询侧报
`fail to find path in version_graph` 且不会自愈（重启 BE 也无效，写入侧 publish version
仍持续成功，`otelcol_exporter_sent_metric_points` 正常增长）。这是该本地沙箱的已知脆弱点，
不是 JuiceFS/otelcol 配置本身的问题；遇到时优先 `down -v` 重建（清空卷），必要时降低压流频率
（调大 `obs-juicefs` 读写循环里的 `sleep` 间隔）。

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

**JuiceFS mount 容器一直 restart**：先看日志确认错误类型：
```bash
docker compose -f deploy/compose/docker-compose.observability.yml logs obs-juicefs
```
- FUSE 相关权限报错 → 确认 `cap_add: SYS_ADMIN` + `/dev/fuse` 设备已生效（见前置条件）。
- 连接 Redis/RustFS 失败 → 确认 `obs-juicefs-format` 已成功退出（`docker compose ... ps` 查看退出码 0）。

**Doris FE 启动报 IP mismatch**：清除卷后重建（`docker compose ... down -v`），固定 IP 与 volume 内 priority_networks 不一致时会报此错。

**Doris arm64 镜像问题**：若 FE/BE 在 Apple Silicon 上无法启动，可在 compose 中给 FE/BE 加 `platform: linux/amd64` 走模拟层（需重启 Docker Desktop）。

## 停止 / 清理

```bash
# 停止并保留数据卷
docker compose -f deploy/compose/docker-compose.observability.yml down

# 停止并清除所有数据（完全重置）
docker compose -f deploy/compose/docker-compose.observability.yml down -v
```
