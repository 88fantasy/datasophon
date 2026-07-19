import type {
  DorisPanelDescriptor,
  DorisRangeQuery,
} from '../_shared/dorisService';

export type { DorisPanelDescriptor as ValkeyPanelDescriptor };

export interface ValkeyDashboardVariables {
  instance: string;
}

export const VALKEY_JOB_FILTER = '^ValkeyExporter$';

export const VALKEY_KEY_TOTAL_QUERY: DorisRangeQuery = {
  label: 'Total',
  metric: 'redis_db_keys',
  groupBy: ['db'],
};

export const VALKEY_KEY_EXPIRING_QUERY: DorisRangeQuery = {
  label: 'Expiring',
  metric: 'redis_db_keys_expiring',
  groupBy: ['db'],
};

export const PANEL_QUERIES: Record<string, DorisPanelDescriptor> = {
  // R1 — Overview Stat
  V01: { type: 'instant', metric: 'redis_uptime_in_seconds', agg: 'max' },
  V02: { type: 'instant', metric: 'redis_connected_clients', agg: 'sum' },
  V03: {
    type: 'instant',
    metric: 'redis_memory_used_bytes',
    agg: 'sum',
    denominatorMetric: 'redis_memory_max_bytes',
    scale: 100,
  },
  V03_max: {
    type: 'instant',
    metric: 'redis_memory_max_bytes',
    agg: 'sum',
  },
  // V04 is derived in useValkeyDashboard from the latest hits/misses rates.
  V04: {
    type: 'multi-range',
    queries: [
      {
        label: 'Hits',
        metric: 'redis_keyspace_hits_total',
        rate: '5m',
        table: 'sum',
      },
      {
        label: 'Misses',
        metric: 'redis_keyspace_misses_total',
        rate: '5m',
        table: 'sum',
      },
    ],
  },

  // R2 — Traffic
  V05: {
    type: 'multi-range',
    queries: [
      {
        label: 'Commands',
        metric: 'redis_commands_total',
        rate: '1m',
        table: 'sum',
        groupBy: ['cmd'],
      },
    ],
  },
  V06: {
    type: 'multi-range',
    queries: [
      {
        label: 'Hits',
        metric: 'redis_keyspace_hits_total',
        rate: '5m',
        table: 'sum',
      },
      {
        label: 'Misses',
        metric: 'redis_keyspace_misses_total',
        rate: '5m',
        table: 'sum',
      },
    ],
  },

  // R3 — Latency & Network
  V07: {
    type: 'multi-range',
    queries: [
      {
        label: 'Avg',
        metric: 'redis_commands_duration_seconds_total',
        denominatorMetric: 'redis_commands_total',
        rate: '1m',
        table: 'sum',
        denominatorTable: 'sum',
        groupBy: ['cmd'],
      },
    ],
  },
  V08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Input',
        metric: 'redis_net_input_bytes_total',
        rate: '5m',
        table: 'sum',
      },
      {
        label: 'Output',
        metric: 'redis_net_output_bytes_total',
        rate: '5m',
        table: 'sum',
      },
    ],
  },

  // R4 — Memory & Connections
  V09: {
    type: 'multi-range',
    queries: [
      { label: 'Used', metric: 'redis_memory_used_bytes' },
      { label: 'Max', metric: 'redis_memory_max_bytes' },
    ],
  },
  V10: {
    type: 'multi-range',
    queries: [
      { label: 'Connected', metric: 'redis_connected_clients' },
      { label: 'Blocked', metric: 'redis_blocked_clients' },
    ],
  },

  // R5 — Keyspace
  V11: {
    type: 'multi-range',
    queries: [{ label: 'Items', metric: 'redis_db_keys', groupBy: ['db'] }],
  },
  // V12 keeps the descriptors here, while the hook reads the raw matrices so
  // total and expiring keys can be paired by instance/job/db/timestamp.
  V12: {
    type: 'multi-range',
    queries: [VALKEY_KEY_TOTAL_QUERY, VALKEY_KEY_EXPIRING_QUERY],
  },
  V13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Expired',
        metric: 'redis_expired_keys_total',
        rate: '5m',
        table: 'sum',
      },
      {
        label: 'Evicted',
        metric: 'redis_evicted_keys_total',
        rate: '5m',
        table: 'sum',
      },
    ],
  },

  // R6 — Errors
  V14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Rejected',
        metric: 'redis_rejected_connections_total',
        rate: '5m',
        table: 'sum',
      },
    ],
  },
};
