import type { PanelDef } from '../_shared/panelTypes';

export interface MySQLDashboardVariables {
  instance: string;
  job: string;
}

export function replaceMySQLVars(
  promql: string,
  variables: Partial<MySQLDashboardVariables>,
  interval: string,
): string {
  return promql
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$job/g, variables.job || '.+')
    .replace(/\[\$__interval\]/g, `[${interval}]`);
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  M01: {
    type: 'instant',
    promql: 'mysql_global_status_uptime{job=~"$job", instance=~"$instance"}',
  },
  M02: {
    type: 'instant',
    promql:
      'rate(mysql_global_status_queries{job=~"$job", instance=~"$instance"}[$__interval])',
  },
  M03: {
    type: 'instant',
    promql:
      'sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval])) / sum(mysql_global_variables_max_connections{job=~"$job", instance=~"$instance"}) * 100',
  },
  M04: {
    type: 'instant',
    promql:
      'mysql_global_variables_innodb_buffer_pool_size{job=~"$job", instance=~"$instance"}',
  },
  M05: {
    type: 'instant',
    promql:
      'sum(rate(mysql_global_status_slow_queries{job=~"$job", instance=~"$instance"}[$__interval]))',
  },
  M06: {
    type: 'instant',
    promql:
      'sum(rate(mysql_global_status_aborted_connects{job=~"$job", instance=~"$instance"}[$__interval]))',
  },
  M07: {
    type: 'range',
    promql:
      'rate(mysql_global_status_questions{job=~"$job", instance=~"$instance"}[$__interval])',
    seriesKey: 'instance',
  },
  M08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Inbound',
        promql:
          'sum(rate(mysql_global_status_bytes_received{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Outbound',
        promql:
          'sum(rate(mysql_global_status_bytes_sent{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
    ],
  },
  M09: {
    type: 'multi-range',
    queries: [
      {
        label: 'Connections',
        promql:
          'sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Max Used',
        promql:
          'sum(mysql_global_status_max_used_connections{job=~"$job", instance=~"$instance"})',
      },
      {
        label: 'Max Connections',
        promql:
          'sum(mysql_global_variables_max_connections{job=~"$job", instance=~"$instance"})',
      },
    ],
  },
  M10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Threads Connected',
        promql:
          'sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Threads Running',
        promql:
          'sum(max_over_time(mysql_global_status_threads_running{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
    ],
  },
  M11: {
    type: 'range',
    promql:
      'sum(rate(mysql_global_status_slow_queries{job=~"$job", instance=~"$instance"}[$__interval]))',
  },
  M12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Aborted Connects',
        promql:
          'sum(rate(mysql_global_status_aborted_connects{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Aborted Clients',
        promql:
          'sum(rate(mysql_global_status_aborted_clients{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
    ],
  },
  M13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Immediate',
        promql:
          'sum(rate(mysql_global_status_table_locks_immediate{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Waited',
        promql:
          'sum(rate(mysql_global_status_table_locks_waited{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
    ],
  },
  M14: {
    type: 'multi-range',
    queries: [
      {
        label: 'Buffer Pool Data',
        promql:
          'sum(mysql_global_status_innodb_page_size{job=~"$job", instance=~"$instance"} * on (instance) mysql_global_status_buffer_pool_pages{job=~"$job", instance=~"$instance", state="data"})',
      },
      {
        label: 'Log Buffer',
        promql:
          'sum(mysql_global_variables_innodb_log_buffer_size{job=~"$job", instance=~"$instance"})',
      },
      {
        label: 'Key Buffer',
        promql:
          'sum(mysql_global_variables_key_buffer_size{job=~"$job", instance=~"$instance"})',
      },
      {
        label: 'Adaptive Hash',
        promql:
          'sum(mysql_global_status_innodb_mem_adaptive_hash{job=~"$job", instance=~"$instance"})',
      },
    ],
  },
  M15: {
    type: 'multi-range',
    queries: [
      {
        label: 'Tmp Tables',
        promql:
          'sum(rate(mysql_global_status_created_tmp_tables{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Tmp Disk Tables',
        promql:
          'sum(rate(mysql_global_status_created_tmp_disk_tables{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
      {
        label: 'Tmp Files',
        promql:
          'sum(rate(mysql_global_status_created_tmp_files{job=~"$job", instance=~"$instance"}[$__interval]))',
      },
    ],
  },
  M16: {
    type: 'range',
    promql:
      'rate(mysql_global_status_handlers_total{job=~"$job", instance=~"$instance", handler!~"commit|rollback|savepoint.*|prepare"}[$__interval])',
    seriesKey: 'handler',
  },
  M17: {
    type: 'range',
    promql:
      'topk(5, rate(mysql_global_status_commands_total{job=~"$job", instance=~"$instance"}[$__interval]) > 0)',
    seriesKey: 'command',
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
