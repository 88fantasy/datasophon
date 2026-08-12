import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DorisPanelDescriptor } from './dorisService';
import {
  matrixToLatestScalar,
  useDorisDashboardData,
} from './useDorisDashboardData';

const mocks = vi.hoisted(() => ({
  fetchDorisNodeCount: vi.fn(),
  queryDorisInstant: vi.fn(),
  queryDorisRange: vi.fn(),
}));

vi.mock('./dorisService', () => ({
  fetchDorisNodeCount: mocks.fetchDorisNodeCount,
  queryDorisInstant: mocks.queryDorisInstant,
  queryDorisRange: mocks.queryDorisRange,
}));

describe('matrixToLatestScalar', () => {
  it('sums all series from the latest shared time bucket', () => {
    expect(
      matrixToLatestScalar({
        resultType: 'matrix',
        result: [
          {
            metric: { instance: 'one' },
            values: [
              [10, '1'],
              [20, '2'],
            ],
          },
          {
            metric: { instance: 'two' },
            values: [
              [10, '3'],
              [20, '4'],
            ],
          },
        ],
      }),
    ).toBe(6);
  });

  it('returns NaN when the range query has no samples', () => {
    expect(
      matrixToLatestScalar({ resultType: 'matrix', result: [] }),
    ).toBeNaN();
  });
});

describe('useDorisDashboardData failures', () => {
  const descriptors: Record<string, DorisPanelDescriptor> = {
    healthy: { type: 'node-count', roleName: 'GravitinoServer' },
    failed: { type: 'instant', metric: 'queued_requests', agg: 'sum' },
  };
  const panelIds = ['healthy', 'failed'];

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.fetchDorisNodeCount.mockResolvedValue({ data: 1 });
    mocks.queryDorisInstant.mockRejectedValue(new Error('Doris unavailable'));
  });

  it('preserves healthy values and marks failed panels as unavailable', async () => {
    const { result } = renderHook(() =>
      useDorisDashboardData({
        panelDescriptors: descriptors,
        panelIds,
        instance: '.+',
        job: '^GravitinoServer$',
        timeRange: '1h',
        clusterId: 1,
        refreshKey: 0,
      }),
    );

    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.instant.healthy).toBe(1);
    expect(result.current.instant.failed).toBeNaN();
    expect(result.current.failedPanelIds).toEqual(['failed']);
    expect(result.current.error).toContain('failed');
  });
});
