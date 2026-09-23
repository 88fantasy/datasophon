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
import { fetchDorisLabels } from '../../_shared/dorisService';
import { useDorisDashboardData } from '../../_shared/useDorisDashboardData';
import { SEGMENT_PANEL_IDS } from '../panelQueries';
import { useSeaTunnelDashboard } from './useSeaTunnelDashboard';

vi.mock('../../_shared/dorisService', () => ({
  fetchDorisLabels: vi.fn().mockResolvedValue({ data: { instances: [] } }),
}));

vi.mock('../../_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: vi.fn(() => ({
    instant: {},
    series: {},
    loading: false,
    failedPanelIds: [],
  })),
}));

describe('useSeaTunnelDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useDorisDashboardData).mockReturnValue({
      instant: {},
      series: {},
      loading: false,
      failedPanelIds: [],
    });
    vi.mocked(fetchDorisLabels).mockResolvedValue({
      data: { instances: [] },
    } as never);
  });

  it('pins cluster panels to instance .+ regardless of the selected instance', async () => {
    const { rerender, unmount } = renderHook(
      ({ instance }) =>
        useSeaTunnelDashboard({
          activeSegment: 'master',
          instance,
          timeRange: '1h',
          clusterId: 7,
          refreshKey: 0,
        }),
      { initialProps: { instance: 'master-1' } },
    );

    await waitFor(() => expect(fetchDorisLabels).toHaveBeenCalled());
    rerender({ instance: 'master-2' });

    const calls = vi
      .mocked(useDorisDashboardData)
      .mock.calls.map(([params]) => params);
    const clusterCalls = calls.filter((params) =>
      params.panelIds.includes('ST-M02'),
    );
    const instanceCalls = calls.filter((params) =>
      params.panelIds.includes('ST-M01'),
    );

    expect(clusterCalls.length).toBeGreaterThan(0);
    expect(clusterCalls.every(({ instance }) => instance === '.+')).toBe(true);
    expect(instanceCalls.some(({ instance }) => instance === 'master-1')).toBe(
      true,
    );
    expect(instanceCalls.some(({ instance }) => instance === 'master-2')).toBe(
      true,
    );
    unmount();
  });

  it('passes only the active segment panels to the data hooks', () => {
    const { unmount } = renderHook(() =>
      useSeaTunnelDashboard({
        activeSegment: 'worker',
        instance: '.+',
        timeRange: '1h',
        clusterId: 7,
        refreshKey: 0,
      }),
    );
    const calls = vi
      .mocked(useDorisDashboardData)
      .mock.calls.slice(0, 2)
      .map(([params]) => params);

    expect(calls[0]?.panelIds).toEqual(SEGMENT_PANEL_IDS.worker);
    expect(calls[1]?.panelIds).toEqual([]);
    unmount();
  });
});
