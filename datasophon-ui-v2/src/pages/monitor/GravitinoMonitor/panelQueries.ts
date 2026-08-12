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

export const GRAVITINO_JOB_FILTER = '^GravitinoServer$';

export const ALL_PANEL_IDS = Array.from(
  { length: 20 },
  (_, index) => `G${String(index + 1).padStart(2, '0')}`,
);

/**
 * Apache Gravitino 原生 Prometheus 指标对应的 Doris 查询描述符。
 *
 * Jetty/JVM 指标为 gauge，HTTP 状态类/健康探针/实体存储读写为 counter（sum 表），
 * HTTP 请求延迟经 Dropwizard Timer 落 summary 表，通过 quantile 字段取 p50/p99。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // R1 — 概览
  G01: { type: 'node-count', roleName: 'GravitinoServer' },
  // G02 复用 G07 的最新时间桶，由 hook 派生当前 HTTP QPS，不重复发起 Doris 查询。
  G03: {
    type: 'instant',
    metric: 'gravitino_server_http_server_busy_thread_num',
    agg: 'max',
    denominatorMetric: 'gravitino_server_http_server_max_thread_num',
    scale: 100,
  },
  G04: {
    type: 'instant',
    metric: 'gravitino_server_http_server_queued_request_num',
    agg: 'sum',
  },
  G05: {
    type: 'instant',
    metric: 'gravitino_relational_store_datasource_active_connections',
    agg: 'sum',
  },
  G06: {
    type: 'instant',
    metric: 'jvm_heap_usage',
    agg: 'max',
    scale: 100,
  },

  // R2 — HTTP 服务
  G07: {
    type: 'multi-range',
    queries: [
      {
        label: '1xx',
        metric: 'gravitino_server_1xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: '2xx',
        metric: 'gravitino_server_2xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: '3xx',
        metric: 'gravitino_server_3xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: '4xx',
        metric: 'gravitino_server_4xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: '5xx',
        metric: 'gravitino_server_5xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },
  G08: {
    type: 'multi-range',
    queries: [
      {
        label: '2xx',
        metric: 'gravitino_server_2xx_responses_total',
        table: 'sum',
        rate: '1m',
        groupBy: ['operation'],
      },
    ],
  },
  G09: {
    type: 'multi-range',
    queries: [
      {
        label: '4xx',
        metric: 'gravitino_server_4xx_responses_total',
        table: 'sum',
        rate: '1m',
        groupBy: ['operation'],
      },
      {
        label: '5xx',
        metric: 'gravitino_server_5xx_responses_total',
        table: 'sum',
        rate: '1m',
        groupBy: ['operation'],
      },
    ],
  },
  // 按 operation 分组展示 p99（而非 p50+p99 两条全局曲线）：Gravitino 单个 timer 下有 125 个
  // operation，若不分组，summary quantile 查询会把绝大多数空闲 operation 的 0 值和真正有流量
  // 的那一路一起 AVG，稀释成接近 0；与 G08/G09 保持同一视觉语言（单一分位数 + groupBy）。
  G10: {
    type: 'multi-range',
    queries: [
      {
        label: 'p99',
        metric: 'gravitino_server_http_request_duration_seconds',
        table: 'summary',
        field: 'quantile',
        quantile: 0.99,
        scale: 1000,
        groupBy: ['operation'],
      },
    ],
  },

  // R3 — Jetty 线程池
  G11: {
    type: 'multi-range',
    queries: [
      { label: 'Busy', metric: 'gravitino_server_http_server_busy_thread_num' },
      { label: 'Idle', metric: 'gravitino_server_http_server_idle_thread_num' },
      { label: 'Total', metric: 'gravitino_server_http_server_total_thread_num' },
      { label: 'Max', metric: 'gravitino_server_http_server_max_thread_num' },
    ],
  },
  G12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Live',
        metric: 'gravitino_server_health_live_2xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: 'Ready',
        metric: 'gravitino_server_health_ready_2xx_responses_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },

  // R4 — 实体存储
  G13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Active',
        metric: 'gravitino_relational_store_datasource_active_connections',
      },
      {
        label: 'Idle',
        metric: 'gravitino_relational_store_datasource_idle_connections',
      },
      {
        label: 'Max',
        metric: 'gravitino_relational_store_datasource_max_connections',
      },
    ],
  },
  G14: {
    type: 'multi-range',
    queries: [
      {
        label: 'List Metalakes',
        metric: 'gravitino_relational_store_listMetalakes_success_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: 'Get Metalake',
        metric: 'gravitino_relational_store_getMetalakeByIdentifier_success_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },
  G15: {
    type: 'multi-range',
    queries: [
      {
        label: 'List Metalakes',
        metric: 'gravitino_relational_store_listMetalakes_failure_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: 'Get Metalake',
        metric: 'gravitino_relational_store_getMetalakeByIdentifier_failure_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },
  G16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Delete Table Metas',
        metric:
          'gravitino_relational_store_deleteTableMetasByLegacyTimeline_success_total',
        table: 'sum',
        rate: '1m',
      },
      {
        label: 'Delete Fileset Versions',
        metric:
          'gravitino_relational_store_deleteFilesetVersionsByRetentionCount_success_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },

  // R5 — JVM
  G17: {
    type: 'multi-range',
    queries: [
      { label: 'Used', metric: 'jvm_heap_used' },
      { label: 'Committed', metric: 'jvm_heap_committed' },
      { label: 'Max', metric: 'jvm_heap_max' },
    ],
  },
  G18: {
    type: 'multi-range',
    queries: [
      {
        label: 'Young',
        metric: 'jvm_G1_Young_Generation_count',
        rate: '1m',
      },
      {
        label: 'Old',
        metric: 'jvm_G1_Old_Generation_count',
        rate: '1m',
      },
    ],
  },
  G19: {
    type: 'multi-range',
    queries: [
      {
        label: 'Young',
        metric: 'jvm_G1_Young_Generation_time',
        rate: '1m',
      },
      {
        label: 'Old',
        metric: 'jvm_G1_Old_Generation_time',
        rate: '1m',
      },
    ],
  },
  G20: {
    type: 'multi-range',
    queries: [
      { label: 'Non-Heap', metric: 'jvm_non_heap_used' },
      { label: 'Metaspace', metric: 'jvm_pools_Metaspace_used' },
      { label: 'Direct', metric: 'jvm_direct_used' },
    ],
  },
};
