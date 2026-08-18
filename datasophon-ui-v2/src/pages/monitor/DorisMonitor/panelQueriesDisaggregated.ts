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

/**
 * 存算分离 Doris（DorisDisaggregatedCluster CR）看板面板描述符。
 *
 * 与 panelQueries.ts（存算一体）的关键差异（均为真实沙箱实测确认，见
 * docs/doris-存算分离监控与CR接管-实施任务清单-2026-08-18.md Phase 0）：
 *   - K8s 侧不存在 `group` 标签，FE/BE 靠各自的 job（service_name）区分，
 *     由 useDorisMonitorDashboard 按角色（fe/compute）拆分成两次取数调用，
 *     job 过滤在调用层传入，本文件的描述符不出现 group/job 过滤键。
 *   - `doris_fe_query_total`/`doris_fe_query_err` 存在"无 user/cluster_name
 *     汇总"与"按 user/cluster_name 拆分"的重复序列，用 filters={user:'',
 *     cluster_name:''}（对应后端 IS NULL 语义）去重，避免 QPS/错误率被放大。
 *   - FE 侧 GC 指标是 jvm_gc（name/type 维度），不是 jvm_old_gc。
 *   - 本地磁盘类指标（doris_be_disks_*）不存在，节点数/容量类面板（原
 *     DO-A01–A06）在存算分离模式下由 useDorisMonitorDashboard 直接用
 *     fetchDorisLabels 的 instances 计数得出，不经过本文件的描述符管线。
 *   - 计算组（compute）新增/替代指标：远程 IO 占比、S3 读写吞吐、缓存命中率、
 *     workload group、溢写盘，见 DO-D01–DO-D12。
 */
