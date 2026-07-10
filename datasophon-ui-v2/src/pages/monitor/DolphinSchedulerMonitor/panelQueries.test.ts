import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES } from './panelQueries';

describe('DolphinSchedulerMonitor panel queries', () => {
  it('defines every DolphinScheduler dashboard panel from D-A01 through D-C13', () => {
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual([
      'D-A01',
      'D-A02',
      'D-A03',
      'D-A04',
      'D-A05',
      'D-A06',
      'D-B01',
      'D-B02',
      'D-B03',
      'D-B04',
      'D-B05',
      'D-B06',
      'D-B07',
      'D-B08',
      'D-B09',
      'D-B10',
      'D-B11',
      'D-B12',
      'D-B13',
      'D-C01',
      'D-C02',
      'D-C03',
      'D-C04',
      'D-C05',
      'D-C06',
      'D-C07',
      'D-C08',
      'D-C09',
      'D-C10',
      'D-C11',
      'D-C12',
      'D-C13',
    ]);
  });

  it('uses Doris descriptors without PromQL strings', () => {
    const allDescriptors = Object.values(PANEL_QUERIES);
    for (const panel of allDescriptors) {
      expect(panel).not.toHaveProperty('promql');
      if (panel.type === 'multi-range') {
        for (const query of panel.queries) {
          expect(query).not.toHaveProperty('promql');
        }
      }
    }
  });

  it('maps worker counters to sum table and expected rate windows', () => {
    expect(PANEL_QUERIES['D-A01']).toMatchObject({
      type: 'multi-range',
      queries: [{ metric: 'process_cpu_usage' }],
    });
    expect(PANEL_QUERIES['D-A02']).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'ds_worker_full_submit_queue_count_total',
          table: 'sum',
          rate: '1m',
          scale: 60,
        },
      ],
    });
    expect(PANEL_QUERIES['D-A04']).toMatchObject({
      type: 'multi-range',
      queries: [{ metric: 'ds_task_running' }],
    });
    expect(PANEL_QUERIES['D-A05']).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'total', table: 'sum', rate: '5m', scale: 300 },
        { label: 'success', filters: { status: 'success' } },
        { label: 'fail', filters: { status: 'fail' } },
      ],
    });
    expect(PANEL_QUERIES['D-A06']).toMatchObject({
      type: 'multi-range',
      queries: [
        { metric: 'ds_worker_resource_download_duration_seconds_max' },
      ],
    });
  });

  it('uses instant ratio descriptors for scheduler success rates', () => {
    expect(PANEL_QUERIES['D-B02']).toMatchObject({
      type: 'instant',
      metric: 'ds_task_instance_count_total',
      table: 'sum',
      agg: 'sum',
      filters: { state: 'success' },
      denominatorMetric: 'ds_task_instance_count_total',
      denominatorTable: 'sum',
      scale: 100,
    });
    expect(PANEL_QUERIES['D-B04']).toMatchObject({
      type: 'instant',
      metric: 'ds_workflow_instance_count_total',
      filters: { state: 'success' },
      denominatorMetric: 'ds_workflow_instance_count_total',
    });
  });

  it('uses summary sum/count field ratios for duration averages', () => {
    const b08 = PANEL_QUERIES['D-B08'];
    expect(b08.type).toBe('multi-range');
    if (b08.type === 'multi-range') {
      expect(b08.queries[0]).toMatchObject({
        label: 'avg',
        metric: 'ds_workflow_command_query_duration_seconds',
        table: 'summary',
        field: 'sum',
        denominatorMetric: 'ds_workflow_command_query_duration_seconds',
        denominatorTable: 'summary',
        denominatorField: 'count',
      });
    }

    const b10 = PANEL_QUERIES['D-B10'];
    expect(b10.type).toBe('multi-range');
    if (b10.type === 'multi-range') {
      expect(b10.queries[0]).toMatchObject({
        label: 'avg',
        metric: 'ds_workflow_instance_generate_duration_seconds',
        table: 'summary',
        field: 'sum',
        denominatorField: 'count',
        scale: 1000,
      });
    }
  });

  it('uses regex filters for HTTP status and groupBy for log and GC panels', () => {
    expect(PANEL_QUERIES['D-C05']).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'http_server_requests_seconds',
          table: 'summary',
          field: 'count',
          filtersRegex: { status: '5..' },
        },
      ],
    });

    const c06 = PANEL_QUERIES['D-C06'];
    expect(c06.type).toBe('multi-range');
    if (c06.type === 'multi-range') {
      expect(c06.queries[0]).toMatchObject({
        filtersNotRegex: { status: '5..' },
        denominatorFiltersNotRegex: { status: '5..' },
      });
      expect(c06.queries[1]).toMatchObject({
        filtersNotRegex: { status: '5..' },
      });
    }

    expect(PANEL_QUERIES['D-C12']).toMatchObject({
      type: 'multi-range',
      queries: [{ groupBy: ['level'] }],
    });
    expect(PANEL_QUERIES['D-C13']).toMatchObject({
      type: 'multi-range',
      queries: [{ groupBy: ['cause'] }],
    });
  });

  it('keeps the required multi-series operational panels', () => {
    expect(PANEL_QUERIES['D-B11']).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'submit' },
        { label: 'success' },
        { label: 'fail' },
        { label: 'timeout' },
      ],
    });

    expect(PANEL_QUERIES['D-C07']).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'used' }, { label: 'committed' }, { label: 'max' }],
    });
  });
});
