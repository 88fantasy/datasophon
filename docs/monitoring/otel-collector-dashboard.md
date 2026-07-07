# OTel Collector 监控看板

Collector 监控看板集成在 `Collector 控制台 > 监控` tab，不新增独立菜单入口。

当前实现以 OTel + Doris 为目标态：`docs/monitoring/dashboard-survey-design.md` 中早期 Prometheus/Grafana 迁移设想仅作为面板梳理历史参考，不再作为 Collector 看板的实现路径。

## 数据源

- 实时快照：`/ddh/api/observability/otelcol/monitor`，直接读取各 Collector 节点 `:8888/metrics`。Doris 不可用时仍可展示健康节点、队列、发送总量、失败/拒收/丢弃总量和节点明细。
- 历史趋势：`/ddh/api/v2/observability/otel/metrics/query_range`，查询 Doris OTel metrics 表中的 `otelcol_*` 指标。页面默认时间范围为 1h，复用现有时间范围和刷新控件。

## 首批面板目录

|     面板      |                                                      指标                                                       |       Doris 表        |                         聚合方式                         | 单位  |
|-------------|---------------------------------------------------------------------------------------------------------------|----------------------|------------------------------------------------------|-----|
| 健康节点 / 异常节点 | `/monitor` 节点返回                                                                                               | 无                    | 按节点计数                                                | 个   |
| 队列使用率快照     | `otelcol_exporter_queue_size` / `otelcol_exporter_queue_capacity`                                             | 实时快照                 | 节点求和后相除                                              | %   |
| 队列使用率趋势     | `otelcol_exporter_queue_size` / `otelcol_exporter_queue_capacity`                                             | `otel_metrics_gauge` | 按 `exporter` 分组，客户端逐点相除                              | %   |
| 发送速率        | `otelcol_exporter_sent_{metric_points,log_records,spans}`                                                     | `otel_metrics_sum`   | `rate(1m)`，按 `exporter` 分组                           | 条/s |
| 发送失败速率      | `otelcol_receiver_failed_{metric_points,log_records,spans}`                                                   | `otel_metrics_sum`   | `rate(1m)`，按 `receiver,transport` 分组                 | 条/s |
| 拒收 / 丢弃速率   | `otelcol_receiver_refused_*` + `otelcol_processor_dropped_*` + `otelcol_processor_filter_datapoints.filtered` | `otel_metrics_sum`   | `rate(1m)`，分别按 `receiver,transport` / `processor` 分组 | 条/s |
| 进程运行时长      | `otelcol_process_uptime`                                                                                      | `otel_metrics_sum`   | 直接展示趋势；快照取节点最大值                                      | 秒   |

## 后端约束

`OtelMetricsQueryService` 的 attribute key 仍采用白名单拼接 SQL。Collector 看板只新增常见 pipeline 维度：

- `exporter`
- `receiver`
- `processor`
- `transport`

指标值仍通过命名参数绑定，不改变现有 SQL 注入防护模型。

## 降级行为

Doris 查询失败或尚未写入 Collector 自采集指标时，趋势图显示空态；实时快照 stat 和节点表仍由 `/monitor` 独立渲染。
