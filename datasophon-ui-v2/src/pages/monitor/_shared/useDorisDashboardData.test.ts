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
    expect(result.current.error).toBeUndefined();
  });

  it('does not issue requests or keep loading when no panels are active', async () => {
    const { result, rerender } = renderHook(
      ({ refreshKey }) =>
        useDorisDashboardData({
          panelDescriptors: descriptors,
          panelIds: [],
          instance: '.+',
          timeRange: '1h',
          clusterId: 1,
          refreshKey,
        }),
      { initialProps: { refreshKey: 0 } },
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    rerender({ refreshKey: 1 });

    expect(mocks.fetchDorisNodeCount).not.toHaveBeenCalled();
    expect(mocks.queryDorisInstant).not.toHaveBeenCalled();
    expect(mocks.queryDorisRange).not.toHaveBeenCalled();
    expect(result.current).toMatchObject({ instant: {}, series: {} });
  });
});

it('passes the opt-in to all panel request types while reporting every failed panel', async () => {
  vi.clearAllMocks();
  mocks.queryDorisInstant.mockRejectedValue(new Error('offline'));
  mocks.queryDorisRange.mockRejectedValue(new Error('offline'));
  mocks.fetchDorisNodeCount.mockRejectedValue(new Error('offline'));
  const descriptors: Record<string, DorisPanelDescriptor> = {
    instant: {
      type: 'instant',
      metric: 'cpu',
      denominatorMetric: 'total',
      agg: 'sum',
    },
    nodes: { type: 'node-count', roleName: 'worker' },
    range: { type: 'range-stat', metric: 'cpu', rate: '5m' },
    series: {
      type: 'multi-range',
      queries: [
        { label: 'ratio', metric: 'cpu', denominatorMetric: 'total' },
        { label: 'raw', metric: 'cpu' },
      ],
    },
  };
  const panelIds = Object.keys(descriptors);
  const { result } = renderHook(() =>
    useDorisDashboardData({
      panelDescriptors: descriptors,
      panelIds,
      instance: '.+',
      timeRange: '1h',
      clusterId: 1,
      refreshKey: 0,
      skipErrorHandler: true,
    }),
  );
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.failedPanelIds.sort()).toEqual(panelIds.sort());
  for (const mock of [mocks.queryDorisInstant, mocks.queryDorisRange]) {
    expect(mock).toHaveBeenCalled();
    for (const call of mock.mock.calls)
      expect(call[1]).toEqual({ skipErrorHandler: true });
  }
  expect(mocks.fetchDorisNodeCount).toHaveBeenCalledWith('worker', 1, {
    skipErrorHandler: true,
  });
});
