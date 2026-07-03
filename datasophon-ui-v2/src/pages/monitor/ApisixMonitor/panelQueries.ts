/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import type { DorisPanelDescriptor } from '../_shared/dorisService';

export type { DorisPanelDescriptor as ApisixPanelDescriptor };

/**
 * APISIX 看板面板描述符,取数源:docs/monitoring/panel-catalog/APISIX.json(17 面板 PromQL 目录)。
 *
 * 与目录的两点偏差(均已用真实 standalone APISIX 抓取数据核实):
 * - **无 etcd 面板**:standalone 模式(deployment.role_data_plane.config_provider=yaml)不依赖 etcd,
 *   `apisix_etcd_*` 指标不存在,原 "Etcd modify indexes" / "Etcd reachable" 两个面板已移除。
 * - **按 code/service/route 分组的面板改为显式枚举值查询**:`useDorisDashboardData` 的
 *   `mergeNamedSeries` 按 query 级 `label` 合并,`groupBy` 产生的多个 series 会被压扁成一条同名线
 *   (DorisMonitor DO-C03 磁盘占比面板已是这个简化先例)。像 "RPS by status code" 这种值域不固定的
 *   分组无法在此框架下正确拆分成多条线,因此不做(会渲染出叠在一起的乱线);像连接状态
 *   (active/reading/writing/waiting)、带宽方向(ingress/egress)这种值域固定的维度,改写成每个值
 *   一条显式 query(与 NexusMonitor N07 状态码面板同一手法)。
 *
 * 指标类型均已用 `curl :9091/apisix/prometheus/metrics` 核实(不能凭指标名后缀猜表):
 * - `apisix_http_requests_total` 声明为 **gauge**(尽管名字带 _total)→ table 用默认 gauge。
 * - `apisix_bandwidth` / `apisix_http_status` / `apisix_nginx_metric_errors_total` 是 counter → table:'sum'。
 * - `apisix_http_latency` 是 **histogram**(bucket_counts,非 le 标签序列)→ table:'histogram'。
 * - `apisix_nginx_http_current_connections` / `apisix_shared_dict_*` 是 gauge → 默认表。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // ── R1 instant 摘要统计 ─────────────────────────────────────────────────────
  A01: { type: 'instant', metric: 'apisix_http_requests_total' },
  A02: {
    type: 'instant',
    metric: 'apisix_nginx_http_current_connections',
    filters: { state: 'accepted' },
  },
  A03: {
    type: 'instant',
    metric: 'apisix_nginx_http_current_connections',
    filters: { state: 'handled' },
  },
  A04: {
    type: 'instant',
    metric: 'apisix_nginx_http_current_connections',
    filters: { state: 'active' },
  },

  // ── R2 状态指示(counter,0 正常) ───────────────────────────────────────────
  A05: { type: 'instant', table: 'sum', metric: 'apisix_nginx_metric_errors_total' },

  // ── R3 流量(counter rate,table=sum) ───────────────────────────────────────
  A06: {
    type: 'multi-range',
    queries: [
      { label: 'RPS', metric: 'apisix_http_status', rate: '1m', table: 'sum' },
    ],
  },
  A07: {
    type: 'multi-range',
    queries: [
      {
        label: 'ingress',
        metric: 'apisix_bandwidth',
        rate: '1m',
        table: 'sum',
        filters: { type: 'ingress' },
      },
      {
        label: 'egress',
        metric: 'apisix_bandwidth',
        rate: '1m',
        table: 'sum',
        filters: { type: 'egress' },
      },
    ],
  },

  // ── R4 延迟(histogram 分位数,p90/p95/p99) ────────────────────────────────
  A08: {
    type: 'multi-range',
    queries: [
      {
        label: 'p90',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.9,
        rate: '1m',
        filters: { type: 'request' },
      },
      {
        label: 'p95',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.95,
        rate: '1m',
        filters: { type: 'request' },
      },
      {
        label: 'p99',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.99,
        rate: '1m',
        filters: { type: 'request' },
      },
    ],
  },
  A09: {
    type: 'multi-range',
    queries: [
      {
        label: 'p90',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.9,
        rate: '1m',
        filters: { type: 'apisix' },
      },
      {
        label: 'p95',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.95,
        rate: '1m',
        filters: { type: 'apisix' },
      },
      {
        label: 'p99',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.99,
        rate: '1m',
        filters: { type: 'apisix' },
      },
    ],
  },
  A10: {
    type: 'multi-range',
    queries: [
      {
        label: 'p90',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.9,
        rate: '1m',
        filters: { type: 'upstream' },
      },
      {
        label: 'p95',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.95,
        rate: '1m',
        filters: { type: 'upstream' },
      },
      {
        label: 'p99',
        metric: 'apisix_http_latency',
        table: 'histogram',
        quantile: 0.99,
        rate: '1m',
        filters: { type: 'upstream' },
      },
    ],
  },

  // ── R5 Nginx 连接状态(值域固定,显式枚举) ────────────────────────────────
  A11: {
    type: 'multi-range',
    queries: [
      { label: 'active', metric: 'apisix_nginx_http_current_connections', filters: { state: 'active' } },
      { label: 'reading', metric: 'apisix_nginx_http_current_connections', filters: { state: 'reading' } },
      { label: 'writing', metric: 'apisix_nginx_http_current_connections', filters: { state: 'writing' } },
      { label: 'waiting', metric: 'apisix_nginx_http_current_connections', filters: { state: 'waiting' } },
    ],
  },

  // ── R6 共享字典剩余空间占比(客户端比值合成,4 个关注度较高的字典) ───────────
  A12: {
    type: 'multi-range',
    queries: [
      {
        label: 'prometheus-metrics',
        metric: 'apisix_shared_dict_free_space_bytes',
        filters: { name: 'prometheus-metrics' },
        denominatorMetric: 'apisix_shared_dict_capacity_bytes',
        denominatorFilters: { name: 'prometheus-metrics' },
        scale: 100,
      },
      {
        label: 'plugin-limit-req',
        metric: 'apisix_shared_dict_free_space_bytes',
        filters: { name: 'plugin-limit-req' },
        denominatorMetric: 'apisix_shared_dict_capacity_bytes',
        denominatorFilters: { name: 'plugin-limit-req' },
        scale: 100,
      },
      {
        label: 'plugin-limit-conn',
        metric: 'apisix_shared_dict_free_space_bytes',
        filters: { name: 'plugin-limit-conn' },
        denominatorMetric: 'apisix_shared_dict_capacity_bytes',
        denominatorFilters: { name: 'plugin-limit-conn' },
        scale: 100,
      },
      {
        label: 'balancer-ewma',
        metric: 'apisix_shared_dict_free_space_bytes',
        filters: { name: 'balancer-ewma' },
        denominatorMetric: 'apisix_shared_dict_capacity_bytes',
        denominatorFilters: { name: 'balancer-ewma' },
        scale: 100,
      },
    ],
  },
};

export const TIME_RANGE_SECONDS: Record<string, number> = {
  '5m': 300,
  '15m': 900,
  '1h': 3600,
  '6h': 21600,
  '24h': 86400,
  '7d': 604800,
};
