import { describe, expect, it } from 'vitest';
import { PANEL_QUERIES, replaceDSVars } from './panelQueries';

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

  it('keeps worker and master segment queries independent from the toolbar application variable', () => {
    const workerQueries = Object.entries(PANEL_QUERIES).filter(([id]) =>
      id.startsWith('D-A'),
    );
    const masterQueries = Object.entries(PANEL_QUERIES).filter(([id]) =>
      id.startsWith('D-B'),
    );

    for (const [, panel] of [...workerQueries, ...masterQueries]) {
      const promqlText =
        panel.type === 'multi-range'
          ? panel.queries.map((query) => query.promql).join('\n')
          : panel.promql;
      expect(promqlText).not.toContain('$application');
    }

    expect(PANEL_QUERIES['D-A01']).toMatchObject({
      type: 'range',
      promql: 'process_cpu_usage{application="worker-server"}',
    });
  });

  it('replaces only generic Spring Boot segment variables', () => {
    expect(
      replaceDSVars(
        'process_uptime_seconds{application="$application", instance=~"$instance"}',
        { application: 'api-server', instance: 'api-1:12345' },
      ),
    ).toBe(
      'process_uptime_seconds{application="api-server", instance=~"api-1:12345"}',
    );

    expect(
      replaceDSVars(
        'jvm_threads_live_threads{application="$application", instance=~"$instance"}',
        {},
      ),
    ).toBe(
      'jvm_threads_live_threads{application="master-server", instance=~".+"}',
    );
  });

  it('uses regex instance matchers for every generic Spring Boot segment query', () => {
    const genericQueries = Object.entries(PANEL_QUERIES).filter(([id]) =>
      id.startsWith('D-C'),
    );

    for (const [, panel] of genericQueries) {
      const promqlText =
        panel.type === 'multi-range'
          ? panel.queries.map((query) => query.promql).join('\n')
          : panel.promql;
      expect(promqlText).not.toContain('instance="$instance"');
      expect(promqlText).toContain('instance=~"$instance"');
    }
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
