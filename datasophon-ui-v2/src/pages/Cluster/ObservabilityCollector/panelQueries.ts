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
    queries: SIGNALS.map(({ label, suffix }) => ({
      label,
      metric: `otelcol_exporter_sent_${suffix}`,
      rate: '1m' as const,
      table: 'sum' as const,
      groupBy: ['exporter'],
    })),
  },
  failedRate: {
    type: 'multi-range',
    queries: SIGNALS.map(({ label, suffix }) => ({
      label: `${label} receiver failed`,
      metric: `otelcol_receiver_failed_${suffix}`,
      rate: '1m' as const,
      table: 'sum' as const,
      groupBy: ['receiver', 'transport'],
    })),
  },
  refusedDroppedRate: {
    type: 'multi-range',
    queries: [
      ...SIGNALS.map(({ label, suffix }) => ({
        label: `${label} refused`,
        metric: `otelcol_receiver_refused_${suffix}`,
        rate: '1m' as const,
        table: 'sum' as const,
        groupBy: ['receiver', 'transport'],
      })),
      ...SIGNALS.map(({ label, suffix }) => ({
        label: `${label} dropped`,
        metric: `otelcol_processor_dropped_${suffix}`,
        rate: '1m' as const,
        table: 'sum' as const,
        groupBy: ['processor'],
      })),
      {
        label: 'Filtered datapoints',
        metric: 'otelcol_processor_filter_datapoints.filtered',
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
