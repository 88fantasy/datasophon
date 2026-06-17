import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceNexusVars } from './panelQueries';

function collectPromql(panelId: string): string[] {
  const panel = PANEL_QUERIES[panelId];
  if (panel.type === 'multi-range') {
    return panel.queries.map((query) => query.promql);
  }
  return [panel.promql];
}

describe('NexusMonitor panel queries', () => {
  it('defines every Nexus dashboard panel from N01 through N18', () => {
    const expectedIds = Array.from(
      { length: 18 },
      (_, index) => `N${String(index + 1).padStart(2, '0')}`,
    );

    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(expectedIds);
  });

  it('replaces Nexus variables and defaults empty values to match all', () => {
    expect(
      replaceNexusVars('jvm_vm_uptime{instance=~"$instance",job=~"$job"}', {
        instance: 'nexus-1:8081',
        job: 'nexus',
      }),
    ).toBe('jvm_vm_uptime{instance=~"nexus-1:8081",job=~"nexus"}');

    expect(
      replaceNexusVars('jvm_vm_uptime{instance=~"$instance",job=~"$job"}', {}),
    ).toBe('jvm_vm_uptime{instance=~".+",job=~".+"}');
  });

  it('uses a topk metric-name regex for component exceptions', () => {
    const promql = collectPromql('N08').join('\n');

    expect(promql).toContain('topk(10');
    expect(promql).toContain('{__name__=~".*_exceptions_total"');
    expect(promql).not.toContain('SearchComponent_read_exceptions_total');
    expect(promql).not.toContain('RepositoryComponent_read_exceptions_total');
  });

  it('uses qtp metric-name regexes for Jetty thread pool metrics', () => {
    const promql = collectPromql('N17').join('\n');

    expect(promql).toContain(
      '{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_jobs"',
    );
    expect(promql).toContain(
      '{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_size"',
    );
    expect(promql).not.toMatch(/qtp\d+_/);
  });

  it('uses direct quantile timer gauges in milliseconds for latency panels', () => {
    for (const panelId of ['N09', 'N10', 'N11']) {
      const promql = collectPromql(panelId).join('\n');

      expect(promql).toContain('quantile=');
      expect(promql).toContain('* 1000');
      expect(promql).not.toContain('histogram_quantile');
    }
  });
});
