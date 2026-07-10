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

import { request } from '@umijs/max';
import type { PrometheusMatrix, PrometheusVector } from './charts/promql';
import type { ApiResponse } from './service';

/** 传给后端 query 接口的 instant 参数 */
export interface DorisInstantParams {
  metric: string;
  agg?: 'sum' | 'max' | 'count';
  scale?: number;
  instance?: string;
  job?: string;
  time?: number;
  clusterId?: number;
  /** OTel 表选择:gauge(默认)、sum(counter/_total 类) */
  table?: 'gauge' | 'sum';
  /** 等值属性过滤(key 须在白名单：group/type/mode/path/device) */
  filters?: Record<string, string>;
  /** 不等属性过滤 */
  filtersNe?: Record<string, string>;
  /** 正则属性过滤 */
  filtersRegex?: Record<string, string>;
  /** 正则不匹配属性过滤 */
  filtersNotRegex?: Record<string, string>;
}

/** 传给后端 query_range 接口的 range 参数 */
export interface DorisRangeParams {
  metric: string;
  rateWindow?: '1m' | '2m' | '5m' | '15m';
  scale?: number;
  instance?: string;
  job?: string;
  start: number;
  end: number;
  step: number;
  clusterId?: number;
  /**
   * OTel 表选择：gauge（默认）、sum（counter/_total 类）、summary（Dropwizard timer quantile）、
   * histogram（OTel HistogramDataPoint 分位数，如 apisix_http_latency）
   */
  table?: 'gauge' | 'sum' | 'summary' | 'histogram';
  /** summary/histogram 表查询时的分位数（0~1），如 0.5 / 0.9 / 0.99，默认 0.5 */
  quantile?: number;
  /** histogram 表查询字段：quantile(默认)、count rate 或 sum rate */
  field?: 'quantile' | 'count' | 'sum';
  /** 等值属性过滤（key 须在白名单：group/type/mode/path/device） */
  filters?: Record<string, string>;
  /** 不等属性过滤 */
  filtersNe?: Record<string, string>;
  /** 正则属性过滤 */
  filtersRegex?: Record<string, string>;
  /** 正则不匹配属性过滤 */
  filtersNotRegex?: Record<string, string>;
  /** 额外 GROUP BY 维度（如 ['path']、['mode']） */
  groupBy?: string[];
}

// ── 描述符类型 ──────────────────────────────────────────────────────────────────

/** instant 面板描述符 */
export interface DorisInstantDescriptor {
  type: 'instant';
  metric: string;
  agg?: 'sum' | 'max' | 'count';
  scale?: number;
  /** OTel 表选择:gauge(默认)、sum(counter/_total 类,如 apisix_http_requests_total) */
  table?: 'gauge' | 'sum';
  /** 等值属性过滤 */
  filters?: Record<string, string>;
  /** 不等属性过滤 */
  filtersNe?: Record<string, string>;
  /** 正则属性过滤 */
  filtersRegex?: Record<string, string>;
  /** 正则不匹配属性过滤 */
  filtersNotRegex?: Record<string, string>;
  /** 可选：分母指标，用于 instant 比值 */
  denominatorMetric?: string;
  denominatorTable?: 'gauge' | 'sum';
  denominatorFilters?: Record<string, string>;
  denominatorFiltersNe?: Record<string, string>;
  denominatorFiltersRegex?: Record<string, string>;
  denominatorFiltersNotRegex?: Record<string, string>;
}

/** 节点计数面板描述符（查角色注册表，替代 PromQL count(up==1)） */
export interface DorisNodeCountDescriptor {
  type: 'node-count';
  /** 角色名，与 meta DDL roles[].name 一致（如 "DorisFE" / "DorisBE"） */
  roleName: string;
}

/**
 * multi-range 面板中单条查询。
 *
 * 若指定 denominatorMetric，hook 将在客户端计算 metric / denominatorMetric * scale 比值，
 * 实现堆占比、错误率、磁盘占比等派生指标。
 */
