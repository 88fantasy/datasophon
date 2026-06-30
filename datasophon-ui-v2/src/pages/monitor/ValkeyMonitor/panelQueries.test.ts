import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceValkeyVars } from './panelQueries';

describe('ValkeyMonitor panel queries', () => {
  it('defines all Valkey dashboard panels V01 through V14 plus V03_max', () => {
    const coreIds = Array.from(
      { length: 14 },
      (_, i) => `V${String(i + 1).padStart(2, '0')}`,
    );
    const allIds = [...coreIds, 'V03_max'].sort();
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(allIds);
  });

  it('replaces $instance variable correctly', () => {
    expect(
      replaceValkeyVars('redis_up{instance=~"$instance"}', {
        instance: '10.0.0.1:9121',
      }),
    ).toBe('redis_up{instance=~"10.0.0.1:9121"}');

    expect(replaceValkeyVars('redis_up{instance=~"$instance"}', {})).toBe(
      'redis_up{instance=~".+"}',
    );
  });

  it('V01 is instant query for max uptime', () => {
    expect(PANEL_QUERIES.V01).toMatchObject({
      type: 'instant',
      promql: 'max(redis_uptime_in_seconds{instance=~"$instance"})',
    });
  });

  it('V04 Cache Hit % uses rate-based ratio formula', () => {
    const v04 = PANEL_QUERIES.V04;
    expect(v04.type).toBe('instant');
    if (v04.type === 'instant') {
      expect(v04.promql).toContain('redis_keyspace_hits_total');
      expect(v04.promql).toContain('redis_keyspace_misses_total');
    }
  });

  it('V05 Commands uses range with seriesKey cmd', () => {
    expect(PANEL_QUERIES.V05).toMatchObject({
      type: 'range',
      seriesKey: 'cmd',
    });
  });

  it('V07 Avg Time by Command uses ratio PromQL with seriesKey cmd', () => {
    const v07 = PANEL_QUERIES.V07;
    expect(v07.type).toBe('range');
    if (v07.type === 'range') {
      expect(v07.promql).toContain('redis_commands_duration_seconds_total');
      expect(v07.promql).toContain('redis_commands_total');
      expect(v07.seriesKey).toBe('cmd');
    }
  });

  it('V09 Memory has Used and Max series for max=0 handling', () => {
    expect(PANEL_QUERIES.V09).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'Used' }, { label: 'Max' }],
    });
  });

  it('V13 Evicted/Expired has correct labels', () => {
    expect(PANEL_QUERIES.V13).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'Expired' }, { label: 'Evicted' }],
    });
  });

  it('V14 Rejected Connections is range (Error補強 panel)', () => {
    expect(PANEL_QUERIES.V14).toMatchObject({ type: 'range' });
    const v14 = PANEL_QUERIES.V14;
    if (v14.type === 'range') {
      expect(v14.promql).toContain('redis_rejected_connections_total');
    }
  });

  it('V03_max auxiliary instant panel exists for maxmemory=0 detection', () => {
    expect(PANEL_QUERIES.V03_max).toMatchObject({
      type: 'instant',
      promql: 'sum(redis_memory_max_bytes{instance=~"$instance"})',
    });
  });
});
