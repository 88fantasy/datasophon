import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PrometheusMatrix } from '../../_shared/charts/promql';
import type { TimeSeriesPoint } from '../../_shared/types';
import {
  calculateCacheHitPct,
  deriveKeyExpirationSeries,
  useValkeyDashboard,
} from './useValkeyDashboard';

const mocks = vi.hoisted(() => ({
  fetchDorisLabels: vi.fn(),
  queryDorisRange: vi.fn(),
  useDorisDashboardData: vi.fn(),
}));

vi.mock('../../_shared/dorisService', () => ({
  fetchDorisLabels: mocks.fetchDorisLabels,
  queryDorisRange: mocks.queryDorisRange,
}));

vi.mock('../../_shared/useDorisDashboardData', () => ({
  useDorisDashboardData: mocks.useDorisDashboardData,
}));

function matrix(result: PrometheusMatrix['result']): PrometheusMatrix {
  return { resultType: 'matrix', result };
}

describe('Valkey dashboard derived data', () => {
  it('calculates the five-minute cache hit percentage across instances', () => {
    const points: TimeSeriesPoint[] = [
      { time: 1_000, value: 100, series: 'Misses (old)' },
      { time: 301_000, value: 60, series: 'Hits (node-1)' },
      { time: 301_000, value: 20, series: 'Hits (node-2)' },
      { time: 301_000, value: 20, series: 'Misses (node-1)' },
      { time: 601_000, value: 0, series: 'Hits (idle)' },
      { time: 601_000, value: 0, series: 'Misses (idle)' },
    ];

    expect(calculateCacheHitPct(points)).toBe(80);
    expect(calculateCacheHitPct([])).toBe(0);
  });

  it('pairs DB matrices before clamping and aggregating by instance', () => {
    const total = matrix([
      {
        metric: { instance: 'node-1:9121', job: 'ValkeyExporter', db: 'db0' },
        values: [[100, '10']],
      },
      {
        metric: { instance: 'node-1:9121', job: 'ValkeyExporter', db: 'db1' },
        values: [[100, '4']],
      },
    ]);
    const expiring = matrix([
      {
        metric: { instance: 'node-1:9121', job: 'ValkeyExporter', db: 'db0' },
        values: [[100, '3']],
      },
      {
        metric: { instance: 'node-1:9121', job: 'ValkeyExporter', db: 'db1' },
        values: [[100, '7']],
      },
    ]);

    expect(deriveKeyExpirationSeries(total, expiring)).toEqual([
      { time: 100000, value: 10, series: 'Expiring' },
      { time: 100000, value: 7, series: 'Not-Expiring' },
    ]);
  });

  it('returns an empty V12 series when both matrices are empty', () => {
    expect(deriveKeyExpirationSeries(matrix([]), matrix([]))).toEqual([]);
  });
});

describe('useValkeyDashboard', () => {
  beforeEach(() => {
    mocks.fetchDorisLabels.mockReset();
    mocks.queryDorisRange.mockReset();
    mocks.useDorisDashboardData.mockReset();
    mocks.fetchDorisLabels.mockResolvedValue({
      data: { instances: ['ddh-01:9121'], jobs: ['ValkeyExporter'] },
    });
    mocks.queryDorisRange.mockResolvedValue({ data: matrix([]) });
    mocks.useDorisDashboardData.mockReturnValue({
      instant: { V01: 30, V02: 2, V03: 25, V03_max: 1024 },
      series: {
        V04: [
          { time: 1000, value: 9, series: 'Hits' },
          { time: 1000, value: 1, series: 'Misses' },
        ],
        V09: [],
        V13: [],
        V14: [],
      },
      loading: false,
    });
  });

  it('uses the service cluster and fixed ValkeyExporter job for Doris data', async () => {
    const { result } = renderHook(() =>
      useValkeyDashboard({
        variables: { instance: 'ddh-01:9121' },
        timeRange: '1h',
        clusterId: 9,
        refreshKey: 2,
      }),
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mocks.fetchDorisLabels).toHaveBeenCalledWith(
      'redis_up',
      9,
      '^ValkeyExporter$',
    );
    expect(mocks.useDorisDashboardData).toHaveBeenCalledWith(
      expect.objectContaining({
        instance: 'ddh-01:9121',
        job: '^ValkeyExporter$',
        clusterId: 9,
        refreshKey: 2,
      }),
    );
    expect(mocks.queryDorisRange).toHaveBeenCalledTimes(2);
    expect(result.current.instances).toEqual(['ddh-01:9121']);
    expect(result.current.instant.cacheHitPct).toBe(90);
  });
});
