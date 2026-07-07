import type { DorisPanelDescriptor } from '../../monitor/_shared/dorisService';

export const COLLECTOR_PANEL_IDS = [
  'queueUsage',
  'sentRate',
  'failedRate',
  'refusedDroppedRate',
  'uptime',
] as const;

export type CollectorPanelId = (typeof COLLECTOR_PANEL_IDS)[number];

const SIGNALS = [
  { label: 'Metrics', suffix: 'metric_points' },
  { label: 'Logs', suffix: 'log_records' },
  { label: 'Traces', suffix: 'spans' },
] as const;

function signalRateQueries(
  metricPrefix: string,
  groupBy: string[],
  labelSuffix?: string,
) {
  return SIGNALS.map(({ label, suffix }) => ({
    label: labelSuffix ? `${label} ${labelSuffix}` : label,
    metric: `${metricPrefix}${suffix}`,
    rate: '1m' as const,
    table: 'sum' as const,
    groupBy,
  }));
}

export const COLLECTOR_PANEL_QUERIES: Record<
  CollectorPanelId,
  DorisPanelDescriptor
> = {
  queueUsage: {
    type: 'multi-range',
    queries: [
      {
        label: 'Queue usage',
        metric: 'otelcol_exporter_queue_size',
        denominatorMetric: 'otelcol_exporter_queue_capacity',
        scale: 100,
        table: 'gauge',
        groupBy: ['exporter'],
      },
    ],
  },
  sentRate: {
    type: 'multi-range',
    queries: signalRateQueries('otelcol_exporter_sent_', ['exporter']),
  },
  failedRate: {
    type: 'multi-range',
    queries: signalRateQueries(
      'otelcol_receiver_failed_',
      ['receiver', 'transport'],
      'receiver failed',
    ),
  },
  refusedDroppedRate: {
    type: 'multi-range',
    queries: [
      ...signalRateQueries(
        'otelcol_receiver_refused_',
        ['receiver', 'transport'],
        'refused',
      ),
      ...signalRateQueries('otelcol_processor_dropped_', ['processor'], 'dropped'),
      {
        label: 'Filtered datapoints',
        metric: 'otelcol_processor_filter_datapoints_filtered',
        rate: '1m' as const,
        table: 'sum' as const,
        groupBy: ['processor'],
      },
    ],
  },
  uptime: {
    type: 'multi-range',
    queries: [
      {
        label: 'Uptime',
        metric: 'otelcol_process_uptime',
        table: 'sum',
      },
    ],
  },
};
