import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, VALKEY_JOB_FILTER } from './panelQueries';

describe('ValkeyMonitor Doris panel descriptors', () => {
  it('defines all Valkey dashboard panels V01 through V14 plus V03_max', () => {
    const coreIds = Array.from(
      { length: 14 },
      (_, i) => `V${String(i + 1).padStart(2, '0')}`,
    );
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(
      [...coreIds, 'V03_max'].sort(),
    );
    expect(VALKEY_JOB_FILTER).toBe('^ValkeyExporter$');
  });

  it('uses gauge instant descriptors for overview values', () => {
    expect(PANEL_QUERIES.V01).toEqual({
      type: 'instant',
      metric: 'redis_uptime_in_seconds',
      agg: 'max',
    });
    expect(PANEL_QUERIES.V02).toEqual({
      type: 'instant',
      metric: 'redis_connected_clients',
      agg: 'sum',
    });
    expect(PANEL_QUERIES.V03).toMatchObject({
      type: 'instant',
      metric: 'redis_memory_used_bytes',
      denominatorMetric: 'redis_memory_max_bytes',
      scale: 100,
    });
  });

  it('derives cache hit percentage from Doris hits and misses range rates', () => {
    expect(PANEL_QUERIES.V04).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          label: 'Hits',
          metric: 'redis_keyspace_hits_total',
          rate: '5m',
          table: 'sum',
        },
        {
          label: 'Misses',
          metric: 'redis_keyspace_misses_total',
          rate: '5m',
          table: 'sum',
        },
      ],
    });
  });

  it('uses sum counters and cmd grouping for command rate and latency', () => {
    expect(PANEL_QUERIES.V05).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'redis_commands_total',
          rate: '1m',
          table: 'sum',
          groupBy: ['cmd'],
        },
      ],
    });
    expect(PANEL_QUERIES.V07).toMatchObject({
      type: 'multi-range',
      queries: [
        {
          metric: 'redis_commands_duration_seconds_total',
          denominatorMetric: 'redis_commands_total',
          rate: '1m',
          table: 'sum',
          denominatorTable: 'sum',
          groupBy: ['cmd'],
        },
      ],
    });
  });

  it('keeps V12 raw key matrices grouped by db for client-side pairing', () => {
    expect(PANEL_QUERIES.V12).toEqual({
      type: 'multi-range',
      queries: [
        { label: 'Total', metric: 'redis_db_keys', groupBy: ['db'] },
        {
          label: 'Expiring',
          metric: 'redis_db_keys_expiring',
          groupBy: ['db'],
        },
      ],
    });
  });

  it('queries all remaining counter panels from the sum table', () => {
    for (const panelId of ['V06', 'V08', 'V13', 'V14']) {
      const descriptor = PANEL_QUERIES[panelId];
      expect(descriptor.type).toBe('multi-range');
      if (descriptor.type === 'multi-range') {
        expect(descriptor.queries.every((query) => query.table === 'sum')).toBe(
          true,
        );
      }
    }
  });
});