export interface DorisRangeQuery {
  label: string;
  metric: string;
  rate?: '1m' | '2m' | '5m' | '15m';
  scale?: number;
  /** OTel 表选择 */
  table?: 'gauge' | 'sum' | 'summary' | 'histogram';
  quantile?: number;
  field?: 'quantile' | 'count' | 'sum';
  /** 等值属性过滤 */
  filters?: Record<string, string>;
  /** 不等属性过滤 */
  filtersNe?: Record<string, string>;
  /** 正则属性过滤 */
  filtersRegex?: Record<string, string>;
  /** 正则不匹配属性过滤 */
  filtersNotRegex?: Record<string, string>;
  /** 额外 GROUP BY 维度 */
  groupBy?: string[];
  /** 可选：分母指标（留空时直接返回原始序列） */
  denominatorMetric?: string;
  denominatorTable?: 'gauge' | 'sum' | 'summary' | 'histogram';
  denominatorField?: 'quantile' | 'count' | 'sum';
  denominatorFilters?: Record<string, string>;
  denominatorFiltersNe?: Record<string, string>;
  denominatorFiltersRegex?: Record<string, string>;
  denominatorFiltersNotRegex?: Record<string, string>;
}

/** multi-range 面板描述符（每条 series 一个 query） */
export interface DorisMultiRangeDescriptor {
  type: 'multi-range';
  queries: DorisRangeQuery[];
}

export type DorisPanelDescriptor =
  | DorisInstantDescriptor
  | DorisMultiRangeDescriptor
  | DorisNodeCountDescriptor;

// ── 参数序列化辅助 ────────────────────────────────────────────────────────────

/**
 * 将过滤 Map 序列化为 "key:value,key:value" 格式。
 * 后端 Controller 的 parseFilters() 期望此格式。
 */
function filtersToString(filters?: Record<string, string>): string | undefined {
  if (!filters || Object.keys(filters).length === 0) return undefined;
  return Object.entries(filters)
    .map(([k, v]) => `${k}:${v}`)
    .join(',');
}

/** 将 string[] 序列化为逗号分隔字符串。 */
function groupByToString(groupBy?: string[]): string | undefined {
  if (!groupBy || groupBy.length === 0) return undefined;
  return groupBy.join(',');
}

// ── API 函数 ──────────────────────────────────────────────────────────────────

/** 查询指定指标的 instant 快照（对应 Prometheus /api/v1/query） */
export function queryDorisInstant(params: DorisInstantParams) {
  const { filters, filtersNe, filtersRegex, filtersNotRegex, ...rest } = params;
  return request<ApiResponse<PrometheusVector>>(
    '/observability/otel/metrics/query',
    {
      method: 'GET',
      params: {
        ...rest,
        filters: filtersToString(filters),
        filtersNe: filtersToString(filtersNe),
        filtersRegex: filtersToString(filtersRegex),
        filtersNotRegex: filtersToString(filtersNotRegex),
      },
    },
  );
}

/** 查询指定指标的时间序列（对应 Prometheus /api/v1/query_range） */
export function queryDorisRange(params: DorisRangeParams) {
  const {
    filters,
    filtersNe,
    filtersRegex,
    filtersNotRegex,
    groupBy,
    rateWindow,
    ...rest
  } = params;
  return request<ApiResponse<PrometheusMatrix>>(
    '/observability/otel/metrics/query_range',
    {
      method: 'GET',
      params: {
        ...rest,
        rateWindow,
        filters: filtersToString(filters),
        filtersNe: filtersToString(filtersNe),
        filtersRegex: filtersToString(filtersRegex),
        filtersNotRegex: filtersToString(filtersNotRegex),
        groupBy: groupByToString(groupBy),
      },
    },
  );
}

/** 查询指标可用的 instance/job 标签值，用于工具栏下拉 */
export function fetchDorisLabels(metric: string, clusterId = 1, job?: string) {
  return request<
    ApiResponse<{
      instances: string[];
      jobs: string[];
      attributes?: Record<string, string[]>;
    }>
  >(
    '/observability/otel/metrics/labels',
    { method: 'GET', params: { metric, clusterId, job } },
  );
}

/** 查询集群内指定角色的 RUNNING 节点数（替代 PromQL count(up==1)） */
export function fetchDorisNodeCount(roleName: string, clusterId = 1) {
  return request<ApiResponse<number>>('/observability/otel/metrics/nodes', {
    method: 'GET',
    params: { roleName, clusterId },
  });
}
