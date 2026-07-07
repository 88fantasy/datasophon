import { describe, expect, it } from 'vitest';

import { COLLECTOR_PANEL_QUERIES } from './panelQueries';

describe('Collector panel descriptors', () => {
  it('queries queue utilization as queue size divided by capacity per exporter', () => {
    expect(COLLECTOR_PANEL_QUERIES.queueUsage).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'otelcol_exporter_queue_size',
          denominatorMetric: 'otelcol_exporter_queue_capacity',
          scale: 100,
          table: 'gauge',
          groupBy: ['exporter'],
        },
      ],
    });
  });

  it('uses Doris sum table and one minute rate for collector counters', () => {
    expect(COLLECTOR_PANEL_QUERIES.sentRate).toMatchObject({
      type: 'multi-range',
      queries: expect.arrayContaining([
        expect.objectContaining({
          metric: 'otelcol_exporter_sent_metric_points',
          rate: '1m',
          table: 'sum',
          groupBy: ['exporter'],
        }),
      ]),
    });
    expect(COLLECTOR_PANEL_QUERIES.failedRate).toMatchObject({
      type: 'multi-range',
      queries: expect.arrayContaining([
        expect.objectContaining({
          metric: 'otelcol_receiver_failed_metric_points',
          rate: '1m',
          table: 'sum',
          groupBy: ['receiver', 'transport'],
        }),
      ]),
    });
  });

  it('groups receiver refused and processor dropped counters by collector pipeline dimensions', () => {
    expect(COLLECTOR_PANEL_QUERIES.refusedDroppedRate).toMatchObject({
      type: 'multi-range',
      queries: expect.arrayContaining([
        expect.objectContaining({
          metric: 'otelcol_receiver_refused_metric_points',
          groupBy: ['receiver', 'transport'],
        }),
        expect.objectContaining({
          metric: 'otelcol_processor_filter_datapoints_filtered',
          groupBy: ['processor'],
        }),
      ]),
    });
  });

  it('reads collector uptime from the Doris sum table', () => {
    expect(COLLECTOR_PANEL_QUERIES.uptime).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'otelcol_process_uptime',
          table: 'sum',
        },
      ],
    });
  });
});
