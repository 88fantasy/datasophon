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

export type DorisDashboardSegment = 'cluster' | 'fe' | 'be';

/**
 * Doris 看板面板描述符（OTel Collector → Doris 存储路径）。
 *
 * 面板分组：
 *   cluster (A): DO-A01–DO-A09（集群总览：节点计数、容量、QPS、堆占比、CPU 空闲）
 *   fe (B):      DO-B01–DO-B12（FE 节点：请求率、查询延迟、错误率、连接数、JVM）
 *   be (C):      DO-C01–DO-C11（BE 节点：CPU、内存、磁盘、IO、Compaction、网络）
 *
 * 指标来源：
 *   - gauge 类（jvm_heap_size_bytes / doris_be_disks_* 等）→ otel_metrics_gauge
 *   - sum/counter 类（doris_fe_query_total / doris_be_cpu 等）→ otel_metrics_sum
 *   - summary 类（doris_fe_query_latency_ms / doris_fe_editlog_write_latency_ms）→ otel_metrics_summary
 *
 * 比值面板（denominatorMetric）：
 *   - DO-A08: FE 堆占比 = jvm_heap_size_bytes{type=used} / jvm_heap_size_bytes{type=max}
 *   - DO-B06: FE 错误率 = rate(query_err) / rate(query_total)
 *   - DO-C03: BE 磁盘占比 = local_used / total_capacity（per path）
 *   DO-A09/DO-C01 CPU 空闲因无法在当前后端做跨 mode SUM，展示原始 idle CPU rate。
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // ── cluster 总览（DO-A） ─────────────────────────────────────────────────────

  /** FE 总节点数（查角色注册表 RUNNING 实例数） */
  'DO-A01': { type: 'node-count', roleName: 'DorisFE' },
  /** FE 存活节点数（与 A01 同，RUNNING = alive） */
  'DO-A02': { type: 'node-count', roleName: 'DorisFE' },
  /** BE 总节点数 */
  'DO-A03': { type: 'node-count', roleName: 'DorisBE' },
  /** BE 存活节点数 */
  'DO-A04': { type: 'node-count', roleName: 'DorisBE' },

  /** BE 磁盘已用字节（Doris 本地存储，SUM across all disks） */
  'DO-A05': {
    type: 'instant',
    metric: 'doris_be_disks_local_used_capacity',
    agg: 'sum',
    filters: { group: 'be' },
  },
  /** BE 磁盘总容量（SUM across all disks） */
  'DO-A06': {
    type: 'instant',
    metric: 'doris_be_disks_total_capacity',
    agg: 'sum',
    filters: { group: 'be' },
  },

  /** FE 查询 QPS（rate 2m，counter → sum 表） */
  'DO-A07': {
    type: 'multi-range',
    queries: [
      {
        label: 'query/s',
        metric: 'doris_fe_query_total',
        rate: '2m',
        table: 'sum',
        filters: { group: 'fe' },
      },
    ],
  },

  /**
   * FE JVM 堆占比（%）= jvm_heap_size_bytes{type=used} / jvm_heap_size_bytes{type=max} * 100
   * filter key 不出现在 labels，故分子分母 labels 均为 {instance, job}，可直接匹配。
   */
  'DO-A08': {
    type: 'multi-range',
    queries: [
      {
        label: 'heap%',
        metric: 'jvm_heap_size_bytes',
        filters: { group: 'fe', type: 'used' },
        denominatorMetric: 'jvm_heap_size_bytes',
        denominatorFilters: { group: 'fe', type: 'max' },
        scale: 100,
      },
    ],
  },

  /**
   * BE CPU 空闲时间率（counter，sum 表，rate 2m）。
   * 注：因无法在后端对所有 mode 做 SUM，此处仅展示 idle mode 的原始速率，
   * 不归一化为 0-100%，用于观察趋势。单位：ms/s（每秒空闲 CPU 毫秒数）。
   */
  'DO-A09': {
    type: 'multi-range',
    queries: [
      {
        label: 'cpu_idle',
        metric: 'doris_be_cpu',
        rate: '2m',
        table: 'sum',
        filters: { mode: 'idle', group: 'be' },
      },
    ],
  },

  // ── FE 节点（DO-B） ──────────────────────────────────────────────────────────

  /** FE HTTP 请求率（req/s，counter） */
  'DO-B01': {
    type: 'multi-range',
    queries: [
      {
        label: 'req/s',
        metric: 'doris_fe_request_total',
        rate: '2m',
        table: 'sum',
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 查询率（query/s，counter） */
  'DO-B02': {
    type: 'multi-range',
    queries: [
      {
        label: 'query/s',
        metric: 'doris_fe_query_total',
        rate: '2m',
        table: 'sum',
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 查询 P99 延迟（summary 表，毫秒） */
  'DO-B03': {
    type: 'multi-range',
    queries: [
      {
        label: 'p99',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.99,
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 查询延迟分位数（p50/p75/p99） */
  'DO-B04': {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.5,
        filters: { group: 'fe' },
      },
      {
        label: 'p75',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.75,
        filters: { group: 'fe' },
      },
      {
        label: 'p99',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.99,
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 查询错误（累积值 + 1m 速率） */
  'DO-B05': {
    type: 'multi-range',
    queries: [
      {
        label: 'cumulative',
        metric: 'doris_fe_query_err',
        table: 'sum',
        filters: { group: 'fe' },
      },
      {
        label: 'rate_1m',
        metric: 'doris_fe_query_err',
        rate: '1m',
        table: 'sum',
        filters: { group: 'fe' },
      },
    ],
  },

  /**
   * FE 查询错误率（%）= rate(query_err) / rate(query_total) * 100
   */
  'DO-B06': {
    type: 'multi-range',
    queries: [
      {
        label: 'error%',
        metric: 'doris_fe_query_err',
        rate: '2m',
        table: 'sum',
        filters: { group: 'fe' },
        denominatorMetric: 'doris_fe_query_total',
        denominatorFilters: { group: 'fe' },
        scale: 100,
      },
    ],
  },

  /** FE 当前连接数（gauge） */
  'DO-B07': {
    type: 'multi-range',
    queries: [
      {
        label: 'connections',
        metric: 'doris_fe_connection_total',
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 最大 Tablet Compaction Score（gauge） */
  'DO-B08': {
    type: 'multi-range',
    queries: [
      {
        label: 'score',
        metric: 'doris_fe_max_tablet_compaction_score',
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE 调度中 Tablet 数（gauge） */
  'DO-B09': {
    type: 'multi-range',
    queries: [
      {
        label: 'tablets',
        metric: 'doris_fe_scheduled_tablet_num',
        filters: { group: 'fe' },
      },
    ],
  },

  /** FE JVM 堆内存（used + max，bytes，gauge） */
  'DO-B10': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        metric: 'jvm_heap_size_bytes',
        filters: { group: 'fe', type: 'used' },
      },
      {
        label: 'max',
        metric: 'jvm_heap_size_bytes',
        filters: { group: 'fe', type: 'max' },
      },
    ],
  },

  /** FE JVM Old GC（gc count + gc time） */
  'DO-B11': {
    type: 'multi-range',
    queries: [
      {
        label: 'gc_count',
        metric: 'jvm_old_gc',
        filters: { group: 'fe', type: 'count' },
      },
      {
        label: 'avg_time_ms',
        metric: 'jvm_old_gc',
        filters: { group: 'fe', type: 'time' },
      },
    ],
  },

  /** FE EditLog 写入 P99 延迟（summary 表） */
  'DO-B12': {
    type: 'multi-range',
    queries: [
      {
        label: 'p99',
        metric: 'doris_fe_editlog_write_latency_ms',
        table: 'summary',
        quantile: 0.99,
        filters: { group: 'fe' },
      },
    ],
  },

  // ── BE 节点（DO-C） ──────────────────────────────────────────────────────────

  /**
   * BE CPU 空闲时间率（counter，sum 表，rate 2m）。
   * 与 DO-A09 同样的简化：展示原始 idle mode CPU rate。
   */
  'DO-C01': {
    type: 'multi-range',
    queries: [
      {
        label: 'cpu_idle',
        metric: 'doris_be_cpu',
        rate: '2m',
        table: 'sum',
        filters: { mode: 'idle', group: 'be' },
      },
    ],
  },

  /** BE 内存已分配字节（gauge） */
  'DO-C02': {
    type: 'multi-range',
    queries: [
      {
        label: 'memory',
        metric: 'doris_be_memory_allocated_bytes',
        filters: { group: 'be' },
      },
    ],
  },

  /**
   * BE 磁盘占比（per path）：
   *   local_used_pct = doris_be_disks_local_used_capacity / doris_be_disks_total_capacity
   *   avail_pct      = doris_be_disks_avail_capacity      / doris_be_disks_total_capacity
   * groupBy=['path'] 使每条磁盘路径独立显示。
   */
  'DO-C03': {
    type: 'multi-range',
    queries: [
      {
        label: 'local_used_pct',
        metric: 'doris_be_disks_local_used_capacity',
        filters: { group: 'be' },
        denominatorMetric: 'doris_be_disks_total_capacity',
        denominatorFilters: { group: 'be' },
        groupBy: ['path'],
        scale: 1,
      },
      {
        label: 'avail_pct',
        metric: 'doris_be_disks_avail_capacity',
        filters: { group: 'be' },
        denominatorMetric: 'doris_be_disks_total_capacity',
        denominatorFilters: { group: 'be' },
        groupBy: ['path'],
        scale: 1,
      },
    ],
  },

  /**
   * BE 磁盘 IO 繁忙度（io_time_ms rate / 10，counter → sum 表，scale=0.1）。
   * 原始值 rate(disk_io_time_ms) / 10 ≈ IO 繁忙度%（100ms/s = 10%）。
   */
  'DO-C04': {
    type: 'multi-range',
    queries: [
      {
        label: 'io_pct',
        metric: 'doris_be_disk_io_time_ms',
        rate: '2m',
        table: 'sum',
        scale: 0.1,
        filters: { group: 'be' },
      },
    ],
  },

  /** BE Compaction 吞吐（base + cumulative，bytes/s） */
  'DO-C05': {
    type: 'multi-range',
    queries: [
      {
        label: 'base',
        metric: 'doris_be_compaction_bytes_total',
        rate: '2m',
        table: 'sum',
        filters: { type: 'base', group: 'be' },
      },
      {
        label: 'cumulative',
        metric: 'doris_be_compaction_bytes_total',
        rate: '2m',
        table: 'sum',
        filters: { type: 'cumulative', group: 'be' },
      },
    ],
  },

  /** BE 扫描读取字节率（bytes/s，counter） */
  'DO-C06': {
    type: 'multi-range',
    queries: [
      {
        label: 'scan_bytes/s',
        metric: 'doris_be_query_scan_bytes',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
      },
    ],
  },

  /** BE 扫描读取行率（rows/s，counter） */
  'DO-C07': {
    type: 'multi-range',
    queries: [
      {
        label: 'scan_rows/s',
        metric: 'doris_be_query_scan_rows',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
      },
    ],
  },

  /** BE Push 写入字节率（bytes/s，counter） */
  'DO-C08': {
    type: 'multi-range',
    queries: [
      {
        label: 'write_bytes/s',
        metric: 'doris_be_push_request_write_bytes',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
      },
    ],
  },

  /** BE Push 写入行率（rows/s，counter） */
  'DO-C09': {
    type: 'multi-range',
    queries: [
      {
        label: 'write_rows/s',
        metric: 'doris_be_push_request_write_rows',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
      },
    ],
  },

  /**
   * BE Push 请求耗时（us → ms，counter，rate 2m，scale=0.001）。
   * 原始单位 μs/s，乘 0.001 换算为 ms/s。
   */
  'DO-C10': {
    type: 'multi-range',
    queries: [
      {
        label: 'push_ms/s',
        metric: 'doris_be_push_request_duration_us',
        rate: '2m',
        table: 'sum',
        scale: 0.001,
        filters: { group: 'be' },
      },
    ],
  },

  /**
   * BE 网络收发字节率（send + recv，bytes/s）。
   * filtersNe={device:'lo'} 排除 loopback 接口。
   */
  'DO-C11': {
    type: 'multi-range',
    queries: [
      {
        label: 'send',
        metric: 'doris_be_network_send_bytes',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
        filtersNe: { device: 'lo' },
      },
      {
        label: 'recv',
        metric: 'doris_be_network_receive_bytes',
        rate: '2m',
        table: 'sum',
        filters: { group: 'be' },
        filtersNe: { device: 'lo' },
      },
    ],
  },
};

export const DORIS_SEGMENT_PANEL_IDS: Record<DorisDashboardSegment, string[]> =
  {
    cluster: [
      'DO-A01',
      'DO-A02',
      'DO-A03',
      'DO-A04',
      'DO-A05',
      'DO-A06',
      'DO-A07',
      'DO-A08',
      'DO-A09',
    ],
    fe: [
      'DO-B01',
      'DO-B02',
      'DO-B03',
      'DO-B04',
      'DO-B05',
      'DO-B06',
      'DO-B07',
      'DO-B08',
      'DO-B09',
      'DO-B10',
      'DO-B11',
      'DO-B12',
    ],
    be: [
      'DO-C01',
      'DO-C02',
      'DO-C03',
      'DO-C04',
      'DO-C05',
      'DO-C06',
      'DO-C07',
      'DO-C08',
      'DO-C09',
      'DO-C10',
      'DO-C11',
    ],
  };

export function getDorisSegmentPanelIds(
  segment: DorisDashboardSegment,
): string[] {
  return DORIS_SEGMENT_PANEL_IDS[segment];
}
