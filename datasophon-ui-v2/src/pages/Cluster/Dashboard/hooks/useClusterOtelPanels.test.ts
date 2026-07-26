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

import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { averageLatestValue, useClusterOtelPanels } from './useClusterOtelPanels';

const mocks = vi.hoisted(() => ({
  useDorisDashboardData: vi.fn(),
}));

vi.mock('../../../monitor/_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: mocks.useDorisDashboardData,
}));

describe('averageLatestValue', () => {
  it('keeps an empty series distinguishable from a real zero', () => {
    expect(averageLatestValue([])).toBeNaN();
    expect(
      averageLatestValue([
        { time: 1000, value: 0, series: 'ddh-01' },
        { time: 1000, value: 0, series: 'ddh-02' },
      ]),
    ).toBe(0);
  });

  it('averages only the latest timestamp across per-node series', () => {
    const points = [
      { time: 1000, value: 10, series: 'ddh-01' },
      { time: 1000, value: 30, series: 'ddh-02' },
      { time: 2000, value: 50, series: 'ddh-01' },
      { time: 2000, value: 70, series: 'ddh-02' },
    ];
    expect(averageLatestValue(points)).toBe(60);
  });
});

describe('useClusterOtelPanels', () => {
  beforeEach(() => {
    mocks.useDorisDashboardData.mockReset();
    mocks.useDorisDashboardData.mockReturnValue({
      instant: { 'CO-MEM-PCT': 42.5, 'CO-DISK-PCT': 8.1 },
      series: {
        'CO-CPU': [
          { time: 1000, value: 2, series: 'CPU 使用率 (ddh-01, node)' },
          { time: 1000, value: 4, series: 'CPU 使用率 (ddh-02, node)' },
        ],
        'CO-NET': [{ time: 1000, value: 500, series: 'receive' }],
      },
      loading: false,
    });
  });

  it('fixes job to the host_metrics job and forwards instance/timeRange/clusterId', () => {
    renderHook(() =>
      useClusterOtelPanels({ clusterId: 3, timeRange: '1h', refreshKey: 2 }),
    );

    expect(mocks.useDorisDashboardData).toHaveBeenCalledWith(
      expect.objectContaining({
        instance: '.+',
        job: '^node$',
        timeRange: '1h',
        clusterId: 3,
        refreshKey: 2,
      }),
    );
  });

  it('derives cluster-wide CPU% from the average of the latest per-node points', () => {
    const { result } = renderHook(() =>
      useClusterOtelPanels({ clusterId: 1, timeRange: '1h', refreshKey: 0 }),
    );

    expect(result.current.cpuPercent).toBe(3);
    expect(result.current.memoryPercent).toBe(42.5);
    expect(result.current.diskPercent).toBe(8.1);
    expect(result.current.cpuSeries).toHaveLength(2);
    expect(result.current.networkSeries).toHaveLength(1);
  });
});