export const DISAGG_PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // ── cluster 概览（沿用 DO-A07–A09 编号，与耦合模式面板标题保持一致） ──────────

  /** FE 查询 QPS（rate 2m，去重 user/cluster_name 维度） */
  'DO-A07': {
    type: 'multi-range',
    queries: [
      {
        label: 'query/s',
        metric: 'doris_fe_query_total',
        rate: '2m',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
      },
    ],
  },

  /** FE JVM 堆占比（%） */
  'DO-A08': {
    type: 'multi-range',
    queries: [
      {
        label: 'heap%',
        metric: 'jvm_heap_size_bytes',
        filters: { type: 'used' },
        denominatorMetric: 'jvm_heap_size_bytes',
        denominatorFilters: { type: 'max' },
        scale: 100,
      },
    ],
  },

  /** 计算组 CPU 空闲率（原始 idle mode rate，与耦合模式同样不做跨 mode 归一化） */
  'DO-A09': {
    type: 'multi-range',
    queries: [
      {
        label: 'cpu_idle',
        metric: 'doris_be_cpu',
        rate: '2m',
        table: 'sum',
        filters: { mode: 'idle' },
      },
    ],
  },

  // ── FE 节点（DO-B，沿用耦合模式编号，仅去掉 group 过滤 + 去重维度） ──────────

  'DO-B01': {
    type: 'multi-range',
    queries: [
      {
        label: 'req/s',
        metric: 'doris_fe_request_total',
        rate: '2m',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
      },
    ],
  },

  'DO-B02': {
    type: 'multi-range',
    queries: [
      {
        label: 'query/s',
        metric: 'doris_fe_query_total',
        rate: '2m',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
      },
    ],
  },

  'DO-B03': {
    type: 'multi-range',
    queries: [
      {
        label: 'p99',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.99,
      },
    ],
  },

  'DO-B04': {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.5,
      },
      {
        label: 'p75',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.75,
      },
      {
        label: 'p99',
        metric: 'doris_fe_query_latency_ms',
        table: 'summary',
        quantile: 0.99,
      },
    ],
  },

  'DO-B05': {
    type: 'multi-range',
    queries: [
      {
        label: 'cumulative',
        metric: 'doris_fe_query_err',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
      },
      {
        label: 'rate_1m',
        metric: 'doris_fe_query_err',
        rate: '1m',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
      },
    ],
  },

  'DO-B06': {
    type: 'multi-range',
    queries: [
      {
        label: 'error%',
        metric: 'doris_fe_query_err',
        rate: '2m',
        table: 'sum',
        filters: { user: '', cluster_name: '' },
        denominatorMetric: 'doris_fe_query_total',
        denominatorFilters: { user: '', cluster_name: '' },
        scale: 100,
      },
    ],
  },

  'DO-B07': {
    type: 'multi-range',
    queries: [{ label: 'connections', metric: 'doris_fe_connection_total' }],
  },

  'DO-B08': {
    type: 'multi-range',
    queries: [
      { label: 'score', metric: 'doris_fe_max_tablet_compaction_score' },
    ],
  },

  'DO-B09': {
    type: 'multi-range',
    queries: [{ label: 'tablets', metric: 'doris_fe_scheduled_tablet_num' }],
  },

  'DO-B10': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        metric: 'jvm_heap_size_bytes',
        filters: { type: 'used' },
      },
      {
        label: 'max',
        metric: 'jvm_heap_size_bytes',
        filters: { type: 'max' },
      },
    ],
  },

  /** FE JVM Old GC：K8s 侧统一走 jvm_gc（name 区分 Old/Young，type 区分 count/time） */
  'DO-B11': {
    type: 'multi-range',
    queries: [
      {
        label: 'gc_count',
        metric: 'jvm_gc',
        filters: { name: 'G1 Old Generation Count', type: 'count' },
      },
      {
        label: 'avg_time_ms',
        metric: 'jvm_gc',
        filters: { name: 'G1 Old Generation Time', type: 'time' },
      },
    ],
  },

  'DO-B12': {
    type: 'multi-range',
    queries: [
      {
        label: 'p99',
        metric: 'doris_fe_editlog_write_latency_ms',
        table: 'summary',
        quantile: 0.99,
      },
    ],
  },

  // ── 计算组（DO-D，存算分离专属） ─────────────────────────────────────────

  /** 计算组 CPU 空闲率 */
  'DO-D01': {
    type: 'multi-range',
    queries: [
      {
        label: 'cpu_idle',
        metric: 'doris_be_cpu',
        rate: '2m',
        table: 'sum',
        filters: { mode: 'idle' },
      },
    ],
  },

  /** 计算组内存已分配字节 */
  'DO-D02': {
    type: 'multi-range',
    queries: [{ label: 'memory', metric: 'doris_be_memory_allocated_bytes' }],
  },

  /** 溢写盘占用率（%）= spill_disk_data_size / spill_disk_capacity。本地磁盘指标在存算分离下不存在，用溢写盘替代 */
  'DO-D03': {
    type: 'multi-range',
    queries: [
      {
        label: 'used_pct',
        metric: 'doris_be_spill_disk_data_size',
        denominatorMetric: 'doris_be_spill_disk_capacity',
        scale: 100,
      },
    ],
  },

  /** 磁盘 IO 繁忙度（%，Doris 自身已算好 0–100 的 gauge，无需 rate 换算） */
  'DO-D04': {
    type: 'multi-range',
    queries: [
      { label: 'io_pct', metric: 'doris_be_max_disk_io_util_percent' },
    ],
  },

  /** Compaction 吞吐（base + cumulative，bytes/s） */
  'DO-D05': {
    type: 'multi-range',
    queries: [
      {
        label: 'base',
        metric: 'doris_be_compaction_bytes_total',
        rate: '2m',
        table: 'sum',
        filters: { type: 'base' },
      },
      {
        label: 'cumulative',
        metric: 'doris_be_compaction_bytes_total',
        rate: '2m',
        table: 'sum',
        filters: { type: 'cumulative' },
      },
    ],
  },

  /** 扫描读取字节率（bytes/s） */
  'DO-D06': {
    type: 'multi-range',
    queries: [
      {
        label: 'scan_bytes/s',
        metric: 'doris_be_query_scan_bytes',
        rate: '2m',
        table: 'sum',
      },
    ],
  },

  /** 扫描读取行率（rows/s） */
  'DO-D07': {
    type: 'multi-range',
    queries: [
      {
        label: 'scan_rows/s',
        metric: 'doris_be_query_scan_rows',
        rate: '2m',
        table: 'sum',
      },
    ],
  },

  /** Workload Group CPU 耗时（秒/s，按 workload_group 分组展示） */
  'DO-D08': {
    type: 'multi-range',
    queries: [
      {
        label: 'cpu_sec/s',
        metric: 'doris_be_workload_group_cpu_time_sec',
        rate: '2m',
        table: 'sum',
        groupBy: ['workload_group'],
      },
    ],
  },

  /** 网络收发字节率（send + recv，排除 loopback） */
  'DO-D09': {
    type: 'multi-range',
    queries: [
      {
        label: 'send',
        metric: 'doris_be_network_send_bytes',
        rate: '2m',
        table: 'sum',
        filtersNe: { device: 'lo' },
      },
      {
        label: 'recv',
        metric: 'doris_be_network_receive_bytes',
        rate: '2m',
        table: 'sum',
        filtersNe: { device: 'lo' },
      },
    ],
  },

  /** 远程 IO 占比（%）= 读远程字节 / 读总字节，存算分离核心信号（缓存未命中即打 S3/HDFS） */
  'DO-D10': {
    type: 'multi-range',
    queries: [
      {
        label: 'remote_pct',
        metric: 'doris_be_num_io_bytes_read_from_remote',
        rate: '2m',
        table: 'sum',
        denominatorMetric: 'doris_be_num_io_bytes_read_total',
        denominatorTable: 'sum',
        scale: 100,
      },
    ],
  },

  /** S3 读写吞吐（bytes/s） */
  'DO-D11': {
    type: 'multi-range',
    queries: [
      {
        label: 'read',
        metric: 'doris_be_s3_bytes_read_total',
        rate: '2m',
        table: 'sum',
      },
      {
        label: 'write',
        metric: 'doris_be_s3_bytes_written_total',
        rate: '2m',
        table: 'sum',
      },
    ],
  },

  /** 数据页缓存命中率（%），name=DataPageCache 是读路径主缓存 */
  'DO-D12': {
    type: 'multi-range',
    queries: [
      {
        label: 'hit_ratio',
        metric: 'doris_be_cache_hit_ratio',
        filters: { name: 'DataPageCache' },
        scale: 100,
      },
    ],
  },
};

