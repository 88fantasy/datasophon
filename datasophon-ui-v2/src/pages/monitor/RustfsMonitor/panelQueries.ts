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

export type { DorisPanelDescriptor as RustfsPanelDescriptor };

/**
 * RustFS 看板面板描述符（OTel Collector → Doris 存储路径，RustFS 走 OTLP 推送而非被抓取）。
 *
 * 指标均为 rustfs_* 原生命名空间（无 minio_* 兼容层，见
 * docs/monitoring/rustfs-otel-verification.md 的 Phase 2 验证结论）：
 *   - gauge 类（容量/磁盘/进程资源）→ otel_metrics_gauge
 *   - sum/counter 类（S3 操作/HTTP 请求/失败/错误）→ otel_metrics_sum
 *   - histogram 类（HTTP 请求耗时分位数）→ otel_metrics_histogram
 *
 * 面板设计详见 docs/monitoring/design/rustfs-dashboard-prototype-spec.md。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // ── R1 概览 Stat（instant） ──────────────────────────────────────────────
  R01: { type: 'instant', metric: 'rustfs_process_uptime_seconds', agg: 'max' },
  R02: { type: 'instant', metric: 'rustfs_cluster_buckets_total', agg: 'max' },
  R03: { type: 'instant', metric: 'rustfs_cluster_objects_total', agg: 'max' },
  R04: {
    type: 'instant',
    metric: 'rustfs_cluster_health_drives_online_count',
    agg: 'sum',
  },
  R05: {
    type: 'instant',
    metric: 'rustfs_cluster_health_drives_offline_count',
    agg: 'sum',
  },

  // ── R2 S3/HTTP 流量（counter rate 1m，sum 表） ──────────────────────────
  R06: {
    type: 'multi-range',
    queries: [
      {
        label: 'op',
        metric: 'rustfs_s3_operations_total',
        rate: '1m',
        table: 'sum',
        groupBy: ['op'],
      },
    ],
  },
  R07: {
    type: 'multi-range',
    queries: [
      {
        label: 'status_class',
        metric: 'rustfs_http_server_requests_total',
        rate: '1m',
        table: 'sum',
        groupBy: ['status_class'],
      },
    ],
  },

  // ── R3 吞吐 & 延迟 ───────────────────────────────────────────────────────
  R08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Request',
        metric: 'rustfs_http_server_request_body_bytes_total',
        rate: '1m',
        table: 'sum',
      },
      {
        label: 'Response',
        metric: 'rustfs_http_server_response_body_bytes_total',
        rate: '1m',
        table: 'sum',
      },
    ],
  },
  R09: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: 'rustfs_http_server_request_duration_seconds',
        table: 'histogram',
        quantile: 0.5,
      },
      {
        label: 'p99',
        metric: 'rustfs_http_server_request_duration_seconds',
        table: 'histogram',
        quantile: 0.99,
      },
    ],
  },

  // ── R4 错误（counter rate 1m，sum 表） ──────────────────────────────────
  R10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Failures',
        metric: 'rustfs_http_server_failures_total',
        rate: '1m',
        table: 'sum',
      },
    ],
  },
  R11: {
    type: 'multi-range',
    queries: [
      {
        label: 'I/O',
        metric: 'rustfs_system_drive_io_errors_total',
        rate: '1m',
        table: 'sum',
      },
      {
        label: 'Timeout',
        metric: 'rustfs_system_drive_timeout_errors_total',
        rate: '1m',
        table: 'sum',
      },
      {
        label: 'Availability',
        metric: 'rustfs_system_drive_availability_errors_total',
        rate: '1m',
        table: 'sum',
      },
    ],
  },

  // ── R5 进程/容量 Saturation ─────────────────────────────────────────────
  R12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Used %',
        metric: 'rustfs_cluster_capacity_used_bytes',
        denominatorMetric: 'rustfs_cluster_capacity_usable_total_bytes',
        scale: 100,
      },
    ],
  },
  R13: {
    type: 'multi-range',
    queries: [{ label: 'CPU %', metric: 'rustfs_process_cpu_percent' }],
  },
  R14: {
    type: 'multi-range',
    queries: [{ label: 'Memory', metric: 'rustfs_process_memory_bytes' }],
  },

  // ── R6 磁盘 Saturation（按 drive 分组） ──────────────────────────────────
  R15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Used',
        metric: 'rustfs_system_drive_used_bytes',
        groupBy: ['drive'],
      },
      {
        label: 'Total',
        metric: 'rustfs_system_drive_total_bytes',
        groupBy: ['drive'],
      },
    ],
  },
  R16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Reads/s',
        metric: 'rustfs_system_drive_reads_per_sec',
        groupBy: ['drive'],
      },
      {
        label: 'Writes/s',
        metric: 'rustfs_system_drive_writes_per_sec',
        groupBy: ['drive'],
      },
    ],
  },
  R17: {
    type: 'multi-range',
    queries: [
      { label: 'Open', metric: 'rustfs_system_process_file_descriptor_open_total' },
      {
        label: 'Limit',
        metric: 'rustfs_system_process_file_descriptor_limit_total',
      },
    ],
  },

  // ── R7 副本（RustFS 独有，MinIO 基础看板没有的维度） ────────────────────
  R18: {
    type: 'multi-range',
    queries: [
      {
        label: 'Active Workers',
        metric: 'rustfs_replication_current_active_workers',
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
