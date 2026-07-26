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
import { useClusterSummary } from './useClusterSummary';

const mocks = vi.hoisted(() => ({
  getClusterDashboardSummary: vi.fn(),
  getRecentAlerts: vi.fn(),
}));

vi.mock('@/services/clusterDashboard', () => ({
  getClusterDashboardSummary: mocks.getClusterDashboardSummary,
  getRecentAlerts: mocks.getRecentAlerts,
}));

describe('useClusterSummary', () => {
  beforeEach(() => {
    mocks.getClusterDashboardSummary.mockReset();
    mocks.getRecentAlerts.mockReset();
  });

  it('loads summary and recent alerts together, keyed off clusterId/refreshKey', async () => {
    mocks.getClusterDashboardSummary.mockResolvedValue({
      data: { stats: { hostTotal: 5 } },
    });
    mocks.getRecentAlerts.mockResolvedValue({
      data: [{ id: 1, alertTargetName: 'CPU 使用率过高' }],
      total: 1,
    });

    const { result } = renderHook(() =>
      useClusterSummary({ clusterId: 1, refreshKey: 0 }),
    );

    expect(result.current.loading).toBe(true);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.summary?.stats.hostTotal).toBe(5);
    expect(result.current.recentAlerts).toHaveLength(1);
    expect(mocks.getRecentAlerts).toHaveBeenCalledWith(1, 5);
  });

  it('skips fetching and stays not-loading when clusterId is not yet resolved', () => {
    const { result } = renderHook(() =>
      useClusterSummary({ clusterId: 0, refreshKey: 0 }),
    );

    expect(result.current.loading).toBe(false);
    expect(result.current.recentAlerts).toEqual([]);
    expect(mocks.getClusterDashboardSummary).not.toHaveBeenCalled();
  });

  it('keeps recent alerts when the summary request fails', async () => {
    mocks.getClusterDashboardSummary.mockRejectedValue(new Error('boom'));
    mocks.getRecentAlerts.mockResolvedValue({
      data: [{ id: 1, alertTargetName: 'CPU 使用率过高' }],
      total: 1,
    });

    const { result } = renderHook(() =>
      useClusterSummary({ clusterId: 1, refreshKey: 0 }),
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe('boom');
    expect(result.current.summary).toBeUndefined();
    expect(result.current.recentAlerts).toHaveLength(1);
  });

  it('keeps the summary when the recent-alerts request fails', async () => {
    mocks.getClusterDashboardSummary.mockResolvedValue({
      data: { stats: { hostTotal: 5 } },
    });
    mocks.getRecentAlerts.mockRejectedValue(new Error('alerts unavailable'));

    const { result } = renderHook(() =>
      useClusterSummary({ clusterId: 1, refreshKey: 0 }),
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.summary?.stats.hostTotal).toBe(5);
    expect(result.current.recentAlerts).toEqual([]);
    expect(result.current.error).toBe('alerts unavailable');
  });
});
