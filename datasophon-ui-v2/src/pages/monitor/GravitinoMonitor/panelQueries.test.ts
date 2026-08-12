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
  ALL_PANEL_IDS,
  GRAVITINO_JOB_FILTER,
  PANEL_QUERIES,
} from './panelQueries';

describe('GravitinoMonitor panel queries', () => {
  it('defines all Doris-backed panels and fixes the Doris job to GravitinoServer', () => {
    expect(ALL_PANEL_IDS).toHaveLength(20);
    for (const id of ALL_PANEL_IDS.filter((panelId) => panelId !== 'G02')) {
      expect(PANEL_QUERIES[id], `${id} should be defined`).toBeDefined();
    }
    expect(PANEL_QUERIES.G02).toBeUndefined();
    expect(GRAVITINO_JOB_FILTER).toBe('^GravitinoServer$');
  });

  it('groups top operation and error request rates by the operation label', () => {
    const topOperations = PANEL_QUERIES.G08;
    const errorsByOperation = PANEL_QUERIES.G09;
    expect(topOperations.type).toBe('multi-range');
    expect(errorsByOperation.type).toBe('multi-range');
    if (
      topOperations.type !== 'multi-range' ||
      errorsByOperation.type !== 'multi-range'
    ) {
      return;
    }
    for (const query of topOperations.queries) {
      expect(query.groupBy).toEqual(['operation']);
    }
    for (const query of errorsByOperation.queries) {
      expect(query.groupBy).toEqual(['operation']);
    }
  });

  it('reads HTTP request latency p99 from the summary table, grouped by operation', () => {
    const latency = PANEL_QUERIES.G10;
    expect(latency.type).toBe('multi-range');
    if (latency.type !== 'multi-range') return;
    for (const query of latency.queries) {
      expect(query.table).toBe('summary');
      expect(query.field).toBe('quantile');
      expect(query.groupBy).toEqual(['operation']);
    }
    expect(latency.queries.map((q) => q.quantile)).toEqual([0.99]);
  });

  it('reads JVM heap usage directly from jvm_heap_usage without a client-side ratio', () => {
    expect(PANEL_QUERIES.G06).toMatchObject({
      type: 'instant',
      metric: 'jvm_heap_usage',
    });
    expect(PANEL_QUERIES.G06).not.toHaveProperty('denominatorMetric');
  });
});
