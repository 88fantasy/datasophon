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
  DISAGG_PANEL_QUERIES,
  DISAGG_PANEL_ROLE,
  DISAGG_SEGMENT_PANEL_IDS,
  getDisaggSegmentPanelIds,
} from './panelQueriesDisaggregated';

describe('DorisMonitor disaggregated panel queries', () => {
  it('never filters on the group label (K8s 侧不存在 group 标签，Phase 0 决定性证据)', () => {
    for (const [id, def] of Object.entries(DISAGG_PANEL_QUERIES)) {
      if (def.type !== 'multi-range') continue;
      for (const q of def.queries) {
        expect(q.filters?.group, `${id} query ${q.label}`).toBeUndefined();
        expect(
          q.denominatorFilters?.group,
          `${id} query ${q.label} denominator`,
        ).toBeUndefined();
      }
    }
  });

  it('dedupes the FE query_total/query_err user+cluster_name dimensions (P1 correctness fix)', () => {
    for (const id of ['DO-A07', 'DO-B02']) {
      const def = DISAGG_PANEL_QUERIES[id];
      expect(def.type).toBe('multi-range');
      if (def.type === 'multi-range') {
        expect(def.queries[0].filters?.user).toBe('');
        expect(def.queries[0].filters?.cluster_name).toBe('');
      }
    }

    const b06 = DISAGG_PANEL_QUERIES['DO-B06'];
    expect(b06.type).toBe('multi-range');
    if (b06.type === 'multi-range') {
      expect(b06.queries[0].filters?.user).toBe('');
      expect(b06.queries[0].filters?.cluster_name).toBe('');
      expect(b06.queries[0].denominatorFilters?.user).toBe('');
      expect(b06.queries[0].denominatorFilters?.cluster_name).toBe('');
    }
  });

  it('uses jvm_gc (name+type) instead of jvm_old_gc for the FE GC panel', () => {
    const b11 = DISAGG_PANEL_QUERIES['DO-B11'];
    expect(b11.type).toBe('multi-range');
    if (b11.type === 'multi-range') {
      expect(b11.queries[0].metric).toBe('jvm_gc');
      expect(b11.queries[0].filters?.name).toBe('G1 Old Generation Count');
      expect(b11.queries[1].filters?.name).toBe('G1 Old Generation Time');
    }
  });

  it('defines every compute-group panel from DO-D01 through DO-D12', () => {
    expect(DISAGG_SEGMENT_PANEL_IDS.compute).toHaveLength(12);
    for (const id of DISAGG_SEGMENT_PANEL_IDS.compute) {
      expect(DISAGG_PANEL_QUERIES[id]).toBeDefined();
    }
  });

  it('does not define local-disk descriptors that do not exist under disaggregated storage', () => {
    for (const id of Object.keys(DISAGG_PANEL_QUERIES)) {
      const def = DISAGG_PANEL_QUERIES[id];
      if (def.type !== 'multi-range') continue;
      for (const q of def.queries) {
        expect(q.metric).not.toMatch(/^doris_be_disks_/);
      }
    }
  });

  it('assigns every panel id a fe/compute role for job-scoped queries', () => {
    for (const id of Object.keys(DISAGG_PANEL_QUERIES)) {
      expect(DISAGG_PANEL_ROLE[id]).toMatch(/^(fe|compute)$/);
    }
    expect(DISAGG_PANEL_ROLE['DO-A09']).toBe('compute');
    expect(DISAGG_PANEL_ROLE['DO-A07']).toBe('fe');
  });

  it('returns only the active segment panel ids', () => {
    expect(getDisaggSegmentPanelIds('cluster')).toEqual(
      DISAGG_SEGMENT_PANEL_IDS.cluster,
    );
    expect(getDisaggSegmentPanelIds('fe')).toEqual(
      DISAGG_SEGMENT_PANEL_IDS.fe,
    );
    expect(getDisaggSegmentPanelIds('compute')).toEqual(
      DISAGG_SEGMENT_PANEL_IDS.compute,
    );
  });
});
