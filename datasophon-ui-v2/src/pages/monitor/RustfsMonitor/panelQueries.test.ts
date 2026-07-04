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

const ALL_PANEL_IDS = Array.from(
  { length: 18 },
  (_, i) => `R${String(i + 1).padStart(2, '0')}`,
);

describe('RustfsMonitor panel queries (Doris 描述符)', () => {
  it('R01-R18 全部已定义', () => {
    for (const id of ALL_PANEL_IDS) {
      expect(PANEL_QUERIES[id], `${id} should be defined`).toBeDefined();
    }
  });

  it('R01-R05 为 instant 类型描述符', () => {
    for (const id of ['R01', 'R02', 'R03', 'R04', 'R05']) {
      expect(PANEL_QUERIES[id].type).toBe('instant');
    }
  });

  it('R06 S3 Operations 按 op 分组，rate 1m，table sum', () => {
    const r06 = PANEL_QUERIES['R06'];
    expect(r06.type).toBe('multi-range');
    if (r06.type !== 'multi-range') return;
    expect(r06.queries).toHaveLength(1);
    expect(r06.queries[0].groupBy).toEqual(['op']);
    expect(r06.queries[0].rate).toBe('1m');
    expect(r06.queries[0].table).toBe('sum');
  });

  it('R09 HTTP Request Duration 用 histogram 表 + p50/p99 quantile，不用 rate', () => {
    const r09 = PANEL_QUERIES['R09'];
    expect(r09.type).toBe('multi-range');
    if (r09.type !== 'multi-range') return;
    expect(r09.queries).toHaveLength(2);
    for (const q of r09.queries) {
      expect(q.table).toBe('histogram');
      expect(q.rate).toBeUndefined();
    }
    expect(r09.queries.map((q) => q.quantile)).toEqual([0.5, 0.99]);
  });

  it('R12 Capacity Used % 用 denominatorMetric 客户端比值合成，scale 100', () => {
    const r12 = PANEL_QUERIES['R12'];
    expect(r12.type).toBe('multi-range');
    if (r12.type !== 'multi-range') return;
    const q = r12.queries[0];
    expect(q.metric).toBe('rustfs_cluster_capacity_used_bytes');
    expect(q.denominatorMetric).toBe('rustfs_cluster_capacity_usable_total_bytes');
    expect(q.scale).toBe(100);
  });

  it('R15/R16 磁盘面板按 drive 分组（非 bucket）', () => {
    for (const id of ['R15', 'R16']) {
      const panel = PANEL_QUERIES[id];
      expect(panel.type).toBe('multi-range');
      if (panel.type !== 'multi-range') continue;
      for (const q of panel.queries) {
        expect(q.groupBy).toEqual(['drive']);
      }
    }
  });

  it('所有面板指标名均为 rustfs_* 原生命名空间，不含 minio_*', () => {
    for (const id of ALL_PANEL_IDS) {
      const panel = PANEL_QUERIES[id];
      const metrics =
        panel.type === 'instant'
          ? [panel.metric]
          : panel.type === 'multi-range'
            ? panel.queries.flatMap((q) =>
                q.denominatorMetric ? [q.metric, q.denominatorMetric] : [q.metric],
              )
            : [];
      for (const m of metrics) {
        expect(m).toMatch(/^rustfs_/);
        expect(m).not.toMatch(/^minio_/);
      }
    }
  });
});
