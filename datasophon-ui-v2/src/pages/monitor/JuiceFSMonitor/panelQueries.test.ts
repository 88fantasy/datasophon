import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceJuiceFSVars } from './panelQueries';

function allPromql(panelId: string): string {
  const def = PANEL_QUERIES[panelId];
  if (def.type === 'multi-range') {
    return def.queries.map((query) => query.promql).join('\n');
  }
  return def.promql;
}

describe('JuiceFSMonitor panel queries', () => {
  it('defines every JuiceFS dashboard panel from J01 through J17', () => {
    const expectedIds = Array.from(
      { length: 17 },
      (_, index) => `J${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces JuiceFS variables and defaults the volume name to .+', () => {
    expect(
      replaceJuiceFSVars(
        'rate(juicefs_uptime{vol_name="$name"}[$__rate_interval])',
        { name: 'prod-fs' },
        '2m',
      ),
    ).toBe('rate(juicefs_uptime{vol_name="prod-fs"}[2m])');

    expect(
      replaceJuiceFSVars(
        'juicefs_uptime{vol_name="$name"}[$__rate_interval]',
        {},
        '1m',
      ),
    ).toBe('juicefs_uptime{vol_name=".+"}[1m]');
  });

  it('uses histogram average ratios for latency panels without histogram_quantile', () => {
    for (const panelId of ['J09', 'J10', 'J11']) {
      const promql = allPromql(panelId);

      expect(promql).toContain('_sum');
      expect(promql).toContain('_count');
      expect(promql).toContain('rate(');
      expect(promql).toContain('* 1000000 /');
      expect(promql).not.toContain('histogram_quantile');
    }
  });

  it('keeps J13 as an independent object errors and transaction restarts panel', () => {
    expect(PANEL_QUERIES.J13).toMatchObject({
      type: 'multi-range',
      queries: [
        { label: 'Object Request Errors' },
        { label: 'Transaction Restarts' },
      ],
    });

    const promql = allPromql('J13');
    expect(promql).toContain('juicefs_object_request_errors');
    expect(promql).toContain('juicefs_transaction_restart');
    expect(promql).not.toContain(
      'juicefs_object_request_durations_histogram_seconds_count',
    );
  });

  it('retains spike filtering on rate and throughput queries where specified', () => {
    for (const panelId of ['J07', 'J08']) {
      expect(allPromql(panelId)).toContain('< 5000000000');
    }
    expect(allPromql('J17')).toContain('< 1000');
  });
});
