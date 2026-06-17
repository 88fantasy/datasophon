import type { PanelDef } from '../PrometheusMonitor/panelQueries';

export interface KyuubiDashboardVariables {
  instance: string;
  baseFilter: string;
  connType: string;
  opType: string;
  trendInterval: string;
}

export type KyuubiPanelDef = PanelDef & {
  tone?: 'error';
};

const CONN_TYPE_PLACEHOLDER = '$' + '{connType}';
const OP_TYPE_PLACEHOLDER = '$' + '{opType}';

export function replaceKyuubiVars(
  promql: string,
  variables: Partial<KyuubiDashboardVariables>,
): string {
  return promql
    .replace(
      /\$\{connType\}/g,
      variables.connType || 'connection_total_INTERACTIVE',
    )
    .replace(/\$\{opType\}/g, variables.opType || 'ExecuteStatement')
    .replace(/\$baseFilter/g, variables.baseFilter || '')
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$trendInterval/g, variables.trendInterval || '5m')
    .replace(/\{\s*,/g, '{')
    .replace(/,\s*\}/g, '}');
}

export const PANEL_QUERIES: Record<string, KyuubiPanelDef> = {
  KY01: {
    type: 'instant',
    promql: 'count(kyuubi_jvm_uptime{$baseFilter})',
  },
  KY02: {
    type: 'instant',
    promql: 'kyuubi_jvm_uptime{$baseFilter,instance=~"$instance"}',
  },
  KY03: {
    type: 'instant',
    promql:
      'sum(kyuubi_connection_opened_INTERACTIVE{$baseFilter,instance=~"$instance"})',
  },
  KY04: {
    type: 'instant',
    promql: 'sum(kyuubi_engine_total{$baseFilter,instance=~"$instance"})',
  },
  KY05: {
    type: 'instant',
    promql:
      'sum(kyuubi_exec_pool_threads_alive{$baseFilter,instance=~"$instance"})',
  },
  KY06: {
    type: 'instant',
    tone: 'error',
    promql:
      'sum(increase(kyuubi_operation_state_' +
      OP_TYPE_PLACEHOLDER +
      '_error_total{$baseFilter,instance=~"$instance"}[$trendInterval]))',
  },
  KY07: {
    type: 'range',
    promql:
      'increase(kyuubi_connection_total_INTERACTIVE{$baseFilter,instance=~"$instance"}[$trendInterval])',
    seriesKey: 'instance',
  },
  KY08: {
    type: 'range',
    promql:
      'increase(kyuubi_operation_total_ExecuteStatement{$baseFilter,instance=~"$instance"}[$trendInterval])',
    seriesKey: 'instance',
  },
  KY09: {
    type: 'multi-range',
    queries: [
      {
        label: 'Pending',
        promql:
          'kyuubi_operation_state_' +
          OP_TYPE_PLACEHOLDER +
          '_pending_total{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Running',
        promql:
          'kyuubi_operation_state_' +
          OP_TYPE_PLACEHOLDER +
          '_running_total{$baseFilter,instance=~"$instance"}',
      },
    ],
  },
  KY10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Launching',
        promql:
          'kyuubi_operation_state_LaunchEngine_running_total{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Startup Permit Limit',
        promql:
          'kyuubi_engine_startup_permit_limit_total{$baseFilter,instance=~"$instance"}',
      },
    ],
  },
  KY11: {
    type: 'range',
    tone: 'error',
    promql:
      'kyuubi_' +
      CONN_TYPE_PLACEHOLDER +
      '_failed{$baseFilter,instance=~"$instance"}',
    seriesKey: 'instance',
  },
  KY12: {
    type: 'multi-range',
    tone: 'error',
    queries: [
      {
        label: 'Operation Error',
        promql:
          'kyuubi_operation_state_' +
          OP_TYPE_PLACEHOLDER +
          '_error_total{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Operation Failed',
        promql:
          'kyuubi_operation_failed_total{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Engine Open Failed',
        promql:
          'kyuubi_engine_open_failed_count{$baseFilter,instance=~"$instance"}',
      },
    ],
  },
  KY13: {
    type: 'range',
    promql:
      'increase(kyuubi_backend_service_fetch_result_rows_rate_total{$baseFilter,instance=~"$instance"}[$trendInterval])',
    seriesKey: 'instance',
  },
  KY14: {
    type: 'range',
    promql:
      'kyuubi_operation_batch_pending_max_elapse{$baseFilter,instance=~"$instance"}',
    seriesKey: 'instance',
  },
  KY15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Used',
        promql:
          'kyuubi_memory_usage_total_used{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Usage Ratio',
        promql:
          'kyuubi_memory_usage_total_used{$baseFilter,instance=~"$instance"} / kyuubi_memory_usage_heap_max{$baseFilter,instance=~"$instance"}',
      },
    ],
  },
  KY16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Eden',
        promql:
          'kyuubi_memory_usage_pools_PS_Eden_Space_used{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Old Gen',
        promql:
          'kyuubi_memory_usage_pools_PS_Old_Gen_used{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Survivor',
        promql:
          'kyuubi_memory_usage_pools_PS_Survivor_Space_used{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Metaspace',
        promql:
          'kyuubi_memory_usage_pools_Metaspace_used{$baseFilter,instance=~"$instance"}',
      },
      {
        label: 'Code Cache',
        promql:
          'kyuubi_memory_usage_pools_Code_Cache_used{$baseFilter,instance=~"$instance"}',
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
