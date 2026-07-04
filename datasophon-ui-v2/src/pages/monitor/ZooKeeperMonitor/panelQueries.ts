import type { DorisPanelDescriptor } from '../_shared/dorisService';

export type { DorisPanelDescriptor as ZKPanelDescriptor };

export interface ZKDashboardVariables {
  instance: string;
  job: string;
}

export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  Z01: { type: 'instant', metric: 'quorum_size', agg: 'max' },
  Z02: { type: 'instant', metric: 'leader_uptime', agg: 'max' },
  Z03: { type: 'instant', metric: 'jvm_threads_current', agg: 'max' },
  Z04: { type: 'instant', metric: 'jvm_threads_deadlocked', agg: 'max' },
  Z05: { type: 'instant', metric: 'num_alive_connections', agg: 'sum' },
  Z06: {
    type: 'instant',
    metric: 'open_file_descriptor_count',
    agg: 'max',
  },

  Z07: {
    type: 'multi-range',
    queries: [{ label: 'outstanding_requests', metric: 'outstanding_requests' }],
  },
  Z08: {
    type: 'multi-range',
    queries: [
      { label: 'max', metric: 'max_latency' },
      { label: 'avg', metric: 'avg_latency' },
      { label: 'min', metric: 'min_latency' },
    ],
  },
  Z09: {
    type: 'multi-range',
    queries: [
      { label: 'global', metric: 'global_sessions' },
      { label: 'local', metric: 'local_sessions' },
    ],
  },
  Z10: {
    type: 'multi-range',
    queries: [
      { label: 'znode_count', metric: 'znode_count' },
      { label: 'ephemerals', metric: 'ephemerals_count' },
    ],
  },
  Z11: {
    type: 'multi-range',
    queries: [{ label: 'approximate_data_size', metric: 'approximate_data_size' }],
  },
  Z12: {
    type: 'multi-range',
    queries: [{ label: 'watch_count', metric: 'watch_count' }],
  },
  Z13: {
    type: 'multi-range',
    queries: [
      { label: 'received', metric: 'packets_received' },
      { label: 'sent', metric: 'packets_sent' },
    ],
  },
  Z14: {
    type: 'multi-range',
    queries: [
      { label: 'num_alive_connections', metric: 'num_alive_connections' },
    ],
  },
  Z15: {
    type: 'multi-range',
    queries: [
      { label: 'conn_rejected', metric: 'connection_rejected', table: 'sum' },
      { label: 'conn_drop', metric: 'connection_drop_count', table: 'sum' },
      {
        label: 'unrecoverable',
        metric: 'unrecoverable_error_count',
        table: 'sum',
      },
      {
        label: 'digest_mismatch',
        metric: 'digest_mismatches_count',
        table: 'sum',
      },
    ],
  },
  Z17: {
    type: 'multi-range',
    queries: [
      { label: 'learners', metric: 'learners' },
      { label: 'synced_observers', metric: 'synced_observers' },
    ],
  },
  Z18: {
    type: 'multi-range',
    queries: [
      { label: 'commits', metric: 'commit_count', table: 'sum' },
      { label: 'snapshots', metric: 'snap_count', table: 'sum' },
      { label: 'proposals', metric: 'proposal_count', table: 'sum' },
    ],
  },
  Z21: {
    type: 'multi-range',
    queries: [
      {
        label: 'pool',
        metric: 'jvm_memory_pool_bytes_used',
        groupBy: ['pool'],
      },
    ],
  },
  Z22: {
    type: 'multi-range',
    queries: [
      {
        label: 'gc',
        metric: 'jvm_gc_collection_seconds',
        table: 'summary',
        field: 'count',
        rate: '5m',
        groupBy: ['gc'],
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
