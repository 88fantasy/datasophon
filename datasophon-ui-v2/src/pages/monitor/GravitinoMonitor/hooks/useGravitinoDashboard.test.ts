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

import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  latestSeriesValue,
  topSeriesByTotalValue,
  useGravitinoDashboard,
} from './useGravitinoDashboard';

const mocks = vi.hoisted(() => ({
  fetchDorisLabels: vi.fn(),
  useDorisDashboardData: vi.fn(),
}));

vi.mock('../../_shared/dorisService', () => ({
  fetchDorisLabels: mocks.fetchDorisLabels,
}));

vi.mock('../../_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: mocks.useDorisDashboardData,
}));

describe('useGravitinoDashboard', () => {
  beforeEach(() => {
    mocks.fetchDorisLabels.mockReset();
    mocks.useDorisDashboardData.mockReset();
    mocks.fetchDorisLabels.mockResolvedValue({
      data: { instances: ['node-2:9001'], jobs: ['GravitinoServer'] },
    });
    mocks.useDorisDashboardData.mockReturnValue({
      instant: { G01: 1, G03: 42, G04: 2, G05: 5, G06: 33 },
      series: {
        G07: [
          { time: 1000, value: 1, series: '2xx' },
          { time: 2000, value: 2, series: '2xx' },
        ],
      },
      loading: false,
      failedPanelIds: [],
    });
  });

  it('passes the service clusterId and fixed GravitinoServer job to Doris queries', async () => {
    const { result } = renderHook(() =>
      useGravitinoDashboard({
        instance: 'node-2:9001',
        timeRange: '1h',
        clusterId: 9,
        refreshKey: 3,
      }),
    );

    await waitFor(() => {
      expect(result.current.instances).toEqual(['node-2:9001']);
    });
    expect(mocks.fetchDorisLabels).toHaveBeenCalledWith(
      'jvm_heap_used',
      9,
      '^GravitinoServer$',
    );
    expect(mocks.useDorisDashboardData).toHaveBeenCalledWith(
      expect.objectContaining({
        instance: 'node-2:9001',
        job: '^GravitinoServer$',
        clusterId: 9,
        refreshKey: 3,
      }),
    );
    expect(result.current.instant.httpQps).toBe(2);
    expect(result.current.instant.jettyThreadUsage).toBe(42);
  });

  it('keeps an empty latest series distinguishable from a real zero', () => {
    expect(latestSeriesValue([])).toBeNaN();
    expect(
      latestSeriesValue([
        { time: 1000, value: 0, series: 'one' },
        { time: 1000, value: 0, series: 'two' },
      ]),
    ).toBe(0);
  });

  it('keeps only the ten busiest operation series', () => {
    const points = Array.from({ length: 12 }, (_, index) => ({
      time: 1000,
      value: index,
      series: `operation-${String(index).padStart(2, '0')}`,
    }));

    const top = topSeriesByTotalValue(points, 10);
    const names = new Set(top.map((point) => point.series));

    expect(names).toHaveLength(10);
    expect(names).not.toContain('operation-00');
    expect(names).not.toContain('operation-01');
    expect(names).toContain('operation-11');
  });

  it('marks the derived QPS unavailable when its G07 source is incomplete', () => {
    mocks.useDorisDashboardData.mockReturnValue({
      instant: {},
      series: {
        G07: [{ time: 1000, value: 2, series: '2xx' }],
      },
      loading: false,
      failedPanelIds: ['G07'],
    });

    const { result } = renderHook(() =>
      useGravitinoDashboard({
        instance: '.+',
        timeRange: '1h',
        clusterId: 9,
        refreshKey: 0,
      }),
    );

    expect(result.current.instant.httpQps).toBeNaN();
    expect(result.current.failedPanelIds).toEqual(['G02', 'G07']);
  });
});
