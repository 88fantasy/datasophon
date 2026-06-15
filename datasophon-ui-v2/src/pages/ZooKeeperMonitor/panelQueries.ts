import type { PanelDef } from '../PrometheusMonitor/panelQueries';

export interface ZKDashboardVariables {
  instance: string;
  job: string;
}

export function replaceZKVars(
  promql: string,
  variables: Partial<ZKDashboardVariables>,
): string {
  return promql
    .replace(/\$instance/g, variables.instance || '.+')
    .replace(/\$job/g, variables.job || '.+');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  Z01: {
    type: 'instant',
    promql: 'max(quorum_size{instance=~"$instance",job=~"$job"})',
  },
  Z02: {
    type: 'instant',
    promql: 'leader_uptime{instance=~"$instance",job=~"$job"}',
  },
  Z03: {
    type: 'instant',
    promql: 'max(jvm_threads_current{instance=~"$instance",job=~"$job"})',
  },
  Z04: {
    type: 'instant',
    promql:
      'max(jvm_threads_deadlocked{instance=~"$instance",job=~"$job"})',
  },
  Z05: {
    type: 'instant',
    promql: 'sum(num_alive_connections{instance=~"$instance",job=~"$job"})',
  },
  Z06: {
    type: 'instant',
    promql:
      'max(open_file_descriptor_count{instance=~"$instance",job=~"$job"})',
  },
  Z07: {
    type: 'range',
    promql: 'outstanding_requests{instance=~"$instance",job=~"$job"}',
    seriesKey: 'instance',
  },
  Z08: {
    type: 'multi-range',
    queries: [
      {
        label: 'max',
        promql: 'max_latency{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'avg',
        promql: 'avg_latency{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'min',
        promql: 'min_latency{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z09: {
    type: 'multi-range',
    queries: [
      {
        label: 'global',
        promql: 'global_sessions{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'local',
        promql: 'local_sessions{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z10: {
    type: 'multi-range',
    queries: [
      {
        label: 'znode_count',
        promql: 'znode_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'ephemerals',
        promql: 'ephemerals_count{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z11: {
    type: 'range',
    promql: 'approximate_data_size{instance=~"$instance",job=~"$job"}',
  },
  Z12: {
    type: 'range',
    promql: 'watch_count{instance=~"$instance",job=~"$job"}',
    seriesKey: 'instance',
  },
  Z13: {
    type: 'multi-range',
    queries: [
      {
        label: 'received',
        promql: 'packets_received{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'sent',
        promql: 'packets_sent{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z14: {
    type: 'range',
    promql: 'num_alive_connections{instance=~"$instance",job=~"$job"}',
    seriesKey: 'instance',
  },
  Z15: {
    type: 'multi-range',
    queries: [
      {
        label: 'conn_rejected',
        promql: 'connection_rejected{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'conn_drop',
        promql: 'connection_drop_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'unrecoverable',
        promql:
          'unrecoverable_error_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'digest_mismatch',
        promql:
          'digest_mismatches_count{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z16: {
    type: 'range',
    promql: 'irate(election_time_sum{instance=~"$instance",job=~"$job"}[1m])',
  },
  Z17: {
    type: 'multi-range',
    queries: [
      {
        label: 'learners',
        promql: 'max(learners{instance=~"$instance",job=~"$job"})',
      },
      {
        label: 'synced_observers',
        promql:
          'max(synced_observers{instance=~"$instance",job=~"$job"})',
      },
    ],
  },
  Z18: {
    type: 'multi-range',
    queries: [
      {
        label: 'commits',
        promql: 'commit_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'snapshots',
        promql: 'snap_count{instance=~"$instance",job=~"$job"}',
      },
      {
        label: 'proposals',
        promql: 'proposal_count{instance=~"$instance",job=~"$job"}',
      },
    ],
  },
  Z19: {
    type: 'range',
    promql: 'irate(fsynctime_sum{instance=~"$instance",job=~"$job"}[1m])',
    seriesKey: 'instance',
  },
  Z20: {
    type: 'range',
    promql: 'rate(snapshottime_sum{instance=~"$instance",job=~"$job"}[5m])',
    seriesKey: 'instance',
  },
  Z21: {
    type: 'range',
    promql:
      'jvm_memory_pool_bytes_used{instance=~"$instance",job=~"$job"}',
    seriesKey: 'pool',
  },
  Z22: {
    type: 'range',
    promql:
      'rate(jvm_gc_collection_seconds_count{instance=~"$instance",job=~"$job"}[5m])',
    seriesKey: 'gc',
  },
  Z23: {
    type: 'range',
    promql:
      'rate(jvm_pause_time_ms_sum{instance=~"$instance",job=~"$job"}[5m])',
    seriesKey: 'instance',
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
