import { describe, expect, it } from 'vitest';
import {
  DORIS_SEGMENT_PANEL_IDS,
  getDorisSegmentPanelIds,
  PANEL_QUERIES,
  replaceDorisVars,
} from './panelQueries';

describe('DorisMonitor panel queries', () => {
  it('defines every Doris dashboard panel from DO-A01 through DO-C11', () => {
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual([
      'DO-A01',
      'DO-A02',
      'DO-A03',
      'DO-A04',
      'DO-A05',
      'DO-A06',
      'DO-A07',
      'DO-A08',
      'DO-A09',
      'DO-B01',
      'DO-B02',
      'DO-B03',
      'DO-B04',
      'DO-B05',
      'DO-B06',
      'DO-B07',
      'DO-B08',
      'DO-B09',
      'DO-B10',
      'DO-B11',
      'DO-B12',
      'DO-C01',
      'DO-C02',
      'DO-C03',
      'DO-C04',
      'DO-C05',
      'DO-C06',
      'DO-C07',
      'DO-C08',
      'DO-C09',
      'DO-C10',
      'DO-C11',
    ]);
  });

  it('replaces Doris cluster, FE, BE, and interval variables with safe defaults', () => {
    expect(
      replaceDorisVars(
        'rate(doris_fe_query_total{job="$cluster", group="fe", instance=~"$fe_instance"}[$interval])',
        {
          cluster: 'prod-doris',
          feInstance: '10.0.0.1:18030',
          interval: '5m',
        },
      ),
    ).toBe(
      'rate(doris_fe_query_total{job="prod-doris", group="fe", instance=~"10.0.0.1:18030"}[5m])',
    );

    expect(
      replaceDorisVars(
        'doris_be_memory_allocated_bytes{job="$cluster", instance=~"$be_instance"}',
        {},
      ),
    ).toBe('doris_be_memory_allocated_bytes{job="doris", instance=~".+"}');
  });

  it('keeps FE and BE scoped panels on their own instance variables', () => {
    const fePanels = Object.entries(PANEL_QUERIES).filter(([id]) =>
      id.startsWith('DO-B'),
    );
    const bePanels = Object.entries(PANEL_QUERIES).filter(([id]) =>
      id.startsWith('DO-C'),
    );

    for (const [, panel] of fePanels) {
      const promqlText =
        panel.type === 'multi-range'
          ? panel.queries.map((query) => query.promql).join('\n')
          : panel.promql;
      expect(promqlText).not.toContain('$be_instance');
    }

    for (const [, panel] of bePanels) {
      const promqlText =
        panel.type === 'multi-range'
          ? panel.queries.map((query) => query.promql).join('\n')
          : panel.promql;
      expect(promqlText).not.toContain('$fe_instance');
    }
  });

  it('defines the required Doris multi-series panels', () => {
    expect(PANEL_QUERIES['DO-B04']).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'p50' }, { label: 'p75' }, { label: 'p99' }],
    });

    expect(PANEL_QUERIES['DO-C05']).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'base' }, { label: 'cumulative' }],
    });

    expect(PANEL_QUERIES['DO-C11']).toMatchObject({
      type: 'multi-range',
      queries: [{ label: 'send' }, { label: 'recv' }],
    });
  });

  it('groups Doris panels by dashboard segment for tab-scoped requests', () => {
    expect(DORIS_SEGMENT_PANEL_IDS.cluster).toEqual([
      'DO-A01',
      'DO-A02',
      'DO-A03',
      'DO-A04',
      'DO-A05',
      'DO-A06',
      'DO-A07',
      'DO-A08',
      'DO-A09',
    ]);
    expect(DORIS_SEGMENT_PANEL_IDS.fe).toEqual([
      'DO-B01',
      'DO-B02',
      'DO-B03',
      'DO-B04',
      'DO-B05',
      'DO-B06',
      'DO-B07',
      'DO-B08',
      'DO-B09',
      'DO-B10',
      'DO-B11',
      'DO-B12',
    ]);
    expect(DORIS_SEGMENT_PANEL_IDS.be).toEqual([
      'DO-C01',
      'DO-C02',
      'DO-C03',
      'DO-C04',
      'DO-C05',
      'DO-C06',
      'DO-C07',
      'DO-C08',
      'DO-C09',
      'DO-C10',
      'DO-C11',
    ]);
  });

  it('returns only the active segment panel ids', () => {
    expect(getDorisSegmentPanelIds('cluster')).toEqual(
      DORIS_SEGMENT_PANEL_IDS.cluster,
    );
    expect(getDorisSegmentPanelIds('fe')).toEqual(DORIS_SEGMENT_PANEL_IDS.fe);
    expect(getDorisSegmentPanelIds('be')).toEqual(DORIS_SEGMENT_PANEL_IDS.be);
  });
});
