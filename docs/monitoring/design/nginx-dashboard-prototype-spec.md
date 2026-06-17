# Nginx 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Nginx 1.30.2
> **数据源**：exporter `nginx-prometheus-exporter`（基于 `stub_status`，默认 `:9113/metrics`）
> **参考 Grafana 看板**：[NGINX exporter](https://grafana.com/grafana/dashboards/12708) (ID 12708)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Nginx.json`（4 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> nginx-prometheus-exporter :9113
                                             └──HTTP(stub_status)──> Nginx :80/stub_status
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 Nginx（stub_status）指标特点与黄金信号边界

- **数据源是 `stub_status`**：`nginx-prometheus-exporter` 抓取 Nginx 内置 `stub_status`，仅导出 **连接数与累计请求数** 这一小组指标：`nginx_up`、`nginx_connections_{active,reading,writing,waiting,accepted,handled}`、`nginx_http_requests_total`。
- **⚠️ 黄金信号天然只覆盖一半**：stub_status **不暴露请求延迟、也不暴露 HTTP 状态码分布（5xx 错误率）**。因此本看板只能覆盖 **Traffic（流量）** 与 **Saturation（连接饱和度）** 两象限；**Latency 与 Errors 物理上无法从该数据源获取**。
- **★ 补齐 Latency/Errors 的唯一途径**：部署 `prometheus-nginxlog-exporter`（解析 access log，导出 `nginx_http_response_count_total{status}` 与 `nginx_http_response_time_seconds` 直方图）或切到 Nginx Plus API。本 spec 在 §5.5 给出补强方案，但**不纳入本原型必做范围**（数据源不同，需额外部署）。
- **单标签 `instance`**：每个 Nginx 实例一个 exporter target。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

Nginx 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `singlestat`（nginx_up） | `<StatusStatPanel>` | `1` → 绿 "Up"，`0` → 红 "Down" |
| `graph`（accepted/handled） | `<Line>` 2 系列 | 二者差值 = 被拒绝连接 |
| `graph`（active 分解） | `<Area>` 堆叠 | reading/writing/waiting |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(nginx_up, instance)` | `.+`（全选） | 多选下拉 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> 速率窗口固定 `[5m]`（catalog 用 `irate(...[5m])`），不暴露 Interval 下拉。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 4 面板全保留 + 补 2 个派生 stat（连接利用、拒绝连接）= **6 面板**（小盘，无需裁剪；Latency/Errors 受数据源限制见 §5.5）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]                [Last 1h▼]   [🔄 30s▼]            │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 状态 Stat（高度 80px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ N01 NGINX Status │ N02 Active Conns │ N03 Dropped Conns│
│ [Up/Down 染色]   │ [当前活跃连接]   │ [accepted-handled]│
└──────────────────┴──────────────────┴──────────────────┘

行 R2 — 流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ N04 Total Requests (RPS)   │ N05 Processed Connections  │
│ [Line]                     │ [Line accepted/handled]    │
└────────────────────────────┴────────────────────────────┘

行 R3 — 连接饱和度 Saturation（高度 200px）
┌─────────────────────────────────────────────────────────┐
│ N06 Active Connections（active/reading/writing/waiting） │
│ [Area 堆叠]  col=24                                      │
└─────────────────────────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | —（stub_status 不支持） | 需 nginxlog-exporter 补强，见 §5.5 ★ |
| **Traffic（流量）** | N04 Total Requests、N05 Processed Connections | RPS 与连接处理速率 |
| **Errors（错误）** | N03 Dropped Connections（accepted−handled） | stub_status 唯一可得的错误近似（HTTP 5xx 需 §5.5 补强） |
| **Saturation（饱和度）** | N02 Active Conns、N06 Active Connections 分解 | 连接占用与读写/等待状态 |

> ⚠️ 诚实声明：本数据源下 **Latency 完全缺失、Errors 仅有连接级近似**。完整四象限需 §5.5 的日志 exporter 方案。

---

### 5.1 R1 — 状态 Stat

#### N01 NGINX Status

| 属性 | 值 |
|---|---|
| 标题 | NGINX Status |
| 图表类型 | `<StatusStatPanel>`（`<Statistic>` + `<Badge>`） |
| Query 类型 | instant query |
| PromQL | `nginx_up{instance=~"$instance"}` |
| 阈值规则 | `= 1` → 绿 "Up"；`= 0` → 红 "Down" |

#### N02 Active Connections

| 属性 | 值 |
|---|---|
| 标题 | Active Connections |
| 图表类型 | `<Statistic>` |
| PromQL | `sum(nginx_connections_active{instance=~"$instance"})` |

#### N03 Dropped Connections ★（派生）

| 属性 | 值 |
|---|---|
| 标题 | Dropped Connections /s |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(irate(nginx_connections_accepted{instance=~"$instance"}[5m])) - sum(irate(nginx_connections_handled{instance=~"$instance"}[5m]))` |
| 阈值 | `= 0` → 绿；`> 0` → 红（accepted 与 handled 的差即被丢弃的连接，唯一连接级错误信号） |

---

### 5.2 R2 — 流量

#### N04 Total Requests

| 属性 | 值 |
|---|---|
| 标题 | Total Requests |
| 图表类型 | `<Line>` 1 系列 |
| Query 类型 | range query |
| PromQL | `sum(irate(nginx_http_requests_total{instance=~"$instance"}[5m]))` |
| y 轴 | `req/s` |
| 系列颜色 | `#1677ff` |

#### N05 Processed Connections

| 属性 | 值 |
|---|---|
| 标题 | Processed Connections |
| 图表类型 | `<Line>` 2 系列 |
| PromQL (accepted) | `irate(nginx_connections_accepted{instance=~"$instance"}[5m])` |
| PromQL (handled) | `irate(nginx_connections_handled{instance=~"$instance"}[5m])` |
| 系列 | `Accepted`（蓝）、`Handled`（绿）；二者背离即出现连接丢弃 |
| y 轴 | `conn/s` |

---

### 5.3 R3 — 连接饱和度

#### N06 Active Connections

| 属性 | 值 |
|---|---|
| 标题 | Active Connections |
| 图表类型 | `<Area>` 堆叠 4 系列 |
| Query 类型 | range query |
| PromQL (active) | `nginx_connections_active{instance=~"$instance"}` |
| PromQL (reading) | `nginx_connections_reading{instance=~"$instance"}` |
| PromQL (writing) | `nginx_connections_writing{instance=~"$instance"}` |
| PromQL (waiting) | `nginx_connections_waiting{instance=~"$instance"}` |
| 系列 | `Active`（蓝）、`Reading`（青）、`Writing`（橙）、`Waiting`（灰） |
| 说明 | `active = reading + writing + waiting`；堆叠展示连接状态构成 |

---

### 5.4 R4 —（预留）

本数据源无更多指标，R4 留空；如启用 §5.5 补强，可在此追加 Latency / Status Code 面板。

---

### 5.5 ★ Latency / Errors 补强方案（数据源不同，非本原型必做）

selection.md 选型备注明确：**该看板缺少延迟面板**。stub_status 物理上无法提供延迟与 HTTP 状态码，需换/加数据源：

| 补强项 | 方案 | 新增面板 |
|---|---|---|
| **请求延迟 P50/P95/P99** | 部署 `prometheus-nginxlog-exporter` 解析 access log，配置 `histogram_buckets` | `histogram_quantile(0.99, sum(rate(nginx_http_response_time_seconds_bucket[5m])) by (le))` |
| **HTTP 状态码 / 错误率** | 同上，导出 `nginx_http_response_count_total{status}` | `sum(rate(nginx_http_response_count_total{status=~"5.."}[5m]))` by status，2xx/3xx/4xx/5xx 配色（复用 APISIX `STATUS_CODE_COLORS`） |
| **按 URI/method 维度** | nginxlog-exporter 标签 | RPS by path / by method |

> 落地建议：若 DataSophon 部署 Nginx 时一并下发 nginxlog-exporter，则在本看板追加 R4「延迟」+ R5「状态码」两行，达成完整四象限。本原型先交付 stub_status 可得的 Traffic + Saturation，并显式标注 Latency/Errors 的缺口与补强路径。

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**。补强方案启用时复用 APISIX spec 的 `STATUS_CODE_COLORS`。

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

```ts
interface NginxDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    instance: string;   // 正则，exporter 地址
    // 注：固定 [5m] 窗口，无 interval 变量
  };
}
```

---

## 8. 组件树结构

```
<NginxDashboard>
  ├── <DashboardToolbar>              # 仅 Instance 多选 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # 状态 Stat（3 个）
  │   ├── <StatusStatPanel N01>       # NGINX Status（Up/Down）
  │   ├── <StatPanel N02>             # Active Connections
  │   └── <StatPanel N03>             # Dropped Connections ★（派生，>0 红）
  │
  ├── <Row R2>                        # 流量
  │   ├── <TimeSeriesPanel N04>       # Total Requests (RPS)
  │   └── <TimeSeriesPanel N05>       # Processed Connections
  │
  └── <Row R3>                        # 连接饱和度
      └── <AreaPanel N06>             # Active Connections（堆叠 4 系列）

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / StatusStatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/NginxMonitor/
  ├── index.tsx                     # 页面容器（3 行布局）
  ├── panelQueries.ts               # PanelDef（6 个面板）
  ├── hooks/useNginxDashboard.ts
  ├── panels/                       # 引用 `monitor/_shared/panels/`
  ├── toolbar/                      # 引用 `_shared/DashboardToolbar.tsx`（children 注入 Instance 选择器）
  ├── mock/nginxMockData.ts
  └── utils/                        # 无此目录 — 直接从 `../../_shared/charts/` import
```

### 9.2 PromQL 变量替换

```ts
function replaceNginxVars(promql: string, vars: NginxDashboardQueryParams['variables']): string {
  return promql.replace(/\$instance/g, vars.instance || '.+');
}
```

### 9.3 Mock 数据要求

`nginxMockData.ts` 覆盖 6 面板：
- N01 Status `1`（绿 Up）、N02 Active `512`、N03 Dropped `0`（绿）
- N04 Total Requests: 1500–3000 req/s 波动
- N05 Processed: accepted ≈ handled ≈ 200 conn/s（重合，无丢弃）
- N06 Active Connections: active ≈ 512（waiting ≈ 480、reading ≈ 5、writing ≈ 27 堆叠）

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

- [ ] 全部 6 个面板（N01-N06）按 §4 布局渲染（3 行 24 列 Grid）
- [ ] N01 用 `<StatusStatPanel>`（1 绿 "Up" / 0 红 "Down"）
- [ ] N03 Dropped Connections 为派生面板（accepted−handled），>0 染红
- [ ] N06 Active Connections 用 `<Area>` 堆叠 4 系列（active/reading/writing/waiting）
- [ ] §5.0 与 §5.5 明确标注 **Latency 缺失、Errors 仅连接级近似**，并给出 nginxlog-exporter 补强路径
- [ ] 工具栏：仅 Instance 多选 + 时间范围 + 刷新
- [ ] 在 1280px 宽度下 3 行布局无横向滚动条
- [ ] golden signals 覆盖说明诚实：本数据源仅 Traffic + Saturation，Latency/Errors 标注为补强项
