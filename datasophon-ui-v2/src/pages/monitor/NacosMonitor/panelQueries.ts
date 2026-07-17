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

export const NACOS_JOB_FILTER = '^NacosServer$';

export const ALL_PANEL_IDS = Array.from(
  { length: 18 },
  (_, index) => `N${String(index + 1).padStart(2, '0')}`,
);

/**
 * Nacos 3.2.2 原生 Prometheus 指标对应的 Doris 查询描述符。
 *
 * Prometheus Timer 经 OTel Collector 写入 otel_metrics_summary，HTTP QPS、平均延迟和
 * 5xx 比例分别通过 count/sum 字段的 rate 计算；其余 Nacos/JVM 指标均为 gauge。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // R1 — 概览
  N01: { type: 'node-count', roleName: 'NacosServer' },
  N02: {
    type: 'instant',
    metric: 'nacos_monitor',
    agg: 'max',
    filters: { module: 'naming', name: 'serviceCount' },
  },
  N03: {
    type: 'instant',
    metric: 'nacos_monitor',
    agg: 'max',
    filters: { module: 'naming', name: 'ipCount' },
  },
  N04: {
    type: 'instant',
    metric: 'nacos_monitor',
    agg: 'max',
    filters: { module: 'config', name: 'configCount' },
  },
  N05: {
    type: 'instant',
    metric: 'nacos_monitor',
    agg: 'sum',
    filters: { module: 'core', name: 'longConnection' },
  },
  // N06 复用 N07 的最新时间桶，由 hook 派生当前 HTTP QPS，不重复发起 Doris 查询。

  // R2 — 请求
  N07: {
    type: 'multi-range',
    queries: [
      {
        label: 'HTTP QPS',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
      },
    ],
  },
  N08: {
    type: 'multi-range',
    queries: [
      {
        label: 'HTTP Latency',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'sum',
        rate: '1m',
        denominatorMetric: 'http_server_requests_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
        scale: 1000,
      },
    ],
  },
  N09: {
    type: 'multi-range',
    queries: [
      {
        label: 'HTTP 5xx',
        metric: 'http_server_requests_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
        filtersRegex: { status: '5..' },
        denominatorMetric: 'http_server_requests_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
        scale: 100,
      },
    ],
  },
  N10: {
    type: 'multi-range',
    queries: [
      {
        label: 'gRPC Max',
        metric: 'grpc_server_requests_seconds_max',
        scale: 1000,
      },
    ],
  },

  // R3 — Nacos 核心业务
  N11: {
    type: 'multi-range',
    queries: [
      {
        label: 'Active',
        metric: 'grpc_server_executor',
        filters: { module: 'core', name: 'activeCount' },
        groupBy: ['type'],
      },
      {
        label: 'Queued',
        metric: 'grpc_server_executor',
        filters: { module: 'core', name: 'inQueueTaskCount' },
        groupBy: ['type'],
      },
    ],
  },
  N12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Get Config',
        metric: 'nacos_monitor',
        filters: { module: 'config', name: 'getConfig' },
      },
      {
        label: 'Publish',
        metric: 'nacos_monitor',
        filters: { module: 'config', name: 'publish' },
      },
      {
        label: 'Long Polling',
        metric: 'nacos_monitor',
        filters: { module: 'config', name: 'longPolling' },
      },
    ],
  },
  N13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Average',
        metric: 'nacos_monitor',
        filters: { module: 'naming', name: 'avgPushCost' },
      },
      {
        label: 'Maximum',
        metric: 'nacos_monitor',
        filters: { module: 'naming', name: 'maxPushCost' },
      },
    ],
  },
  N14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Failed Push',
        metric: 'nacos_monitor',
        filters: { module: 'naming', name: 'failedPush' },
      },
      {
        label: 'Pending Tasks',
        metric: 'nacos_monitor',
        filters: { module: 'naming', name: 'pushPendingTaskCount' },
      },
    ],
  },

  // R4 — JVM / 资源
  N15: {
    type: 'multi-range',
    queries: [
      { label: 'System', metric: 'system_cpu_usage', scale: 100 },
      { label: 'Process', metric: 'process_cpu_usage', scale: 100 },
    ],
  },
  N16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Heap',
        metric: 'jvm_memory_used_bytes',
        filters: { area: 'heap' },
        denominatorMetric: 'jvm_memory_max_bytes',
        denominatorFilters: { area: 'heap' },
        scale: 100,
      },
    ],
  },
  N17: {
    type: 'multi-range',
    queries: [
      { label: 'Live', metric: 'jvm_threads_live_threads' },
      { label: 'Daemon', metric: 'jvm_threads_daemon_threads' },
    ],
  },
  N18: {
    type: 'multi-range',
    queries: [
      {
        label: 'GC Pause Max',
        metric: 'jvm_gc_pause_seconds_max',
        scale: 1000,
      },
    ],
  },
};
