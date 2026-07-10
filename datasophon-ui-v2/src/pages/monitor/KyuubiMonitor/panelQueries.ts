import type { DorisPanelDescriptor } from '../_shared/dorisService';

export interface KyuubiDashboardVariables {
  instance: string;
  job: string;
  connType: string;
  opType: string;
}

export const KYUUBI_CONN_TYPES = [
  'thrift_binary_connection',
  'rest_connection',
  'thrift_http_connection',
  'metadata_request',
];

export const KYUUBI_OP_TYPES = [
  'ExecuteStatement',
  'BatchJobSubmission',
  'LaunchEngine',
];

export type KyuubiTrendInterval = '1m' | '5m' | '15m' | '1h';

const TREND_SECONDS: Record<KyuubiTrendInterval, number> = {
  '1m': 60,
  '5m': 300,
  '15m': 900,
  '1h': 3600,
};

export function buildKyuubiPanelQueries(
  connType = 'thrift_binary_connection',
  opType = 'ExecuteStatement',
  trendInterval: KyuubiTrendInterval = '5m',
): Record<string, DorisPanelDescriptor> {
  const trendSeconds = TREND_SECONDS[trendInterval];
  return {
    KY01: { type: 'instant', metric: 'kyuubi_jvm_uptime', agg: 'count' },
    KY02: { type: 'instant', metric: 'kyuubi_jvm_uptime' },
    KY03: {
      type: 'instant',
      metric: 'kyuubi_connection_opened_INTERACTIVE',
      agg: 'sum',
    },
    KY04: { type: 'instant', metric: 'kyuubi_engine_total', agg: 'sum' },
    KY05: {
      type: 'instant',
      metric: 'kyuubi_exec_pool_threads_alive',
      agg: 'sum',
    },
    KY06: {
      type: 'range-stat',
      metric: `kyuubi_operation_state_${opType}_error_total`,
      rate: trendInterval,
      scale: trendSeconds,
      table: 'sum',
    },
    KY07: {
      type: 'multi-range',
      queries: [
        {
          label: 'sessions',
          metric: 'kyuubi_connection_total_INTERACTIVE',
          rate: trendInterval,
          scale: trendSeconds,
        },
      ],
    },
    KY08: {
      type: 'multi-range',
      queries: [
        {
          label: 'operations',
          metric: 'kyuubi_operation_total_ExecuteStatement',
          rate: trendInterval,
          scale: trendSeconds,
        },
      ],
    },
    KY09: {
      type: 'multi-range',
      queries: [
        {
          label: 'Pending',
          metric: `kyuubi_operation_state_${opType}_pending_total`,
          table: 'sum',
        },
        {
          label: 'Running',
          metric: `kyuubi_operation_state_${opType}_running_total`,
          table: 'sum',
        },
      ],
    },
    KY10: {
      type: 'multi-range',
      queries: [
        {
          label: 'Launching',
          metric: 'kyuubi_operation_state_LaunchEngine_running_total',
          table: 'sum',
        },
        {
          label: 'Startup Permit Limit',
          metric: 'kyuubi_engine_startup_permit_limit_total',
          table: 'sum',
        },
      ],
    },
    KY11: {
      type: 'multi-range',
      queries: [
        {
          label: 'Connection Failed',
          metric: `kyuubi_${connType}_failed`,
        },
      ],
    },
    KY12: {
      type: 'multi-range',
      queries: [
        {
          label: 'Operation Error',
          metric: `kyuubi_operation_state_${opType}_error_total`,
          table: 'sum',
        },
      ],
    },
    KY13: {
      type: 'multi-range',
      queries: [
        {
          label: 'Fetch Rows',
          metric: 'kyuubi_backend_service_fetch_result_rows_rate_total',
          rate: trendInterval,
          scale: trendSeconds,
          table: 'sum',
        },
      ],
    },
    KY14: {
      type: 'multi-range',
      queries: [
        {
          label: 'Max Pending Elapse',
          metric: 'kyuubi_operation_batch_pending_max_elapse',
        },
      ],
    },
    KY15: {
      type: 'multi-range',
      queries: [
        { label: 'Used', metric: 'kyuubi_memory_usage_total_used' },
        {
          label: 'Usage Ratio',
          metric: 'kyuubi_memory_usage_total_used',
          denominatorMetric: 'kyuubi_memory_usage_heap_max',
        },
      ],
    },
    KY16: {
      type: 'multi-range',
      queries: [
        { label: 'Eden', metric: 'kyuubi_memory_usage_pools_Eden_Space_used' },
        {
          label: 'Old Gen',
          metric: 'kyuubi_memory_usage_pools_Tenured_Gen_used',
        },
        {
          label: 'Survivor',
          metric: 'kyuubi_memory_usage_pools_Survivor_Space_used',
        },
        {
          label: 'Metaspace',
          metric: 'kyuubi_memory_usage_pools_Metaspace_used',
        },
        {
          label: 'Code Cache',
          metric: 'kyuubi_memory_usage_pools_Code_Cache_used',
        },
      ],
    },
  };
}

export const PANEL_QUERIES = buildKyuubiPanelQueries();
