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
  agg?: 'sum' | 'max';
  scale?: number;
  instance?: string;
  job?: string;
  time?: number;
  clusterId?: number;
}

/** 传给后端 query_range 接口的 range 参数 */
export interface DorisRangeParams {
  metric: string;
  rateWindow?: '1m' | '5m';
  scale?: number;
  instance?: string;
  job?: string;
  start: number;
  end: number;
  step: number;
  clusterId?: number;
  /** OTel 表选择：gauge（默认）、sum（counter/_total 类）、summary（Dropwizard timer quantile） */
  table?: 'gauge' | 'sum' | 'summary';
  /** summary 表查询时的分位数（0~1），如 0.5 / 0.99，默认 0.5 */
  quantile?: number;
}

/** instant 面板描述符 */
export interface DorisInstantDescriptor {
  type: 'instant';
  metric: string;
  agg?: 'sum' | 'max';
  scale?: number;
}

/** multi-range 面板描述符（每条 series 一个 metric） */
export interface DorisMultiRangeDescriptor {
  type: 'multi-range';
  queries: Array<{
    label: string;
    metric: string;
    rate?: '1m' | '5m';
    scale?: number;
    /** OTel 表选择：gauge（默认）、sum（counter/_total 类）、summary（Dropwizard timer quantile） */
    table?: 'gauge' | 'sum' | 'summary';
    /** summary 表查询时的分位数（0~1），默认 0.5 */
    quantile?: number;
  }>;
}

export type DorisPanelDescriptor = DorisInstantDescriptor | DorisMultiRangeDescriptor;

/** 查询指定指标的 instant 快照（对应 Prometheus /api/v1/query） */
export function queryDorisInstant(params: DorisInstantParams) {
  return request<ApiResponse<PrometheusVector>>(
    '/observability/otel/metrics/query',
    { method: 'GET', params },
  );
}

/** 查询指定指标的时间序列（对应 Prometheus /api/v1/query_range） */
export function queryDorisRange(params: DorisRangeParams) {
  return request<ApiResponse<PrometheusMatrix>>(
    '/observability/otel/metrics/query_range',
    { method: 'GET', params },
  );
}

/** 查询指标可用的 instance/job 标签值，用于工具栏下拉 */
export function fetchDorisLabels(metric: string, clusterId = 1) {
  return request<ApiResponse<{ instances: string[]; jobs: string[] }>>(
    '/observability/otel/metrics/labels',
    { method: 'GET', params: { metric, clusterId } },
  );
}
