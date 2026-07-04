import type { DorisPanelDescriptor } from '../_shared/dorisService';

export type { DorisPanelDescriptor as JuiceFSPanelDescriptor };

const FUSE_OPS_HISTOGRAM = 'juicefs_fuse_ops_durations_histogram_seconds';
const TRANSACTION_HISTOGRAM = 'juicefs_transaction_durations_histogram_seconds';
const OBJECT_REQUEST_HISTOGRAM =
  'juicefs_object_request_durations_histogram_seconds';

export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  J01: { type: 'instant', metric: 'juicefs_uptime', agg: 'max' },
  J02: { type: 'instant', metric: 'juicefs_used_space', agg: 'max' },
  J03: { type: 'instant', metric: 'juicefs_used_inodes', agg: 'max' },
  J04: { type: 'instant', metric: 'juicefs_uptime', agg: 'count' },
  J05: {
    type: 'multi-range',
    queries: [
      {
        label: 'Hits',
        metric: 'juicefs_blockcache_hits',
        rate: '1m',
        table: 'gauge',
      },
      {
        label: 'Miss',
        metric: 'juicefs_blockcache_miss',
        rate: '1m',
        table: 'gauge',
      },
    ],
  },
  J06: { type: 'instant', metric: 'juicefs_staging_blocks', agg: 'sum' },
  J07: {
    type: 'multi-range',
    queries: [
      {
        label: 'Operations',
        metric: FUSE_OPS_HISTOGRAM,
        rate: '1m',
        table: 'histogram',
        field: 'count',
      },
    ],
  },
  J08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Write',
        metric: 'juicefs_fuse_written_size_bytes',
        rate: '1m',
        table: 'histogram',
        field: 'sum',
      },
      {
        label: 'Read',
        metric: 'juicefs_fuse_read_size_bytes',
        rate: '1m',
        table: 'histogram',
        field: 'sum',
      },
    ],
  },
  J09: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: FUSE_OPS_HISTOGRAM,
        table: 'histogram',
        quantile: 0.5,
        scale: 1000000,
        groupBy: ['mp'],
      },
      {
        label: 'p99',
        metric: FUSE_OPS_HISTOGRAM,
        table: 'histogram',
        quantile: 0.99,
        scale: 1000000,
        groupBy: ['mp'],
      },
    ],
  },
  J10: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: TRANSACTION_HISTOGRAM,
        table: 'histogram',
        quantile: 0.5,
        scale: 1000000,
      },
      {
        label: 'p99',
        metric: TRANSACTION_HISTOGRAM,
        table: 'histogram',
        quantile: 0.99,
        scale: 1000000,
      },
    ],
  },
  J11: {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        metric: OBJECT_REQUEST_HISTOGRAM,
        table: 'histogram',
        quantile: 0.5,
        scale: 1000000,
      },
      {
        label: 'p99',
        metric: OBJECT_REQUEST_HISTOGRAM,
        table: 'histogram',
        quantile: 0.99,
        scale: 1000000,
      },
    ],
  },
  J12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Requests',
        metric: OBJECT_REQUEST_HISTOGRAM,
        rate: '1m',
        table: 'histogram',
        field: 'count',
        groupBy: ['method'],
      },
    ],
  },
  J13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Object Request Errors',
        metric: 'juicefs_object_request_errors',
        rate: '1m',
        table: 'gauge',
      },
      {
        label: 'Transaction Restarts',
        metric: 'juicefs_transaction_restart',
        rate: '1m',
        table: 'gauge',
      },
    ],
  },
  J14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Size',
        metric: 'juicefs_blockcache_bytes',
        groupBy: ['mp'],
      },
    ],
  },
  J15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Count Hits',
        metric: 'juicefs_blockcache_hits',
        rate: '1m',
        table: 'gauge',
        groupBy: ['mp'],
      },
      {
        label: 'Count Miss',
        metric: 'juicefs_blockcache_miss',
        rate: '1m',
        table: 'gauge',
        groupBy: ['mp'],
      },
      {
        label: 'Bytes Hits',
        metric: 'juicefs_blockcache_hit_bytes',
        rate: '1m',
        table: 'gauge',
        groupBy: ['mp'],
      },
      {
        label: 'Bytes Miss',
        metric: 'juicefs_blockcache_miss_bytes',
        rate: '1m',
        table: 'gauge',
        groupBy: ['mp'],
      },
    ],
  },
  J16: {
    type: 'multi-range',
    queries: [
      {
        label: 'PUT',
        metric: 'juicefs_object_request_data_bytes',
        rate: '1m',
        table: 'gauge',
        filters: { method: 'PUT' },
        groupBy: ['method'],
      },
      {
        label: 'GET',
        metric: 'juicefs_object_request_data_bytes',
        rate: '1m',
        table: 'gauge',
        filters: { method: 'GET' },
        groupBy: ['method'],
      },
    ],
  },
  J17: {
    type: 'multi-range',
    queries: [
      {
        label: 'CPU %',
        metric: 'juicefs_cpu_usage',
        rate: '1m',
        table: 'gauge',
        scale: 100,
        groupBy: ['mp'],
      },
      {
        label: 'Memory',
        metric: 'juicefs_memory',
        groupBy: ['mp'],
      },
    ],
  },
};
