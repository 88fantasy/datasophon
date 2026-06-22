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
import { PANEL_QUERIES } from './panelQueries';

/** T1+T2 面板（已实现），T3 延后 */
const IMPLEMENTED_PANEL_IDS = [
  'N01', 'N02', 'N03', 'N04', 'N05', 'N06', // T1 instant
  'N07', // T2 counter rate
  'N12', 'N13', 'N14', // T1/T2 multi-range
  'N16', 'N18',        // T1 multi-range
];

/** T3 面板（延后，不应出现在 PANEL_QUERIES 中） */
const DEFERRED_PANEL_IDS = ['N08', 'N09', 'N10', 'N11', 'N15', 'N17'];

describe('NexusMonitor panel queries (Doris 描述符)', () => {
  it('T1+T2 面板均已定义', () => {
    for (const id of IMPLEMENTED_PANEL_IDS) {
      expect(PANEL_QUERIES[id], `${id} should be defined`).toBeDefined();
    }
  });

  it('T3 面板未定义（延后），不出现在 PANEL_QUERIES 中', () => {
    for (const id of DEFERRED_PANEL_IDS) {
      expect(PANEL_QUERIES[id], `${id} should be deferred`).toBeUndefined();
    }
  });

  it('N01-N06 为 instant 类型描述符', () => {
    for (const id of ['N01', 'N02', 'N03', 'N04', 'N05', 'N06']) {
      expect(PANEL_QUERIES[id].type).toBe('instant');
    }
  });

  it('N02 和 N03 的 scale 为 100（ratio → 百分比）', () => {
    const n02 = PANEL_QUERIES['N02'];
    const n03 = PANEL_QUERIES['N03'];
    expect(n02.type).toBe('instant');
    if (n02.type === 'instant') expect(n02.scale).toBe(100);
    if (n03.type === 'instant') expect(n03.scale).toBe(100);
  });

  it('N07 HTTP 响应码面板包含 1xx-5xx 五条 series，均设 rate: 1m', () => {
    const n07 = PANEL_QUERIES['N07'];
    expect(n07.type).toBe('multi-range');
    if (n07.type !== 'multi-range') return;
    expect(n07.queries).toHaveLength(5);
    for (const q of n07.queries) {
      expect(q.rate).toBe('1m');
      expect(q.metric).toContain('responses_total');
    }
  });

  it('N14 GC 面板包含 MarkSweep 和 Scavenge，均设 rate: 1m', () => {
    const n14 = PANEL_QUERIES['N14'];
    expect(n14.type).toBe('multi-range');
    if (n14.type !== 'multi-range') return;
    expect(n14.queries).toHaveLength(2);
    for (const q of n14.queries) {
      expect(q.rate).toBe('1m');
    }
    const labels = n14.queries.map((q) => q.label);
    expect(labels).toContain('MarkSweep');
    expect(labels).toContain('Scavenge');
  });

  it('N12 内存堆面板包含 Max/Used/Committed，均无 rate', () => {
    const n12 = PANEL_QUERIES['N12'];
    expect(n12.type).toBe('multi-range');
    if (n12.type !== 'multi-range') return;
    const labels = n12.queries.map((q) => q.label);
    expect(labels).toContain('Max');
    expect(labels).toContain('Used');
    expect(labels).toContain('Committed');
    for (const q of n12.queries) {
      expect(q.rate).toBeUndefined();
    }
  });
});
