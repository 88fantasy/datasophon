/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { describe, expect, it } from 'vitest';
import {
  DORIS_SEGMENT_PANEL_IDS,
  getDorisSegmentPanelIds,
  PANEL_QUERIES,
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

  it('uses node-count descriptors for FE/BE node stat panels', () => {
    expect(PANEL_QUERIES['DO-A01']).toEqual({
      type: 'node-count',
      roleName: 'DorisFE',
    });
    expect(PANEL_QUERIES['DO-A02']).toEqual({
      type: 'node-count',
      roleName: 'DorisFE',
    });
    expect(PANEL_QUERIES['DO-A03']).toEqual({
      type: 'node-count',
      roleName: 'DorisBE',
    });
    expect(PANEL_QUERIES['DO-A04']).toEqual({
      type: 'node-count',
      roleName: 'DorisBE',
    });
  });

  it('uses instant descriptors for disk capacity summary panels', () => {
    const a05 = PANEL_QUERIES['DO-A05'];
    expect(a05.type).toBe('instant');
    if (a05.type === 'instant') {
      expect(a05.metric).toBe('doris_be_disks_local_used_capacity');
      expect(a05.agg).toBe('sum');
      expect(a05.filters?.group).toBe('be');
    }
  });

  it('uses multi-range descriptors for time-series panels', () => {
    const a07 = PANEL_QUERIES['DO-A07'];
    expect(a07.type).toBe('multi-range');
    if (a07.type === 'multi-range') {
      expect(a07.queries[0].metric).toBe('doris_fe_query_total');
      expect(a07.queries[0].rate).toBe('2m');
      expect(a07.queries[0].table).toBe('sum');
    }
  });

  it('uses denominatorMetric for ratio panels (heap%, error rate, disk %)', () => {
    const a08 = PANEL_QUERIES['DO-A08'];
    expect(a08.type).toBe('multi-range');
    if (a08.type === 'multi-range') {
      expect(a08.queries[0].denominatorMetric).toBe('jvm_heap_size_bytes');
      expect(a08.queries[0].scale).toBe(100);
      expect(a08.queries[0].filters?.type).toBe('used');
      expect(a08.queries[0].denominatorFilters?.type).toBe('max');
    }

    const b06 = PANEL_QUERIES['DO-B06'];
    expect(b06.type).toBe('multi-range');
    if (b06.type === 'multi-range') {
      expect(b06.queries[0].denominatorMetric).toBe('doris_fe_query_total');
      expect(b06.queries[0].scale).toBe(100);
    }

    const c03 = PANEL_QUERIES['DO-C03'];
    expect(c03.type).toBe('multi-range');
    if (c03.type === 'multi-range') {
      expect(c03.queries[0].label).toBe('local_used_pct');
      expect(c03.queries[0].denominatorMetric).toBe(
        'doris_be_disks_total_capacity',
      );
      expect(c03.queries[0].groupBy).toEqual(['path']);
      expect(c03.queries[1].label).toBe('avail_pct');
    }
  });

  it('uses summary table for latency quantile panels', () => {
    const b04 = PANEL_QUERIES['DO-B04'];
    expect(b04.type).toBe('multi-range');
    if (b04.type === 'multi-range') {
      expect(b04.queries).toHaveLength(3);
      expect(b04.queries[0]).toMatchObject({
        label: 'p50',
        table: 'summary',
        quantile: 0.5,
      });
      expect(b04.queries[2]).toMatchObject({
        label: 'p99',
        table: 'summary',
        quantile: 0.99,
      });
    }

    const b12 = PANEL_QUERIES['DO-B12'];
    expect(b12.type).toBe('multi-range');
    if (b12.type === 'multi-range') {
      expect(b12.queries[0].table).toBe('summary');
      expect(b12.queries[0].quantile).toBe(0.99);
    }
  });

  it('uses sum table and rate for counter metrics', () => {
    for (const id of ['DO-C06', 'DO-C07', 'DO-C08', 'DO-C09']) {
      const def = PANEL_QUERIES[id];
      expect(def.type).toBe('multi-range');
      if (def.type === 'multi-range') {
        expect(def.queries[0].table).toBe('sum');
        expect(def.queries[0].rate).toBe('2m');
      }
    }
  });

  it('uses filtersNe to exclude loopback for network panels', () => {
    const c11 = PANEL_QUERIES['DO-C11'];
    expect(c11.type).toBe('multi-range');
    if (c11.type === 'multi-range') {
      expect(c11.queries[0].label).toBe('send');
      expect(c11.queries[0].filtersNe?.device).toBe('lo');
      expect(c11.queries[1].label).toBe('recv');
      expect(c11.queries[1].filtersNe?.device).toBe('lo');
    }
  });

  it('scopes FE panels to group=fe, BE panels to group=be', () => {
    for (const id of DORIS_SEGMENT_PANEL_IDS.fe) {
      const def = PANEL_QUERIES[id];
      if (def.type === 'multi-range') {
        for (const q of def.queries) {
          if (q.filters?.group !== undefined) {
            expect(q.filters.group).toBe('fe');
          }
        }
      }
    }

    for (const id of DORIS_SEGMENT_PANEL_IDS.be) {
      const def = PANEL_QUERIES[id];
      if (def.type === 'multi-range') {
        for (const q of def.queries) {
          if (q.filters?.group !== undefined) {
            expect(q.filters.group).toBe('be');
          }
        }
      }
    }
  });

  it('defines the required multi-series panels with correct labels', () => {
    const b04 = PANEL_QUERIES['DO-B04'];
    expect(b04.type).toBe('multi-range');
    if (b04.type === 'multi-range') {
      expect(b04.queries.map((q) => q.label)).toEqual(['p50', 'p75', 'p99']);
    }

    const c05 = PANEL_QUERIES['DO-C05'];
    expect(c05.type).toBe('multi-range');
    if (c05.type === 'multi-range') {
      expect(c05.queries.map((q) => q.label)).toEqual(['base', 'cumulative']);
    }

    const c11 = PANEL_QUERIES['DO-C11'];
    expect(c11.type).toBe('multi-range');
    if (c11.type === 'multi-range') {
      expect(c11.queries.map((q) => q.label)).toEqual(['send', 'recv']);
    }
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
