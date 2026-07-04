import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES } from './panelQueries';

function allMetrics(panelId: string): string[] {
  const def = PANEL_QUERIES[panelId];
  if (def.type === 'multi-range') {
    return def.queries.map((query) => query.metric);
  }
  if (def.type === 'instant') {
    return [def.metric];
  }
  return [];
}

describe('JuiceFSMonitor panel queries (Doris descriptors)', () => {
  it('defines every JuiceFS dashboard panel from J01 through J17', () => {
    const expectedIds = Array.from(
      { length: 17 },
      (_, index) => `J${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('uses only juicefs metrics', () => {
    for (const panelId of Object.keys(PANEL_QUERIES)) {
      for (const metric of allMetrics(panelId)) {
        expect(metric).toMatch(/^juicefs_/);
      }
    }
  });

  it('maps stat panels to instant Doris descriptors', () => {
    expect(PANEL_QUERIES.J01).toMatchObject({
      type: 'instant',
      metric: 'juicefs_uptime',
      agg: 'max',
    });
    expect(PANEL_QUERIES.J02).toMatchObject({
      type: 'instant',
      metric: 'juicefs_used_space',
      agg: 'max',
    });
    expect(PANEL_QUERIES.J04).toMatchObject({
      type: 'instant',
      metric: 'juicefs_uptime',
      agg: 'count',
    });
  });

  it('uses histogram field rates for operation and throughput panels', () => {
    expect(PANEL_QUERIES.J07).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'juicefs_fuse_ops_durations_histogram_seconds',
          table: 'histogram',
          field: 'count',
        },
      ],
    });
    expect(PANEL_QUERIES.J08).toMatchObject({
      type: 'multi-range',
      queries: [
        { table: 'histogram', field: 'sum' },
        { table: 'histogram', field: 'sum' },
      ],
    });
  });

  it('uses p50 and p99 histogram quantiles for latency panels', () => {
    for (const panelId of ['J09', 'J10', 'J11']) {
      expect(PANEL_QUERIES[panelId]).toMatchObject({
        type: 'multi-range',
        queries: [
          { table: 'histogram', quantile: 0.5, scale: 1000000 },
          { table: 'histogram', quantile: 0.99, scale: 1000000 },
        ],
      });
    }
  });

  it('groups object request rates by method', () => {
    expect(PANEL_QUERIES.J12).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'juicefs_object_request_durations_histogram_seconds',
          table: 'histogram',
          field: 'count',
          groupBy: ['method'],
        },
      ],
    });
  });

  it('keeps J13 as object errors plus transaction restarts from gauge table', () => {
    // JuiceFS 的非 _total 后缀计数器（即使 Prometheus TYPE 为 counter）落在
    // otel_metrics_gauge 表，而非 otel_metrics_sum；已用真实沙箱数据核实
    // （见 docs/monitoring/juicefs-otel-verification.md）。
    expect(PANEL_QUERIES.J13).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          label: 'Object Request Errors',
          metric: 'juicefs_object_request_errors',
          table: 'gauge',
        },
        {
          label: 'Transaction Restarts',
          metric: 'juicefs_transaction_restart',
          table: 'gauge',
        },
      ],
    });
  });

  it('filters object throughput by method', () => {
    expect(PANEL_QUERIES.J16).toMatchObject({
      type: 'multi-range',
      queries: [
        { filters: { method: 'PUT' } },
        { filters: { method: 'GET' } },
      ],
    });
  });
});
