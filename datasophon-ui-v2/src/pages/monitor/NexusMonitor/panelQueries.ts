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

export type { DorisPanelDescriptor as NexusPanelDescriptor };

/**
 * Nexus 看板面板描述符（T1 + T2 + T3 全部已实现）。
 *
 * T1 instant gauge:     N01–N06
 * T1 multi-range gauge: N12, N13, N15, N16, N17, N18
 * T2 counter rate:      N07, N08, N14
 * T3 summary quantile:  N09, N10, N11
 */
export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // ── T1 instant ─────────────────────────────────────────────────────────────
  N01: { type: 'instant', metric: 'jvm_vm_uptime' },
  N02: { type: 'instant', metric: 'jvm_memory_heap_usage', scale: 100 },
  N03: { type: 'instant', metric: 'jvm_fd_usage', scale: 100 },
  N04: { type: 'instant', metric: 'readonly_enabled' },
  N05: { type: 'instant', metric: 'jvm_thread_states_count' },
  N06: { type: 'instant', metric: 'jvm_thread_states_deadlock_count' },

  // ── T2 counter rate 1m（table=sum：_total 指标存 otel_metrics_sum） ──────────
  N07: {
    type: 'multi-range',
    queries: ['1xx', '2xx', '3xx', '4xx', '5xx'].map((code) => ({
      label: code,
      metric: `org_eclipse_jetty_ee8_nested_ContextHandler_CoreContextHandler_${code}_responses_total`,
      rate: '1m' as const,
      table: 'sum' as const,
    })),
  },

  // ── T2 component exceptions rate（sum 表，任意 read 异常为代理指标） ────────
  N08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Exceptions',
        metric: 'com_sonatype_nexus_api_extdirect_selfhosted_clm_ClmComponent_read_exceptions_total',
        rate: '1m' as const,
        table: 'sum' as const,
      },
    ],
  },

  // ── T3 summary quantile（Dropwizard timer，单位 s → ms scale=1000） ────────
  N09: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: 'org_eclipse_jetty_ee8_nested_ContextHandler_CoreContextHandler_dispatches',
        table: 'summary' as const,
        quantile: 0.5,
        scale: 1000,
      },
      {
        label: 'p99',
        metric: 'org_eclipse_jetty_ee8_nested_ContextHandler_CoreContextHandler_dispatches',
        table: 'summary' as const,
        quantile: 0.99,
        scale: 1000,
      },
    ],
  },
  N10: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: 'org_sonatype_nexus_blobstore_file_FileBlobStore_get_timer',
        table: 'summary' as const,
        quantile: 0.5,
        scale: 1000,
      },
      {
        label: 'p99',
        metric: 'org_sonatype_nexus_blobstore_file_FileBlobStore_get_timer',
        table: 'summary' as const,
        quantile: 0.99,
        scale: 1000,
      },
    ],
  },
  N11: {
    type: 'multi-range',
    queries: [
      {
        label: 'Create p99',
        metric: 'org_sonatype_nexus_blobstore_file_FileBlobStore_create_timer',
        table: 'summary' as const,
        quantile: 0.99,
        scale: 1000,
      },
      {
        label: 'Get p99',
        metric: 'org_sonatype_nexus_blobstore_file_FileBlobStore_get_timer',
        table: 'summary' as const,
        quantile: 0.99,
        scale: 1000,
      },
    ],
  },

  // ── T1 multi-range gauge ───────────────────────────────────────────────────
  N12: {
    type: 'multi-range',
    queries: [
      { label: 'Max', metric: 'jvm_memory_heap_max' },
      { label: 'Used', metric: 'jvm_memory_heap_used' },
      { label: 'Committed', metric: 'jvm_memory_heap_committed' },
    ],
  },
  // G1GC pool names（JDK 17 默认 G1GC，不再有 PS_Eden_Space / PS_Old_Gen）
  N13: {
    type: 'multi-range',
    queries: [
      { label: 'G1 Eden', metric: 'jvm_memory_pools_G1_Eden_Space_used' },
      { label: 'G1 Old', metric: 'jvm_memory_pools_G1_Old_Gen_used' },
      { label: 'G1 Survivor', metric: 'jvm_memory_pools_G1_Survivor_Space_used' },
      { label: 'Metaspace', metric: 'jvm_memory_pools_Metaspace_used' },
    ],
  },

  // ── T1 GC pause time rate（G1GC，ms/s 衡量 GC 开销） ─────────────────────
  N15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Young GC',
        metric: 'jvm_garbage_collectors_G1_Young_Generation_time',
        rate: '1m' as const,
      },
      {
        label: 'Old GC',
        metric: 'jvm_garbage_collectors_G1_Old_Generation_time',
        rate: '1m' as const,
      },
    ],
  },

  // ── T2 GC counter rate 1m（G1GC） ─────────────────────────────────────────
  N14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Old GC',
        metric: 'jvm_garbage_collectors_G1_Old_Generation_count',
        rate: '1m' as const,
      },
      {
        label: 'Young GC',
        metric: 'jvm_garbage_collectors_G1_Young_Generation_count',
        rate: '1m' as const,
      },
    ],
  },

  // ── T1 multi-range gauge ───────────────────────────────────────────────────
  N16: {
    type: 'multi-range',
    queries: [
      { label: 'Runnable', metric: 'jvm_thread_states_runnable_count' },
      { label: 'Blocked', metric: 'jvm_thread_states_blocked_count' },
      { label: 'Waiting', metric: 'jvm_thread_states_waiting_count' },
      { label: 'Timed Waiting', metric: 'jvm_thread_states_timed_waiting_count' },
    ],
  },
  N18: {
    type: 'multi-range',
    queries: [
      { label: 'Non-Heap', metric: 'jvm_memory_non_heap_used' },
      { label: 'Direct Buffers', metric: 'jvm_buffers_direct_used' },
      { label: 'Mapped Buffers', metric: 'jvm_buffers_mapped_used' },
    ],
  },

  // ── T1 Jetty ThreadPool（gauge，含 QTP hash，实例间可能不同） ───────────────
  N17: {
    type: 'multi-range',
    queries: [
      { label: 'Queued Jobs', metric: 'org_eclipse_jetty_util_thread_QueuedThreadPool_qtp965453174_jobs' },
      { label: 'Pool Size', metric: 'org_eclipse_jetty_util_thread_QueuedThreadPool_qtp965453174_size' },
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
