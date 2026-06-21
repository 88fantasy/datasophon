import type { PanelDef } from '../_shared/panelTypes';

export interface DorisDashboardVariables {
  cluster: string;
  feInstance: string;
  beInstance: string;
  interval: string;
}

export type DorisDashboardSegment = 'cluster' | 'fe' | 'be';

export function replaceDorisVars(
  promql: string,
  variables: Partial<DorisDashboardVariables>,
): string {
  return promql
    .replace(/\$cluster/g, variables.cluster || 'doris')
    .replace(/\$fe_instance/g, variables.feInstance || '.+')
    .replace(/\$be_instance/g, variables.beInstance || '.+')
    .replace(/\$interval/g, variables.interval || '2m');
}

export const PANEL_QUERIES: Record<string, PanelDef> = {
  'DO-A01': {
    type: 'instant',
    promql: 'count(up{group="fe", job="$cluster"})',
  },
  'DO-A02': {
    type: 'instant',
    promql: 'count(up{group="fe", job="$cluster"} == 1)',
  },
  'DO-A03': {
    type: 'instant',
    promql: 'count(up{group="be", job="$cluster"})',
  },
  'DO-A04': {
    type: 'instant',
    promql: 'count(up{group="be", job="$cluster"} == 1)',
  },
  'DO-A05': {
    type: 'instant',
    promql: 'SUM(doris_be_disks_local_used_capacity{job="$cluster"})',
  },
  'DO-A06': {
    type: 'instant',
    promql: 'SUM(doris_be_disks_total_capacity{job="$cluster"})',
  },
  'DO-A07': {
    type: 'range',
    promql:
      'sum by (job)(rate(doris_fe_query_total{group="fe", job="$cluster"}[$interval]))',
    seriesKey: 'job',
  },
  'DO-A08': {
    type: 'range',
    promql:
      'sum(jvm_heap_size_bytes{group="fe", job="$cluster", type="used"} * 100) by (instance) / sum(jvm_heap_size_bytes{group="fe", job="$cluster", type="max"}) by (instance)',
    seriesKey: 'instance',
  },
  'DO-A09': {
    type: 'range',
    promql:
      '(sum(rate(doris_be_cpu{mode="idle", job="$cluster"}[$interval])) by (job)) / (sum(rate(doris_be_cpu{job="$cluster"}[$interval])) by (job)) * 100',
    seriesKey: 'job',
  },

  'DO-B01': {
    type: 'range',
    promql:
      'rate(doris_fe_request_total{job="$cluster", group="fe", instance=~"$fe_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-B02': {
    type: 'range',
    promql:
      'rate(doris_fe_query_total{job="$cluster", group="fe", instance=~"$fe_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-B03': {
    type: 'range',
    promql:
      'sum(doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.99"}) by (instance)',
    seriesKey: 'instance',
  },
  'DO-B04': {
    type: 'multi-range',
    queries: [
      {
        label: 'p50',
        promql:
          'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.5"}',
      },
      {
        label: 'p75',
        promql:
          'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.75"}',
      },
      {
        label: 'p99',
        promql:
          'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.99"}',
      },
    ],
  },
  'DO-B05': {
    type: 'multi-range',
    queries: [
      {
        label: 'cumulative',
        promql: 'doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}',
      },
      {
        label: 'rate_1m',
        promql:
          'rate(doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}[$interval])',
      },
    ],
  },
  'DO-B06': {
    type: 'range',
    promql:
      'rate(doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}[$interval]) / rate(doris_fe_query_total{job="$cluster", group="fe", instance=~"$fe_instance"}[$interval]) * 100',
    seriesKey: 'instance',
  },
  'DO-B07': {
    type: 'range',
    promql:
      'doris_fe_connection_total{job="$cluster", instance=~"$fe_instance"}',
    seriesKey: 'instance',
  },
  'DO-B08': {
    type: 'range',
    promql:
      'doris_fe_max_tablet_compaction_score{job="$cluster", instance=~"$fe_instance"}',
    seriesKey: 'instance',
  },
  'DO-B09': {
    type: 'range',
    promql: 'doris_fe_scheduled_tablet_num{job="$cluster"}',
  },
  'DO-B10': {
    type: 'multi-range',
    queries: [
      {
        label: 'used',
        promql:
          'jvm_heap_size_bytes{job="$cluster", instance=~"$fe_instance", type="used"}',
      },
      {
        label: 'max',
        promql:
          'jvm_heap_size_bytes{job="$cluster", instance=~"$fe_instance", type="max"}',
      },
    ],
  },
  'DO-B11': {
    type: 'multi-range',
    queries: [
      {
        label: 'gc_count',
        promql:
          'jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="count"}',
      },
      {
        label: 'avg_time_ms',
        promql:
          'sum(jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="time"}) / sum(jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="count"})',
      },
    ],
  },
  'DO-B12': {
    type: 'range',
    promql:
      'doris_fe_editlog_write_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.99"}',
    seriesKey: 'instance',
  },

  'DO-C01': {
    type: 'range',
    promql:
      '(sum(rate(doris_be_cpu{mode="idle", job="$cluster", instance=~"$be_instance"}[$interval])) by (job, instance)) / (sum(rate(doris_be_cpu{job="$cluster", instance=~"$be_instance"}[$interval])) by (job, instance)) * 100',
    seriesKey: 'instance',
  },
  'DO-C02': {
    type: 'range',
    promql:
      'doris_be_memory_allocated_bytes{job="$cluster", instance=~"$be_instance"}',
    seriesKey: 'instance',
  },
  'DO-C03': {
    type: 'multi-range',
    queries: [
      {
        label: 'used_pct',
        promql:
          '(SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path) - SUM(doris_be_disks_avail_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)) / SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)',
      },
      {
        label: 'local_used_pct',
        promql:
          'SUM(doris_be_disks_local_used_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path) / SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)',
      },
    ],
  },
  'DO-C04': {
    type: 'range',
    promql:
      'rate(doris_be_disk_io_time_ms{job="$cluster", instance=~"$be_instance"}[$interval]) / 10',
    seriesKey: 'instance',
  },
  'DO-C05': {
    type: 'multi-range',
    queries: [
      {
        label: 'base',
        promql:
          'rate(doris_be_compaction_bytes_total{type="base", job="$cluster", instance=~"$be_instance"}[$interval])',
      },
      {
        label: 'cumulative',
        promql:
          'rate(doris_be_compaction_bytes_total{type="cumulative", job="$cluster", instance=~"$be_instance"}[$interval])',
      },
    ],
  },
  'DO-C06': {
    type: 'range',
    promql:
      'rate(doris_be_query_scan_bytes{job="$cluster", instance=~"$be_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-C07': {
    type: 'range',
    promql:
      'rate(doris_be_query_scan_rows{job="$cluster", instance=~"$be_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-C08': {
    type: 'range',
    promql:
      'rate(doris_be_push_request_write_bytes{job="$cluster", instance=~"$be_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-C09': {
    type: 'range',
    promql:
      'rate(doris_be_push_request_write_rows{job="$cluster", instance=~"$be_instance"}[$interval])',
    seriesKey: 'instance',
  },
  'DO-C10': {
    type: 'range',
    promql:
      'irate(doris_be_push_request_duration_us{job="$cluster", instance=~"$be_instance"}[$interval]) / 1000',
    seriesKey: 'instance',
  },
  'DO-C11': {
    type: 'multi-range',
    queries: [
      {
        label: 'send',
        promql:
          'irate(doris_be_network_send_bytes{job="$cluster", group="be", device!="lo", instance=~"$be_instance"}[$interval])',
      },
      {
        label: 'recv',
        promql:
          'irate(doris_be_network_receive_bytes{job="$cluster", group="be", device!="lo", instance=~"$be_instance"}[$interval])',
      },
    ],
  },
};

export const DORIS_RANGE_PANEL_IDS = [
  'DO-A07',
  'DO-A08',
  'DO-A09',
  'DO-B01',
  'DO-B02',
  'DO-B03',
  'DO-B04',
  'DO-B05',
  'DO-B06',
  'DO-B07',
  'DO-B08',
  'DO-B09',
  'DO-B10',
  'DO-B11',
  'DO-B12',
  'DO-C01',
  'DO-C02',
  'DO-C03',
  'DO-C04',
  'DO-C05',
  'DO-C06',
  'DO-C07',
  'DO-C08',
  'DO-C09',
  'DO-C10',
  'DO-C11',
];

export const DORIS_SEGMENT_PANEL_IDS: Record<DorisDashboardSegment, string[]> =
  {
    cluster: [
      'DO-A01',
      'DO-A02',
      'DO-A03',
      'DO-A04',
      'DO-A05',
      'DO-A06',
      'DO-A07',
      'DO-A08',
      'DO-A09',
    ],
    fe: [
      'DO-B01',
      'DO-B02',
      'DO-B03',
      'DO-B04',
      'DO-B05',
      'DO-B06',
      'DO-B07',
      'DO-B08',
      'DO-B09',
      'DO-B10',
      'DO-B11',
      'DO-B12',
    ],
    be: [
      'DO-C01',
      'DO-C02',
      'DO-C03',
      'DO-C04',
      'DO-C05',
      'DO-C06',
      'DO-C07',
      'DO-C08',
      'DO-C09',
      'DO-C10',
      'DO-C11',
    ],
  };

export function getDorisSegmentPanelIds(
  segment: DorisDashboardSegment,
): string[] {
  return DORIS_SEGMENT_PANEL_IDS[segment];
}
