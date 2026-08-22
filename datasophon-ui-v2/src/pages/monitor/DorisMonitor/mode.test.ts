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
import { DORIS_SEGMENT_PANEL_IDS, PANEL_QUERIES } from './panelQueries';
import {
  DISAGG_PANEL_QUERIES,
  DISAGG_SEGMENT_PANEL_IDS,
} from './panelQueriesDisaggregated';
import {
  getDorisPanelSet,
  parseDorisMonitorProfile,
  resolveDorisMode,
  splitPanelIdsByRole,
} from './mode';

describe('resolveDorisMode', () => {
  it('returns coupled when monitorProfile is absent (MANAGED / 未接管场景)', () => {
    expect(resolveDorisMode(undefined)).toBe('coupled');
    expect(resolveDorisMode('')).toBe('coupled');
  });

  it('returns coupled when monitorProfile does not carry the disaggregated marker', () => {
    expect(resolveDorisMode(JSON.stringify({ profile: 'something-else' }))).toBe(
      'coupled',
    );
    expect(resolveDorisMode('not-json')).toBe('coupled');
  });

  it('returns disaggregated only when profile === doris-disaggregated', () => {
    expect(
      resolveDorisMode(
        JSON.stringify({
          profile: 'doris-disaggregated',
          roles: { fe: ['a-fe'], compute: ['a-cg1', 'a-cg2'] },
        }),
      ),
    ).toBe('disaggregated');
  });
});

describe('parseDorisMonitorProfile', () => {
  it('parses roles out of the registered monitor_profile JSON', () => {
    const parsed = parseDorisMonitorProfile(
      JSON.stringify({
        profile: 'doris-disaggregated',
        roles: { fe: ['doris-fe'], compute: ['doris-cg1', 'doris-cg2'] },
      }),
    );
    expect(parsed?.roles?.fe).toEqual(['doris-fe']);
    expect(parsed?.roles?.compute).toEqual(['doris-cg1', 'doris-cg2']);
  });

  it('returns undefined for empty or malformed input without throwing', () => {
    expect(parseDorisMonitorProfile(undefined)).toBeUndefined();
    expect(parseDorisMonitorProfile('')).toBeUndefined();
    expect(parseDorisMonitorProfile('{not json')).toBeUndefined();
  });
});

describe('getDorisPanelSet', () => {
  it('returns the coupled (MANAGED) panel set unchanged', () => {
    const set = getDorisPanelSet('coupled');
    expect(set.queries).toBe(PANEL_QUERIES);
    expect(set.segmentPanelIds).toBe(DORIS_SEGMENT_PANEL_IDS);
  });

  it('returns the disaggregated panel set', () => {
    const set = getDorisPanelSet('disaggregated');
    expect(set.queries).toBe(DISAGG_PANEL_QUERIES);
    expect(set.segmentPanelIds).toBe(DISAGG_SEGMENT_PANEL_IDS);
  });
});

describe('splitPanelIdsByRole', () => {
  it('splits cluster-segment ids into fe and compute groups', () => {
    const { feIds, computeIds } = splitPanelIdsByRole(
      DISAGG_SEGMENT_PANEL_IDS.cluster,
    );
    expect(feIds).toEqual(['DO-A07', 'DO-A08']);
    expect(computeIds).toEqual(['DO-A09']);
  });

  it('puts every fe-segment id into feIds and every compute-segment id into computeIds', () => {
    expect(splitPanelIdsByRole(DISAGG_SEGMENT_PANEL_IDS.fe)).toEqual({
      feIds: DISAGG_SEGMENT_PANEL_IDS.fe,
      computeIds: [],
    });
    expect(splitPanelIdsByRole(DISAGG_SEGMENT_PANEL_IDS.compute)).toEqual({
      feIds: [],
      computeIds: DISAGG_SEGMENT_PANEL_IDS.compute,
    });
  });

  it('returns empty groups for an empty input', () => {
    expect(splitPanelIdsByRole([])).toEqual({ feIds: [], computeIds: [] });
  });
});
