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

export type SeaTunnelDashboardSegment = 'master' | 'worker';

export const SEATUNNEL_JOB_BY_SEGMENT: Record<
  SeaTunnelDashboardSegment,
  string
> = {
  master: '^SeaTunnelMaster$',
  worker: '^SeaTunnelWorker$',
};

const COMMON_PANEL_IDS = [
  'ST-C01',
  'ST-C02',
  'ST-C03',
  'ST-C04',
  'ST-C05',
  'ST-C06',
  'ST-C07',
  'ST-C08',
  'ST-C09',
  'ST-C10',
];

export const SEGMENT_PANEL_IDS: Record<SeaTunnelDashboardSegment, string[]> = {
  master: [
    'ST-M01',
    'ST-M02',
    'ST-M03',
    'ST-M04',
    'ST-M05',
    ...COMMON_PANEL_IDS,
  ],
  worker: ['ST-W01', 'ST-W02', 'ST-W03', ...COMMON_PANEL_IDS],
};

export const CLUSTER_PANEL_IDS = ['ST-M02', 'ST-M03', 'ST-M04', 'ST-M05'];

export function getSeaTunnelSegmentPanelIds(
  segment: SeaTunnelDashboardSegment,
): string[] {
  return SEGMENT_PANEL_IDS[segment];
}

export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  'ST-M01': { type: 'node-count', roleName: 'SeaTunnelMaster' },
  'ST-M02': {
    type: 'instant',
    metric: 'job_count',
    agg: 'max',
    filters: { type: 'running' },
  },
  'ST-M03': {
    type: 'multi-range',
    queries: [
      {
        label: 'job_count',
        metric: 'job_count',
        groupBy: ['type'],
        filtersRegex: {
          type: '^(running|failed|canceled|finished|pending)$',
        },
      },
    ],
  },
  'ST-M04': {
    type: 'multi-range',
    queries: [
      { label: 'activeCount', metric: 'job_thread_pool_activeCount' },
      { label: 'poolSize', metric: 'job_thread_pool_poolSize' },
      { label: 'queueTaskCount', metric: 'job_thread_pool_queueTaskCount' },
    ],
  },
  'ST-M05': {
    type: 'multi-range',
    queries: [
      {
        label: 'request/s',
        metric: 'request_slot_operation_total',
        table: 'sum',
        rate: '1m',
        groupBy: ['result'],
      },
    ],
  },
  'ST-W01': { type: 'node-count', roleName: 'SeaTunnelWorker' },
  'ST-W02': {
    type: 'multi-range',
    queries: [
      {
        label: 'report/s',
        metric: 'report_metrics_operation_total',
        table: 'sum',
        rate: '1m',
        groupBy: ['result'],
      },
    ],
  },
  'ST-W03': {
    type: 'multi-range',
    queries: [
      {
        label: 'last',
        metric: 'report_metrics_operation_last_invocation_latency_ms',
      },
      {
        label: 'max',
        metric: 'report_metrics_operation_max_invocation_latency_ms',
      },
    ],
  },
  'ST-C01': { type: 'instant', metric: 'node_count', agg: 'max' },
  'ST-C02': {
    type: 'multi-range',
    queries: [{ label: 'safe', metric: 'hazelcast_partition_isClusterSafe' }],
  },
  'ST-C03': {
    type: 'multi-range',
    queries: [
      {
        label: 'queueSize',
        metric: 'hazelcast_executor_queueSize',
        groupBy: ['type'],
      },
    ],
  },
  'ST-C04': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        metric: 'jvm_memory_bytes_used',
        filters: { area: 'heap' },
      },
      {
        label: 'committed',
        metric: 'jvm_memory_bytes_committed',
        filters: { area: 'heap' },
      },
      {
        label: 'max',
        metric: 'jvm_memory_bytes_max',
        filters: { area: 'heap' },
      },
    ],
  },
  'ST-C05': {
    type: 'multi-range',
    queries: [
      {
        label: 'Non-Heap',
        metric: 'jvm_memory_bytes_used',
        filters: { area: 'nonheap' },
      },
      {
        label: 'Direct',
        metric: 'jvm_buffer_pool_used_bytes',
        filters: { pool: 'direct' },
      },
    ],
  },
  'ST-C06': {
    type: 'multi-range',
    queries: [
      {
        label: 'count/s',
        metric: 'jvm_gc_collection_seconds',
        table: 'summary',
        field: 'count',
        rate: '1m',
        groupBy: ['gc'],
      },
    ],
  },
  'ST-C07': {
    type: 'multi-range',
    queries: [
      {
        label: 'time (ms/s)',
        metric: 'jvm_gc_collection_seconds',
        table: 'summary',
        field: 'sum',
        rate: '1m',
        scale: 1000,
        groupBy: ['gc'],
      },
    ],
  },
  'ST-C08': {
    type: 'multi-range',
    queries: [
      { label: 'current', metric: 'jvm_threads_current' },
      {
        label: 'state',
        metric: 'jvm_threads_state',
        groupBy: ['state'],
      },
    ],
  },
  'ST-C09': {
    type: 'multi-range',
    queries: [
      {
        label: 'CPU cores',
        metric: 'process_cpu_seconds_total',
        table: 'sum',
        rate: '1m',
      },
    ],
  },
  'ST-C10': {
    type: 'multi-range',
    queries: [
      { label: 'RSS', metric: 'process_resident_memory_bytes' },
      { label: 'open fds', metric: 'process_open_fds' },
      { label: 'max fds', metric: 'process_max_fds' },
    ],
  },
};
