import type { PanelDef } from '../_shared/panelTypes';

export interface JuiceFSDashboardVariables {
  name: string;
}

export function replaceJuiceFSVars(
  promql: string,
  variables: Partial<JuiceFSDashboardVariables>,
  interval: string,
): string {
  return promql
    .replace(/\$name/g, variables.name || '.+')
    .replace(/\$__rate_interval/g, interval);
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  J01: {
    type: 'instant',
    promql: 'max(juicefs_uptime{vol_name="$name"})',
  },
  J02: {
    type: 'instant',
    promql: 'avg(juicefs_used_space{vol_name="$name"})',
  },
  J03: {
    type: 'instant',
    promql: 'avg(juicefs_used_inodes{vol_name="$name"})',
  },
  J04: {
    type: 'instant',
    promql: 'count(juicefs_uptime{vol_name="$name"})',
  },
  J05: {
    type: 'instant',
    promql:
      'sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) * 100 / (sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) + sum(rate(juicefs_blockcache_miss{vol_name="$name"}[$__rate_interval])))',
  },
  J06: {
    type: 'instant',
    promql: 'sum(juicefs_staging_blocks{vol_name="$name"})',
  },
  J07: {
    type: 'range',
    promql:
      'sum(rate(juicefs_fuse_ops_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)',
    seriesKey: 'instance',
  },
  J08: {
    type: 'multi-range',
    queries: [
      {
        label: 'Write',
        promql:
          'sum(rate(juicefs_fuse_written_size_bytes_sum{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)',
      },
      {
        label: 'Read',
        promql:
          'sum(rate(juicefs_fuse_read_size_bytes_sum{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)',
      },
    ],
  },
  J09: {
    type: 'range',
    promql:
      'sum(rate(juicefs_fuse_ops_durations_histogram_seconds_sum{vol_name="$name"}[$__rate_interval])) by (instance,mp) * 1000000 / sum(rate(juicefs_fuse_ops_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (instance,mp)',
    seriesKey: 'instance',
  },
  J10: {
    type: 'range',
    promql:
      'sum(rate(juicefs_transaction_durations_histogram_seconds_sum{vol_name="$name"}[$__rate_interval])) by (instance,mp) * 1000000 / sum(rate(juicefs_transaction_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (instance,mp)',
    seriesKey: 'instance',
  },
  J11: {
    type: 'range',
    promql:
      'sum(rate(juicefs_object_request_durations_histogram_seconds_sum{vol_name="$name"}[$__rate_interval])) by (instance) * 1000000 / sum(rate(juicefs_object_request_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (instance)',
    seriesKey: 'instance',
  },
  J12: {
    type: 'range',
    promql:
      'sum(rate(juicefs_object_request_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (method)',
    seriesKey: 'method',
  },
  J13: {
    type: 'multi-range',
    queries: [
      {
        label: 'Object Request Errors',
        promql:
          'sum(rate(juicefs_object_request_errors{vol_name="$name"}[$__rate_interval]))',
      },
      {
        label: 'Transaction Restarts',
        promql:
          'sum(rate(juicefs_transaction_restart{vol_name="$name"}[$__rate_interval])) by (instance)',
      },
    ],
  },
  J14: {
    type: 'range',
    promql: 'sum(juicefs_blockcache_bytes{vol_name="$name"}) by (instance,mp)',
    seriesKey: 'instance',
  },
  J15: {
    type: 'multi-range',
    queries: [
      {
        label: 'By Count',
        promql:
          'sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) by (instance,mp) *100 / (sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) by (instance,mp) + sum(rate(juicefs_blockcache_miss{vol_name="$name"}[$__rate_interval])) by (instance,mp))',
      },
      {
        label: 'By Bytes',
        promql:
          'sum(rate(juicefs_blockcache_hit_bytes{vol_name="$name"}[$__rate_interval])) by (instance,mp) *100 / (sum(rate(juicefs_blockcache_hit_bytes{vol_name="$name"}[$__rate_interval])) by (instance,mp) + sum(rate(juicefs_blockcache_miss_bytes{vol_name="$name"}[$__rate_interval])) by (instance,mp))',
      },
    ],
  },
  J16: {
    type: 'multi-range',
    queries: [
      {
        label: 'PUT',
        promql:
          'sum(rate(juicefs_object_request_data_bytes{method="PUT",vol_name="$name"}[$__rate_interval])) by (instance,method)',
      },
      {
        label: 'GET',
        promql:
          'sum(rate(juicefs_object_request_data_bytes{method="GET",vol_name="$name"}[$__rate_interval])) by (instance,method)',
      },
    ],
  },
  J17: {
    type: 'multi-range',
    queries: [
      {
        label: 'CPU %',
        promql:
          'sum(rate(juicefs_cpu_usage{vol_name="$name"}[$__rate_interval])*100 < 1000) by (instance,mp)',
      },
      {
        label: 'Memory',
        promql: 'sum(juicefs_memory{vol_name="$name"}) by (instance,mp)',
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
