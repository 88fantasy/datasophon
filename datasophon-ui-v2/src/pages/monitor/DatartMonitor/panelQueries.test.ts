import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceDatartVars } from './panelQueries';

function allPromql(panelId: string): string {
  const panel = PANEL_QUERIES[panelId];

  if (panel.type === 'multi-range') {
    return panel.queries.map((query) => query.promql).join('\n');
  }

  return panel.promql;
}

describe('DatartMonitor panel queries', () => {
  it('defines every Datart dashboard panel from D01 through D18', () => {
    const expectedIds = Array.from(
      { length: 18 },
      (_, index) => `D${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces Datart variables and defaults instance to a match-all regex', () => {
    expect(
      replaceDatartVars(
        'metric{application="$application",instance=~"$instance",id="$memory_pool_heap",pool="$hikaricp"}',
        {
          application: 'datart',
          instance: 'datart-1:8080|datart-2:8080',
          memory_pool_heap: 'G1 Old Gen',
          hikaricp: 'HikariPool-1',
        },
      ),
    ).toBe(
      'metric{application="datart",instance=~"datart-1:8080|datart-2:8080",id="G1 Old Gen",pool="HikariPool-1"}',
    );

    expect(
      replaceDatartVars(
        'process_uptime_seconds{application="$application",instance=~"$instance"}',
        {
          application: 'datart',
          memory_pool_heap: 'G1 Old Gen',
          hikaricp: 'HikariPool-1',
        },
      ),
    ).toBe('process_uptime_seconds{application="datart",instance=~".+"}');
  });

  it('uses sum/count ratios for D08 and D15 latency panels without histogram_quantile', () => {
    for (const panelId of ['D08', 'D15']) {
      const promql = allPromql(panelId);

      expect(promql).toContain('_sum');
      expect(promql).toContain('_count');
      expect(promql).toContain('/');
      expect(promql).not.toContain('histogram_quantile');
    }
  });

  it('keeps D18 as one by-level logback query', () => {
    expect(PANEL_QUERIES.D18).toMatchObject({
      type: 'range',
      seriesKey: 'level',
    });
    expect(allPromql('D18')).toBe(
      'sum(irate(logback_events_total{instance=~"$instance", application="$application"}[5m])) by (level)',
    );
  });

  it('keeps the D10 heap pool id variable in every memory pool query', () => {
    const panel = PANEL_QUERIES.D10;

    expect(panel.type).toBe('multi-range');
    if (panel.type !== 'multi-range') return;

    expect(panel.queries).toHaveLength(3);
    for (const query of panel.queries) {
      expect(query.promql).toContain('id="$memory_pool_heap"');
    }
  });
});
