import type { PanelDef } from '../_shared/panelTypes';

export interface ValkeyDashboardVariables {
  instance: string;
}

export function replaceValkeyVars(
  promql: string,
  variables: Partial<ValkeyDashboardVariables>,
): string {
  return promql.replace(/\$instance/g, variables.instance || '.+');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  // R1 — Overview Stat
  V01: {
    type: 'instant',
    promql: 'max(redis_uptime_in_seconds{instance=~"$instance"})',
  },
  V02: {
    type: 'instant',
    promql: 'sum(redis_connected_clients{instance=~"$instance"})',
  },
  // V03: memory usage % — division done in hook to handle max=0 edge case
  V03: {
    type: 'instant',
    promql:
      'sum(redis_memory_used_bytes{instance=~"$instance"}) / sum(redis_memory_max_bytes{instance=~"$instance"}) * 100',
  },
  // V03_max: used to detect maxmemory=0 (unlimited)
  V03_max: {
    type: 'instant',
    promql: 'sum(redis_memory_max_bytes{instance=~"$instance"})',
  },
  // V04: cache hit % (reverse threshold: >=95 green, 80-95 orange, <80 red)
  V04: {
    type: 'instant',
    promql:
      'sum(rate(redis_keyspace_hits_total{instance=~"$instance"}[5m])) * 100 / (sum(rate(redis_keyspace_hits_total{instance=~"$instance"}[5m])) + sum(rate(redis_keyspace_misses_total{instance=~"$instance"}[5m])))',
  },
  // R2 — Traffic
  // V05: commands by cmd — dynamic series via seriesKey
  V05: {
    type: 'range',
    promql:
      'sum(rate(redis_commands_total{instance=~"$instance"}[1m])) by (cmd)',
    seriesKey: 'cmd',
  },
  V06: {
    type: 'multi-range',
    queries: [
      {
        label: 'Hits',
        promql: 'irate(redis_keyspace_hits_total{instance=~"$instance"}[5m])',
      },
      {
        label: 'Misses',
        promql: 'irate(redis_keyspace_misses_total{instance=~"$instance"}[5m])',
      },
    ],
  },
  // R3 — Latency & Network
  // V07: avg time by command (ratio method) — Prometheus does division server-side
  V07: {
    type: 'range',
    promql:
      'sum(irate(redis_commands_duration_seconds_total{instance=~"$instance"}[1m])) by (cmd) / sum(irate(redis_commands_total{instance=~"$instance"}[1m])) by (cmd)',
    seriesKey: 'cmd',
  },
  V08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Input',
        promql:
          'sum(rate(redis_net_input_bytes_total{instance=~"$instance"}[5m]))',
      },
      {
        label: 'Output',
        promql:
          'sum(rate(redis_net_output_bytes_total{instance=~"$instance"}[5m]))',
      },
    ],
  },
  // R4 — Memory & Connections
  V09: {
    type: 'multi-range',
    queries: [
      {
        label: 'Used',
        promql: 'redis_memory_used_bytes{instance=~"$instance"}',
      },
      {
        label: 'Max',
        promql: 'redis_memory_max_bytes{instance=~"$instance"}',
      },
    ],
  },
  V10: {
    type: 'multi-range',
    queries: [
      {
        label: 'Connected',
        promql: 'sum(redis_connected_clients{instance=~"$instance"})',
      },
      {
        label: 'Blocked',
        promql: 'sum(redis_blocked_clients{instance=~"$instance"})',
      },
    ],
  },
  // R5 — Keyspace
  // V11: items by db — dynamic series
  V11: {
    type: 'range',
    promql: 'sum(redis_db_keys{instance=~"$instance"}) by (db)',
    seriesKey: 'db',
  },
  V12: {
    type: 'multi-range',
    queries: [
      {
        label: 'Not-Expiring',
        promql:
          'sum(redis_db_keys{instance=~"$instance"}) by (instance) - sum(redis_db_keys_expiring{instance=~"$instance"}) by (instance)',
      },
      {
        label: 'Expiring',
        promql:
          'sum(redis_db_keys_expiring{instance=~"$instance"}) by (instance)',
      },
    ],
  },
  V13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Expired',
        promql:
          'sum(rate(redis_expired_keys_total{instance=~"$instance"}[5m])) by (instance)',
      },
      {
        label: 'Evicted',
        promql:
          'sum(rate(redis_evicted_keys_total{instance=~"$instance"}[5m])) by (instance)',
      },
    ],
  },
  // R6 — Errors (补强)
  V14: {
    type: 'range',
    promql:
      'sum(rate(redis_rejected_connections_total{instance=~"$instance"}[5m])) by (instance)',
  },
};
