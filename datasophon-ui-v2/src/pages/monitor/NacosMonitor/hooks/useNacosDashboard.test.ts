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
import { latestSeriesValue, useNacosDashboard } from './useNacosDashboard';

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

describe('useNacosDashboard', () => {
  beforeEach(() => {
    mocks.fetchDorisLabels.mockReset();
    mocks.useDorisDashboardData.mockReset();
    mocks.fetchDorisLabels.mockResolvedValue({
      data: { instances: ['node-2:8848'], jobs: ['NacosServer'] },
    });
    mocks.useDorisDashboardData.mockReturnValue({
      instant: { N01: 1, N02: 2, N03: 3, N04: 4, N05: 5 },
      series: {
        N07: [
          { time: 1000, value: 1, series: 'HTTP QPS' },
          { time: 2000, value: 2, series: 'HTTP QPS' },
        ],
      },
      loading: false,
    });
  });

  it('passes the service clusterId and fixed NacosServer job to Doris queries', async () => {
    const { result } = renderHook(() =>
      useNacosDashboard({
        instance: 'node-2:8848',
        timeRange: '1h',
        clusterId: 9,
        refreshKey: 3,
      }),
    );

    await waitFor(() => {
      expect(result.current.instances).toEqual(['node-2:8848']);
    });
    expect(mocks.fetchDorisLabels).toHaveBeenCalledWith(
      'nacos_monitor',
      9,
      '^NacosServer$',
    );
    expect(mocks.useDorisDashboardData).toHaveBeenCalledWith(
      expect.objectContaining({
        instance: 'node-2:8848',
        job: '^NacosServer$',
        clusterId: 9,
        refreshKey: 3,
      }),
    );
    expect(result.current.instant.httpQps).toBe(2);
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
});
