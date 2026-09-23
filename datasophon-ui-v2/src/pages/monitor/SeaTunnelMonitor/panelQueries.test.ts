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
  PANEL_QUERIES,
  SEGMENT_PANEL_IDS,
  type SeaTunnelDashboardSegment,
} from './panelQueries';

const COMMON_PANEL_IDS = [
  'ST-C01',
  'ST-C02',
  'ST-C03',
  'ST-C04',
  'ST-C05',
  'ST-C06',
  'ST-C07',
  'ST-C08',
  'ST-C09',
  'ST-C10',
];

describe('SeaTunnel panel descriptors', () => {
  it('defines exactly the 18 specified panel IDs', () => {
    expect(Object.keys(PANEL_QUERIES).sort()).toEqual(
      [
        'ST-M01',
        'ST-M02',
        'ST-M03',
        'ST-M04',
        'ST-M05',
        'ST-W01',
        'ST-W02',
        'ST-W03',
        ...COMMON_PANEL_IDS,
      ].sort(),
    );
  });

  it.each([
    [
      'master',
      ['ST-M01', 'ST-M02', 'ST-M03', 'ST-M04', 'ST-M05', ...COMMON_PANEL_IDS],
    ],
    ['worker', ['ST-W01', 'ST-W02', 'ST-W03', ...COMMON_PANEL_IDS]],
  ] as Array<
    [SeaTunnelDashboardSegment, string[]]
  >)('%s segment contains only its specified panels', (segment, expectedIds) => {
    expect(SEGMENT_PANEL_IDS[segment]).toEqual(expectedIds);
  });

  it('keeps every sum-table metric name suffixed with _total', () => {
    const sumMetrics = Object.values(PANEL_QUERIES).flatMap((descriptor) =>
      descriptor.type === 'multi-range'
        ? descriptor.queries
            .filter((query) => query.table === 'sum')
            .map((query) => query.metric)
        : [],
    );

    expect(sumMetrics.length).toBeGreaterThan(0);
    expect(sumMetrics.every((metric) => metric.endsWith('_total'))).toBe(true);
  });
});