export const DISAGG_SEGMENT_PANEL_IDS: Record<
  'cluster' | 'fe' | 'compute',
  string[]
> = {
  cluster: ['DO-A07', 'DO-A08', 'DO-A09'],
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
  compute: [
    'DO-D01',
    'DO-D02',
    'DO-D03',
    'DO-D04',
    'DO-D05',
    'DO-D06',
    'DO-D07',
    'DO-D08',
    'DO-D09',
    'DO-D10',
    'DO-D11',
    'DO-D12',
  ],
};

/**
 * 每个面板归属的角色（fe/compute），决定 useDorisMonitorDashboard 用哪个角色的
 * job 去查询——cluster 段面板混合了 FE（A07/A08）与计算组（A09）指标，不能整段
 * 共用一个 job 过滤。
 */
export const DISAGG_PANEL_ROLE: Record<string, 'fe' | 'compute'> = {
  'DO-A07': 'fe',
  'DO-A08': 'fe',
  'DO-A09': 'compute',
  ...Object.fromEntries(
    DISAGG_SEGMENT_PANEL_IDS.fe.map((id) => [id, 'fe' as const]),
  ),
  ...Object.fromEntries(
    DISAGG_SEGMENT_PANEL_IDS.compute.map((id) => [id, 'compute' as const]),
  ),
};

export function getDisaggSegmentPanelIds(
  segment: 'cluster' | 'fe' | 'compute',
): string[] {
  return DISAGG_SEGMENT_PANEL_IDS[segment];
}
