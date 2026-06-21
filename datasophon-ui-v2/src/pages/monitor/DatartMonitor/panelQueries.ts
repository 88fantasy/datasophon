import type { PanelDef } from '../_shared/panelTypes';

export interface DatartDashboardVariables {
  application: string;
  instance?: string;
  memory_pool_heap: string;
  hikaricp: string;
}

export function replaceDatartVars(
  promql: string,
  variables: Partial<DatartDashboardVariables>,
): string {
  return promql
    .replace(/\$application/g, variables.application || 'datart')
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$memory_pool_heap/g, variables.memory_pool_heap || 'G1 Old Gen')
    .replace(/\$hikaricp/g, variables.hikaricp || 'HikariPool-1');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  D01: {
    type: 'instant',
    promql:
      'process_uptime_seconds{application="$application", instance=~"$instance"}',
  },
  D02: {
    type: 'instant',
    promql:
      'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="heap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="heap"})',
  },
  D03: {
    type: 'instant',
    promql:
      'sum(jvm_memory_used_bytes{application="$application", instance=~"$instance", area="nonheap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance=~"$instance", area="nonheap"})',
  },
  D04: {
    type: 'instant',
    promql:
      'process_cpu_usage{instance=~"$instance", application="$application"} * 100',
  },
  D05: {
    type: 'instant',
    promql:
      'hikaricp_connections_active{instance=~"$instance", application="$application", pool="$hikaricp"}',
  },
  D06: {
    type: 'instant',
    promql:
      'sum(irate(logback_events_total{instance=~"$instance", application="$application", level="error"}[5m]))',
  },
  D07: {
    type: 'range',
    promql:
      'irate(http_server_requests_seconds_count{instance=~"$instance", application="$application", uri!~".*actuator.*"}[5m])',
    seriesKey: 'uri',
  },
  D08: {
    type: 'range',
    promql:
      'irate(http_server_requests_seconds_sum{instance=~"$instance", application="$application", exception="None", uri!~".*actuator.*"}[5m]) / irate(http_server_requests_seconds_count{instance=~"$instance", application="$application", exception="None", uri!~".*actuator.*"}[5m])',
    seriesKey: 'uri',
  },
  D09: {
    type: 'multi-range',
    queries: [
      {
        label: 'System CPU',
        promql:
          'system_cpu_usage{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Process CPU',
        promql:
          'process_cpu_usage{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Load 1m',
        promql:
          'system_load_average_1m{instance=~"$instance", application="$application"}',
      },
    ],
  },
  D10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Used',
        promql:
          'jvm_memory_used_bytes{instance=~"$instance", application="$application", id="$memory_pool_heap"}',
      },
      {
        label: 'Committed',
        promql:
          'jvm_memory_committed_bytes{instance=~"$instance", application="$application", id="$memory_pool_heap"}',
      },
      {
        label: 'Max',
        promql:
          'jvm_memory_max_bytes{instance=~"$instance", application="$application", id="$memory_pool_heap"}',
      },
    ],
  },
  D11: {
    type: 'range',
    promql:
      'irate(jvm_gc_pause_seconds_count{instance=~"$instance", application="$application"}[5m])',
    seriesKey: 'action',
  },
  D12: {
    type: 'range',
    promql:
      'irate(jvm_gc_pause_seconds_sum{instance=~"$instance", application="$application"}[5m])',
    seriesKey: 'action',
  },
  D13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Daemon',
        promql:
          'jvm_threads_daemon_threads{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Live',
        promql:
          'jvm_threads_live_threads{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Peak',
        promql:
          'jvm_threads_peak_threads{instance=~"$instance", application="$application"}',
      },
    ],
  },
  D14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Active',
        promql:
          'hikaricp_connections_active{instance=~"$instance", application="$application", pool="$hikaricp"}',
      },
      {
        label: 'Idle',
        promql:
          'hikaricp_connections_idle{instance=~"$instance", application="$application", pool="$hikaricp"}',
      },
      {
        label: 'Pending',
        promql:
          'hikaricp_connections_pending{instance=~"$instance", application="$application", pool="$hikaricp"}',
      },
    ],
  },
  D15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Acquire Time',
        promql:
          'hikaricp_connections_acquire_seconds_sum{instance=~"$instance", application="$application", pool="$hikaricp"} / hikaricp_connections_acquire_seconds_count{instance=~"$instance", application="$application", pool="$hikaricp"}',
      },
      {
        label: 'Usage Time',
        promql:
          'hikaricp_connections_usage_seconds_sum{instance=~"$instance", application="$application", pool="$hikaricp"} / hikaricp_connections_usage_seconds_count{instance=~"$instance", application="$application", pool="$hikaricp"}',
      },
    ],
  },
  D16: {
    type: 'multi-range',
    queries: [
      {
        label: 'Current Threads',
        promql:
          'tomcat_threads_current_threads{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Busy Threads',
        promql:
          'tomcat_threads_busy_threads{instance=~"$instance", application="$application"}',
      },
      {
        label: 'Active Sessions',
        promql:
          'tomcat_sessions_active_current_sessions{instance=~"$instance", application="$application"}',
      },
    ],
  },
  D17: {
    type: 'multi-range',
    queries: [
      {
        label: 'Sent',
        promql:
          'irate(tomcat_global_sent_bytes_total{instance=~"$instance", application="$application"}[5m])',
      },
      {
        label: 'Received',
        promql:
          'irate(tomcat_global_received_bytes_total{instance=~"$instance", application="$application"}[5m])',
      },
    ],
  },
  D18: {
    type: 'range',
    promql:
      'sum(irate(logback_events_total{instance=~"$instance", application="$application"}[5m])) by (level)',
    seriesKey: 'level',
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
